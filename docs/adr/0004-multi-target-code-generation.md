# ADR-0004: Multi-target code generation via a `Generator` SPI

- **Status:** 🟢 Accepted
- **Date:** 2026-07-05
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

Stock JavaCC generates Java. HiveVM CC must emit parsers in **Java, C++, and Rust** from the same
grammar, and it must be possible to add further target languages later without rewriting the front
end. The front end — grammar parsing, the model, semantic analysis, and lexer/DFA construction — is
language-independent and must be shared; only the back end (emitting source) is language-specific.

## Decision

We separate a **shared, language-independent front end** from **pluggable per-language back ends**
selected at runtime through a service-provider interface:

1. The output language is the enum [`org.hivevm.cc.Language`](../../src/main/java/org/hivevm/cc/Language.java)
   (`JAVA`, `CPP`, `RUST`), chosen via the `CODE_GENERATOR` option (default `java`).
2. Back ends implement the
   [`org.hivevm.cc.generator.Generator`](../../src/main/java/org/hivevm/cc/generator/Generator.java)
   interface, which produces the three sub-generators — `NodeGenerator`, `LexerGenerator`,
   `ParserGenerator`.
3. Back ends are registered as **Java `ServiceLoader` providers** in
   `META-INF/services/org.hivevm.cc.generator.Generator` (`JavaGenerator`, `CppGenerator`,
   `RustGenerator`) and resolved at runtime.
4. Each back end lives in its own package (`generator/java`, `generator/cpp`, `generator/rust`) with a
   parallel structure (`<Lang>Generator`, `<Lang>LexerGenerator`, `<Lang>NodeGenerator`,
   `<Lang>ParserGenerator`, `<Lang>Template`) and its own template set under
   `src/main/resources/templates/<lang>/` (see [ADR-0005](0005-custom-template-engine.md)).

## Consequences

- One grammar produces parsers for several languages; the front end is written and tested once.
- Adding a target language is a **new provider + template set**, with no change to the front end or
  existing back ends — the extension point is explicit and closed to modification.
- Every back end must map the shared model (productions, expansions, regular expressions, tree nodes)
  onto its language; target-specific options exist for this (`JAVA_PACKAGE`/`JAVA_IMPORTS`,
  `RUST_MODULE`, `CPP_NAMESPACE`/`CPP_STACK_LIMIT`).
- Feature parity across targets is not automatic — a capability added to one back end must be
  deliberately ported to the others, and gaps must be tracked.
- The `ServiceLoader` registration file is part of the public contract; a missing or wrong entry
  silently drops a target.

## Alternatives considered

- **Java-only, like stock JavaCC.** Rejected: multi-language output is a primary goal.
- **A `switch` over `Language` in one monolithic generator.** Rejected: every new target edits shared
  code, violating open/closed and making back ends hard to isolate and test.
- **Separate forks per target language.** Rejected: triplicates the front end and guarantees
  divergence.
</content>
