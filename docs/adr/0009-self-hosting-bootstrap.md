# ADR-0009: Self-hosting bootstrap with a checked-in generated parser

- **Status:** 🟢 Accepted
- **Date:** 2026-07-05
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

HiveVM CC parses grammars written in its own redesigned syntax
([ADR-0008](0008-grammar-syntax-and-lexical-file.md)). The parser that reads that syntax is itself
produced by HiveVM CC from a grammar (`src/main/resources/JavaCC.jj` + `JavaCC.lex`; the JJTree
front end from `JJTree.jjt`). This is a classic bootstrap: the tool needs a working parser to build
the tool. Something has to break the cycle.

## Decision

The generator is **self-hosting**, and the generated bootstrap parser is **checked into the
repository**:

1. The generated bootstrap sources live in `src/main/generated/` and are added to the compile source
   path in `build.gradle`, so a fresh checkout compiles without first running the generator.
2. The `generateParser` Gradle task ([ADR-0006](0006-gradle-plugin-interface.md)) regenerates that
   parser from the bootstrap grammars; CI runs `./gradlew generateParser build`, so the tool
   regenerates its own parser on every build and then compiles with it.
3. Regeneration round-trips are exercised by tests (`CCParserTest` regenerates into a separate
   `src/main/generated2/` and the JJTree pass likewise), keeping the checked-in parser and the
   grammars in agreement without overwriting the live tree mid-build.

## Consequences

- A clean checkout builds immediately; there is no external "stage-0" parser to obtain.
- The tool continuously proves it can regenerate itself — a strong end-to-end test of the whole
  pipeline for the Java target.
- **Generated code is committed**, which is deliberately unusual: reviewers must not hand-edit
  `src/main/generated/`, and a change to the grammars *or* the generator must be accompanied by a
  regeneration so the checked-in parser stays consistent. A stale generated tree is a defect.
- Diffs can be large when the generator output changes; that noise is the accepted cost of the
  bootstrap.
- Multiple generation output directories (`generated`, `generated1`, `generated2`) exist to separate
  the live bootstrap from regeneration/scratch targets; their roles must stay clear to avoid
  confusion.

## Alternatives considered

- **Depend on a prebuilt bootstrap artifact.** Rejected: reintroduces an external stage-0 dependency
  and hides the self-hosting property behind a binary.
- **Generate at build time into a build directory, nothing committed.** Rejected: a clean checkout
  could not compile without first running the generator, creating a chicken-and-egg problem for IDEs
  and first builds; committing the generated tree keeps the repo self-contained.
</content>
