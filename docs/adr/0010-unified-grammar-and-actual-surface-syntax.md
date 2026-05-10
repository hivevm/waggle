# ADR-0010: Unified grammar — integrated tree building and the actual surface syntax

- **Status:** 🟡 Proposed
- **Date:** 2026-07-12
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** ADR-0008
- **Superseded by:** —

## Context

[ADR-0008](0008-grammar-syntax-and-lexical-file.md) (accepted) recorded the decision to redesign the
grammar surface syntax away from JavaCC's. That direction still holds, but the ADR's concrete syntax
no longer matches the implementation, and it does not cover a second change that has since landed.

Two divergences, both verified against `src/main/resources/JavaCC.jj` — which, being self-hosting, *is*
the normative description of the language:

1. **The surface syntax in ADR-0008 was never implemented as written.** ADR-0008 specifies a
   `PARSER( KEY: value, … )` header and `RULE name() ::= expansion };` productions. The tool actually
   accepts a `grammar Name;` line followed by an optional `options { KEY: value, … }` block, and
   productions of the form `name = expansion ;`. The `<? … ?>` actions, the `BASE_PARSER` indirection,
   and the optional sibling `.lex` file from ADR-0008 *are* implemented as described.

2. **Tree building has been folded into the grammar and the single generation pass.** JavaCC runs
   JJTree as a pre-processor: `.jjt` → annotated `.jj` → parser, a two-step pipeline with an
   intermediate grammar. HiveVM CC no longer does this. `#Node` descriptors are part of the parser
   grammar (`node_descriptor` in `JavaCC.jj`), and `Parser.parse()` routes `.jj` and `.jjt` through the
   *same* single pass — `parseJJTree()` in `src/main/java/org/hivevm/cc/Parser.java` only reads the file
   and prints a banner; it performs no tree transformation. The `.jjt` extension is now merely an alias.

Neither the specification nor any ADR records (2); §4 of the specification still described a JJTree
pass, and the tutorials in `docs/tutorials/` document ADR-0008's unimplemented syntax.

## Decision

We will treat **the grammar as a single, unified input** and record its **actual** surface syntax:

1. **Header.** A grammar opens with `grammar Name;` (naming the parser), optionally followed by
   `options { KEY: value, … }` — a comma-separated list whose values are strings, integers, or
   booleans. This replaces ADR-0008's `PARSER( … )` form and JavaCC's `options { }` +
   `PARSER_BEGIN`/`PARSER_END`.
2. **Productions.** Rules are written `name = expansion ;`, with an optional parameter list
   (`name(Type p) = …`), an optional return type (`name() : Type = …`), and an optional `#Node`
   annotation. This replaces ADR-0008's `RULE name() ::= … };` form.
3. **Actions.** Semantic actions remain `<? … ?>` host-language islands; the empty block `{}` denotes
   an empty expansion.
4. **Token definitions** remain `TOKEN` / `SKIP` / `MORE` / `SPECIAL_TOKEN` blocks, inline in the
   grammar or in an optional sibling `.lex` file that the driver appends (unchanged from ADR-0008).
5. **Tree building is part of the grammar.** `#Node` descriptors annotate productions and expansions;
   the node classes and visitor are generated in the **same pass** as the parser. There is **no JJTree
   pre-processor, no intermediate grammar, and no two-step pipeline**. `.jjt` is accepted only as an
   alias for `.jj`.

## Consequences

- The documented syntax matches the tool. Until now `docs/tutorials/` taught a syntax the generator
  would reject, which is worse than no documentation.
- Grammar authors write and maintain **one** file for both parsing and tree building; the `.jjt`/`.jj`
  distinction, the JJTree options split, and the intermediate generated grammar all disappear.
- **JavaCC grammars are not accepted verbatim** and must be migrated — unchanged from ADR-0008 and
  consistent with [ADR-0003](0003-fork-javacc-as-baseline.md), where grammar compatibility is an
  explicit non-goal.
- The wording of [ADR-0009](0009-self-hosting-bootstrap.md), which still refers to "the JJTree front
  end from `JJTree.jjt`", is now inaccurate in that detail. ADR-0009's actual decision (a checked-in
  generated bootstrap parser) is unaffected; if its wording is to be corrected, that needs its own ADR.
- Follow-up required: `docs/tutorials/` (six files) must be rewritten in the real syntax; they are
  currently wrong end to end.
- Risk: because the language is self-hosting, the grammar is its own specification. Any future syntax
  change must update this ADR (via a superseding one) rather than silently drifting again — which is
  exactly how ADR-0008 became inaccurate.

## Alternatives considered

- **Amend ADR-0008 in place.** Rejected: accepted ADRs are immutable by
  [`AGENTS.md`](../../AGENTS.md) §3 — a changed decision is recorded by a superseding ADR, so the
  historical record stays intact.
- **Two ADRs (one for the syntax correction, one for unified tree building).** Rejected as
  unnecessary: both concern the same object — what a single grammar file contains — and splitting them
  would leave ADR-0008 half-superseded and harder to follow.
- **Keep JJTree as a separate pass.** Rejected: it forces a second grammar dialect, an intermediate
  generated artefact, and a two-step build for what is one language; removing it is a direct win for
  the specification's simplicity and maintainability goals.
