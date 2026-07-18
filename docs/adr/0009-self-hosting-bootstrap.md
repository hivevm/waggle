# ADR-0009: Self-hosting bootstrap with a checked-in generated parser

- **Status:** 🟢 Accepted
- **Date:** 2026-07-05
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** —
- **Superseded by:** —
- **Erratum (2026-07-13):** the context, decision item 3 and the last consequence described files and
  directories that do not exist (`JavaCC.lex`, `src/main/generated1/`, `src/main/generated2/`). They
  are corrected below; the decision itself is unchanged.

## Context

HiveVM CC parses grammars written in its own redesigned syntax
([ADR-0010](0010-unified-grammar-and-actual-surface-syntax.md), which supersedes
[ADR-0008](0008-grammar-syntax-and-lexical-file.md)). The parser that reads that syntax is itself
produced by HiveVM CC from a grammar (`src/main/resources/JavaCC.jj`; the JJTree front end from
`JJTree.jj` plus its lexical file `JJTree.lex`). This is a classic bootstrap: the tool needs a working
parser to build the tool. Something has to break the cycle.

## Decision

The generator is **self-hosting**, and the generated bootstrap parser is **checked into the
repository**:

1. The generated bootstrap sources live in `src/main/generated/` and are added to the compile source
   path in `build.gradle`, so a fresh checkout compiles without first running the generator.
2. The `generateParser` Gradle task ([ADR-0006](0006-gradle-plugin-interface.md)) regenerates that
   parser from the bootstrap grammars; CI runs `./gradlew generateParser build`, so the tool
   regenerates its own parser on every build and then compiles with it.
3. Regeneration is exercised by tests (`CCParserTest`) that generate from the bootstrap grammars into
   a temporary directory, so a test run never overwrites the live tree.

## Consequences

- A clean checkout builds immediately; there is no external "stage-0" parser to obtain.
- The tool continuously proves it can regenerate itself — a strong end-to-end test of the whole
  pipeline for the Java target.
- **Generated code is committed**, which is deliberately unusual: reviewers must not hand-edit
  `src/main/generated/`, and a change to the grammars *or* the generator must be accompanied by a
  regeneration so the checked-in parser stays consistent. A stale generated tree is a defect.
- Diffs can be large when the generator output changes; that noise is the accepted cost of the
  bootstrap.
- `src/main/generated/` is the only generation output directory in the repository; regeneration during
  a test run goes to a temporary directory instead.
- **"A stale generated tree is a defect" is not enforced today.** No test compares regenerated output
  against the checked-in tree, and `generateParser` overwrites it in place without CI diffing the
  result — so a stale bootstrap cannot currently be detected. Closing that gap is open work.

## Alternatives considered

- **Depend on a prebuilt bootstrap artifact.** Rejected: reintroduces an external stage-0 dependency
  and hides the self-hosting property behind a binary.
- **Generate at build time into a build directory, nothing committed.** Rejected: a clean checkout
  could not compile without first running the generator, creating a chicken-and-egg problem for IDEs
  and first builds; committing the generated tree keeps the repo self-contained.
</content>
