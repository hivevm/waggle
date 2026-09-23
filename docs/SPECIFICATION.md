# HiveVM Waggle — Specification

> This is the **constitution** of the project: the problem it solves, its goals and non-goals, the
> vocabulary everyone uses, and the criteria a change is measured against. Authority runs
> **specification → [accepted ADRs](adr/) → individual change**. Where a decision is *costly to
> reverse* or *constrains future choices*, the reasoning lives in an [ADR](adr/README.md), not here.
> Working rules for contributors and coding agents live in [`AGENTS.md`](../AGENTS.md); build and run
> commands live in [`README.md`](../README.md).

## 1. Problem

Turning a language grammar into a working parser by hand is error-prone and hard to maintain, and
existing generators tie the result to a single implementation language. **HiveVM Waggle** is a
*parser generator*: it reads a grammar specification and produces a recursive-descent parser, a
lexical analyzer (token manager), optional syntax-tree building, and grammar documentation.

HiveVM Waggle is a **fork of JavaCC 7.0.13** ([ADR-0003](adr/0003-fork-javacc-as-baseline.md)). It keeps
JavaCC's proven conceptual model — `LL(k)` recursive-descent parsing, a token manager with lexical
states, syntactic and semantic lookahead, tree building and grammar docs — while pursuing two goals
that JavaCC does not serve: a **more maintainable** codebase and **multiple output languages** from a
single grammar.

## 2. Goals

1. **Generate correct top-down parsers.** Produce `LL(k)` recursive-descent parsers with a token
   manager, from a grammar plus its lexical specification.
2. **Multiple target languages from one grammar.** Emit parsers in **Java, C++, and Rust**, with the
   target selectable per generation unit and the set of targets extensible
   ([ADR-0004](adr/0004-multi-target-code-generation.md)).
3. **Maintainability.** A clearly staged pipeline, a language-independent front end, and pluggable
   back ends, favouring simplicity and small, reviewable parts.
4. **Build-tool-native usage.** Drive generation as a declarative, cacheable step of a Gradle build
   ([ADR-0006](adr/0006-gradle-plugin-interface.md)).
5. **Feature coverage users expect.** Lexical states; `TOKEN`/`SKIP`/`MORE`/`SPECIAL_TOKEN`; syntactic
   and semantic lookahead; tree building — expressed in the grammar itself and generated in the same
   pass, without JavaCC's separate JJTree step; grammar documentation (JJDoc); full-Unicode lexing;
   the established generation options (lookahead depth, ambiguity checks, debug tracing, etc.).
6. **Self-hosting.** HiveVM Waggle generates its own parser from a grammar written in its own syntax
   ([ADR-0009](adr/0009-self-hosting-bootstrap.md)).

## 3. Non-goals

- **Verbatim JavaCC compatibility.** The grammar surface syntax is deliberately redesigned
  ([ADR-0008](adr/0008-grammar-syntax-and-lexical-file.md), with the syntax as actually implemented
  and the integration of tree building recorded in
  [ADR-0010](adr/0010-unified-grammar-and-actual-surface-syntax.md)); JavaCC grammars must be
  migrated, not dropped in. Compatibility is at the *concept and feature* level, not source or syntax.
- **Bottom-up / LR parsing.** HiveVM Waggle generates top-down parsers only; left recursion is disallowed.
- **A general-purpose CLI as the primary interface.** The Gradle plugin and the `ParserBuilder` API
  are the supported entry points; a standalone fat-jar CLI is not a maintained deliverable (JJDoc
  keeps its own entry point).
- **Guaranteed feature parity across targets at every moment.** A capability may land in one back end
  before the others; gaps are tracked, not assumed absent.
- **Runtime dependency in generated parsers.** Generated code should stand on its own target-language
  toolchain, with no HiveVM Waggle runtime library required.

## 4. Core concepts and pipeline

A grammar is transformed to generated source through a staged pipeline. The front end (stages 1–4) is
language-independent; only the back end (stage 5) is target-specific.

1. **Input.** A single grammar file (`.waggle`), and nothing else: it carries the header, the
   productions and the token definitions ([ADR-0025](adr/0025-one-grammar-file.md)). Tree-node
   annotations (`#Node`) are part of the grammar too, so there is **no separate JJTree
   pre-processing pass and no intermediate grammar** — one grammar is compiled in one step.
2. **Parse → model.** The grammar is parsed into a model of productions, expansions, and regular
   expressions; `#Node` annotations become node scopes on that model.
3. **Semantic analysis (*semanticize*).** Cross-checks and lookahead computation over the model
   (ambiguity checks, empty-expansion detection, first/follow reasoning).
4. **Lexer construction.** The lexical specification is compiled into an NFA and then a DFA for the
   token manager, per lexical state.
5. **Code generation.** A target back end, selected via the `CODE_GENERATOR` option and resolved
   through a service-provider interface, renders the model into source using per-language templates —
   producing the parser, the token manager (lexer), and, when the grammar sets `USE_AST` and uses `#Node`, the tree-node
   classes and visitor.

A grammar can also be **interpreted** instead of generated: the pipeline runs to the finished
automaton of stage 4 and stops, and the lexical specification is simulated rather than rendered
([ADR-0020](adr/0020-interpreted-mode.md)). This adds no stage and changes no output; it is a second
reader of the same model.

Grammar documentation (**JJDoc**) is a separate tool that renders a grammar to human-readable docs
(e.g. BNF) and does not participate in parser generation.

## 5. Vocabulary

The project uses this vocabulary consistently in code, comments, ADRs, and documentation.

### Grammar input

- **Grammar file (`.waggle`)** — the whole grammar: header, productions and token definitions.
  Tree-node annotations live here too; there is no separate tree-grammar format and no separate
  lexical file ([ADR-0025](adr/0025-one-grammar-file.md)).
- **Header** — `grammar Name;` declares the grammar's name (recorded as `PARSER_NAME`), followed by an
  optional `options { KEY: value, … }` block (comma-separated; values are strings, integers, or
  booleans). Note that the generated Java classes are named `Parser`/`Lexer`/`ParserConstants`, not
  after the grammar. `LOOKAHEAD` and `IGNORE_CASE` are reserved words and cannot be option keys.
- **Action** — a host-language island inside a rule, delimited by `<? … ?>`. The empty block `{}`
  denotes an empty expansion.
- **`BASE_PARSER` / `BASE_LEXER`** — user-supplied base classes with shared logic, referenced from the
  header instead of embedding a full class body in the grammar.

### Parsing model

- **Token** — a lexical unit; each has an **ordinal** and a **kind**
  (`TOKEN` / `SKIP` / `MORE` / `SPECIAL_TOKEN`). *Special tokens* (e.g. comments) are ignored by the
  parser but available to tools.
- **Token production** — a lexical block that defines one or more tokens, optionally scoped to
  **lexical states**.
- **Regular expression** — the pattern of a token: string literals, character lists/ranges, choices,
  sequences, and repetitions (`*`, `+`, `?`, ranges).
- **Production / rule** — a named grammar rule (`name = … ;`); the name is its **non-terminal**.
- **Expansion** — the right-hand side of a rule: sequences, choices, `(…)*` / `(…)+` / `[…]`,
  non-terminal references, actions, and lookahead.
- **Non-terminal** — a reference from one rule to another.
- **LOOKAHEAD** — syntactic and/or semantic lookahead used to choose between alternatives; the default
  is `LL(1)`, with local `LL(k)` where specified.

### Lexer

- **Token manager / lexer** — the generated component that turns input characters into tokens.
- **Lexical state** — a named mode of the token manager (default `DEFAULT`); rules can switch states.
- **NFA / DFA** — the automata the lexical specification is compiled into.

### Tree building

Tree building is integrated into the grammar and the single generation pass; "JJTree" survives only as
the historical name of JavaCC's separate pre-processor, not as a stage of this pipeline.

- **Node** — a syntax-tree node produced for a rule; a **multi**-node has a distinct class per rule,
  a **void** node produces none.
- **Node descriptor / scope** — the `#Node` annotation and the region of a rule it covers.
- **Visitor** — the generated traversal interface over the tree.

### Generation

- **Target language** — Java, C++, or Rust (`Language` enum), chosen by the `CODE_GENERATOR` option.
- **Generator (back end)** — the service-provider that renders a model to a target language; each
  provides a node, lexer, and parser sub-generator.
- **Template** — a per-language source template with comment-directives (`//@if`, `//@foreach`, …) and
  `__NAME__` substitutions ([ADR-0005](adr/0005-custom-template-engine.md)).
- **Option** — a named generation setting (e.g. `LOOKAHEAD`, `IGNORE_CASE`, `DEBUG_PARSER`,
  `OUTPUT_DIRECTORY`, target-specific `JAVA_PACKAGE` / `RUST_MODULE` / `CPP_NAMESPACE`), with a defined
  type and default.

### Tooling

- **Gradle plugin** (`org.hivevm.waggle`) — the primary interface: a `parserProject { task { … } }` DSL and
  a `generateParser` task.
- **`ParserBuilder`** — the programmatic fluent API underneath the plugin.
- **Generation unit / task** — one grammar-to-source generation, with its own target, input, and
  output.
- **JJDoc** — the grammar-documentation tool.
- **Bootstrap** — the checked-in generated parser HiveVM Waggle uses to parse its own grammar syntax.

## 6. Success criteria

A change or the project as a whole is measured against:

1. **Builds green.** `./gradlew generateParser build` succeeds, and the self-regenerated bootstrap
   parser compiles — the tool regenerates itself without error.
2. **Round-trips.** Regenerating the bootstrap parser from its own grammar produces a parser consistent
   with the checked-in one — regeneration reaches a fixpoint; the regeneration tests pass.
3. **Targets generate.** For a given grammar, the Java, C++, and Rust back ends each produce their
   sources without error.
4. **Concepts preserved.** The generated parsers behave as `LL(k)` recursive-descent parsers with the
   feature set of §2.5 (lexical states, lookahead, special tokens, tree building, options).
5. **Tested behaviour.** New behaviour ships with tests; a bug fix ships with a regression test that
   fails before and passes after ([`AGENTS.md`](../AGENTS.md) §5).
6. **Decisions recorded.** Architecture-relevant decisions are captured as ADRs before implementation,
   and the project vocabulary above is used consistently.
</content>
