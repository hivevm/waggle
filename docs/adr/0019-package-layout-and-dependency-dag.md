# ADR-0019: A package layout that names the stages, and a dependency DAG that is tested

- **Status:** 🟢 Accepted
- **Date:** 2026-09-20
- **Deciders:** HiveVM Waggle maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

[SPECIFICATION §4](../SPECIFICATION.md) stages the pipeline: input, parse to model, semantic
analysis, lexer construction, code generation. The package names do not say that, and where they do,
they say it wrongly.

**Names that do not match what is inside.** `org.hivevm.waggle.parser` holds the grammar parser
(`JavaCCParserDefault`, `AbstractJavaCCParser`), the request object every later stage reads
(`JavaCCData`), the option system (`Options`) and the generated parser's runtime classes — four
different things under one name that says "parser". `org.hivevm.waggle.generator` holds the back
ends *and* `ParserData`/`ParserPlanner`, which are lookahead analysis: they decide how every choice
point is tested, and nothing about how it is printed. The root package holds the driver, the request,
the context, the options and `Encoding`.

**Rules exist, but only where something went wrong.** `ModelLayeringTest` (ADR-0013),
`JJTreeLayeringTest` (ADR-0016) and `LexerLayeringTest` (ADR-0012) each guard one edge, each written
after that edge was crossed. There is no statement of the whole graph, so the next edge is guarded
after it is crossed too.

**The model carries analysis state.** `model/Expansion` has `generation` (a visit marker for
follow-set computation), `inMinimumSize` (a re-entry guard for `minimumSize`) and `internalName` (the
name the parser generator invents for a `jj_3` routine). None of them is a property of the grammar;
all three are scratch space that a stage writes while it walks the model, which is why the model
cannot be shared between two generations and why "what is in the model" has no answer.

**`Expansion.hashCode()` returns `line + column`.** The comment says it is "a reimplementing of
Object.hashCode() to be deterministic", and it is genuinely needed: hash order reaches the generated
output. But it collides for every two expansions on the same diagonal, it is inconsistent with
identity equality, and it silently depends on positions being set before the expansion is put in a
map. The two maps keyed by `Expansion` today are a `LinkedHashMap` (insertion order, so the hash does
not matter) and a lookup-only `HashMap` (never iterated, so it does not matter either).

**Options are strings.** `Waggle` is 63 lines of name constants, `Options` 262 lines of
`stringValue("JAVA_PACKAGE")`-style accessors, `WaggleOptions` 360 lines of the map behind them, and
32 classes read them. A typo in a name is found at run time, if at all.

That last one has a constraint the others do not. [ADR-0005](0005-custom-template-engine.md) made
templates read option keys *by name* — `//@if(USE_AST)`, `__PARSER_NAME__` — which is why `Options`
extends `Environment`. A name-keyed view is not incidental; it is the template contract.

**State of the art.** [ArchUnit](https://www.archunit.org/) is the usual way to state and test rules
like these in Java: it can express a layered architecture, forbid cycles between package slices, and
fail the build on a violation. It is also a dependency, and this project already enforces three such
rules with a twenty-line source scan.

## Decision

1. **Packages are named after the stages they hold.**

   | package | holds |
   | --- | --- |
   | `api` | what a caller uses: the compiler, the request, the context, the builder |
   | `diag` | diagnostics (unchanged) |
   | `grammar` | the grammar parser, its base class and the parsed request |
   | `model` | the grammar as data (unchanged) |
   | `analysis` | semantic analysis and lookahead planning, including `ParserPlan` |
   | `lexer` | NFA/DFA construction (unchanged) |
   | `tree` | tree analysis, model and emitter SPI (unchanged) |
   | `codegen` | the back ends and the template sets |
   | `jjtree` | the reference consumer |

   `generator/ParserPlanner` and `generator/ParserData` move to `analysis` as `ParserPlan`: they are
   analysis output, and calling them by the stage that produces them is the point of the layout.

2. **The dependency graph is a DAG, and a test says so.** `model` depends on nothing but the JDK,
   `org.hivevm.core` and the generated `Token`; `analysis`, `lexer` and `tree` depend on `model`;
   `codegen` depends on those; `api` depends on everything; `jjtree` depends on `model` and `tree`
   only. One test states the whole graph and replaces the three that each guard one edge. It stays a
   source scan rather than [ArchUnit](https://www.archunit.org/): the rule is a dozen lines of
   allowed prefixes, and adding a dependency to express it would contradict
   [`AGENTS.md`](../../AGENTS.md) §1 as much as it would ADR-0005's reasoning.

3. **`jjtree` moves by changing `JAVA_PACKAGE` in `JJTree.waggle`.** It is a grammar option, so the
   generated parser follows in the same build; no hand-written class is renamed and no user grammar
   changes.

4. **Analysis state leaves the model.** `generation`, `inMinimumSize` and `internalName` move into
   side tables owned by the stage that writes them. `Expansion.hashCode()` goes back to identity,
   which is what its equality already is.

5. **Typed option views, not a replacement.** `ParserOptions`, `TreeOptions` (which exists) and a
   per-target `TargetOptions` are records read from the resolved options, so a stage takes the
   settings it uses and a typo is a compile error. The name-keyed `Environment` stays underneath,
   because that is what templates read (ADR-0005); replacing it would mean redesigning the template
   engine, which this decision does not.

Option names in grammars do not change, and no user grammar needs migrating.

## Consequences

- A reader can find a stage by its package, and the test says what may depend on what — once, rather
  than three times after the fact.
- The model becomes shareable and describable: it is the grammar, and nothing a walk left behind.
- Removing the positional `hashCode` removes a latent trap. It is safe only because neither map
  keyed by `Expansion` is iterated; the byte-for-byte diff is what proves it, and if the diff moves,
  the hash *was* reaching the output and the change must be reconsidered rather than forced.
- The move is wide: every import of `org.hivevm.waggle.generator.*`, `…parser.*` and the root package
  changes. It is mechanical, and the output must not move — except for the generated jjtree parser,
  which moves package by construction, and the golden file of
  [ADR-0016](0016-tree-building-as-an-optional-module.md) with it.
- The typed views sit *beside* the map rather than replacing it, so for a while both exist. That is
  the honest cost of ADR-0005's template contract; collapsing them is a separate decision about the
  template engine.
- Risk: a package layout is easy to agree with and easy to get wrong in the details. The test is
  what makes it real; a layout without an enforced graph would decay exactly as the current one did.

## Alternatives considered

- **Leave the packages and only add the DAG test.** Rejected: the test would have to encode that
  `generator` contains analysis and `parser` contains four unrelated things, which writes the current
  confusion down as a rule instead of removing it.
- **Use ArchUnit.** Rejected for now: it is a test-scope dependency for a rule that a short source
  scan already expresses, and the project has three working examples of that scan. It becomes the
  right answer when the rules outgrow prefixes — cycles between slices, inheritance constraints — and
  that is a later decision.
- **Package by feature rather than by stage.** Rejected: the stages *are* the features here. The
  specification describes a pipeline, and a reader looking for "where does lookahead get decided"
  wants a package, not a cross-cutting slice.
- **Replace the option map with records outright.** Rejected: templates read option keys by name by
  decision (ADR-0005), so the map is the template contract. Records on top give the type safety
  without breaking it.
- **Keep the positional `hashCode` and only document it.** Rejected: it is a correctness hazard that
  looks like a performance detail. If hash order ever does reach the output, a `LinkedHashMap` says
  so where a clever `hashCode` hides it.
