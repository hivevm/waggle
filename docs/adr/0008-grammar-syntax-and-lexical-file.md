# ADR-0008: Redesigned grammar syntax with a separate lexical specification

- **Status:** 🟢 Accepted
- **Date:** 2026-07-05
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

Classic JavaCC packs everything into one `.jj` file with a specific surface syntax: an `options { … }`
block, a `PARSER_BEGIN(Name) … PARSER_END(Name)` wrapper holding a whole Java class, productions of
the form `ReturnType name() : { decls } { expansion }`, and inline `{ java }` action blocks. That
syntax entangles the grammar with a Java compilation unit and predates the multi-target ambition of
this project ([ADR-0004](0004-multi-target-code-generation.md)) — a Java class literal embedded in
the grammar makes little sense when the target is C++ or Rust.

## Decision

HiveVM CC uses a **redesigned grammar syntax** and splits lexical definitions into a sibling file:

1. **Header.** A grammar begins with a `PARSER( KEY: value, … )` block that sets options (e.g.
   `JAVA_PACKAGE`, `PARSER_NAME`, `BASE_PARSER`, JJTree options), replacing `options { }` +
   `PARSER_BEGIN/END`.
2. **Productions.** Rules are written `RULE name() ::= expansion };` (with optional `: Type` return
   and, in `.jjt`, a trailing `#Node` tree annotation), replacing the `ReturnType name() : {} { }`
   form.
3. **Actions.** Semantic actions are delimited by `<? … ?>` instead of `{ … }`, so they read as an
   explicitly marked host-language island rather than being confused with grammar braces.
4. **Separate lexical file.** Token definitions live in a sibling `*.lex` file (e.g. `JavaCC.lex`
   alongside `JavaCC.jj`), which the driver appends to the grammar during a build. This separates the
   lexical specification from the productions.
5. **Language-neutral base classes.** Shared semantic-action code is factored into a `BASE_PARSER`
   class referenced from the header, keeping the grammar file free of a full embedded class body.

## Consequences

- The grammar is decoupled from any single host-language class literal, which fits multi-target
  generation and improves readability.
- Actions and lexical rules are clearly separated from the productions, supporting the maintainability
  goal.
- **JavaCC grammars are not accepted verbatim** — they must be migrated to the new syntax; the
  compatibility claim in [ADR-0003](0003-fork-javacc-as-baseline.md) is conceptual, not syntactic.
- The new syntax is the tool's own contract: it is itself specified by the bootstrap grammars
  `src/main/resources/JavaCC.jj` (+`.lex`) and `JJTree.jjt`, so it is self-describing but must be
  documented for users independently of the source.
- Tooling/editor support for the classic `.jj` syntax does not apply.

## Alternatives considered

- **Keep classic JavaCC syntax.** Rejected: the embedded Java-class model conflicts with multi-target
  output and is a primary source of the entanglement the fork set out to reduce.
- **A wholly new grammar meta-language.** Rejected: would discard the familiar token/production/
  lookahead concepts users know; the redesign changes the *surface*, not the model.
</content>
