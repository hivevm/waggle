# ADR-0005: Custom template engine instead of a third-party dependency

- **Status:** 🟢 Accepted
- **Date:** 2026-07-05
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

Each code-generation back end ([ADR-0004](0004-multi-target-code-generation.md)) turns the parsed
model into source files. The emitted artifacts are themselves valid-ish source files with small
substitution and control-flow needs (insert names, repeat over tokens/productions, include a block
conditionally). This calls for templating. The obvious route is a mature engine such as FreeMarker,
Velocity, or Mustache/Handlebars — but that adds a runtime dependency, its own syntax, and, more
importantly, template files that are **not** themselves compilable source in the target language.

## Decision

We implement a **small, purpose-built template engine** in `org.hivevm.source` rather than depend on
a third-party one:

1. `org.hivevm.source.Template` compiles a template into a `Renderer` tree and renders it against an
   `Environment` (the same abstraction the options system implements — templates read option keys
   directly).
2. Directives are written as **line comments** so a template file remains syntactically close to real
   target-language source: `//@if(...)`, `//@elif`, `//@else`, `//@fi`, `//@foreach(...)`, `//@end`,
   `//@invoke`; variable substitution uses `__NAME__`.
3. Templates live under `src/main/resources/templates/{java,cpp,rust}/` as files named like their
   output (e.g. `java/Parser.java`, `cpp/Parser.cc`, `rust/parser.rs`).
4. The engine depends only on `org.hivevm.core.Environment`; it is decoupled from any parser concept
   and is reusable on its own.

## Consequences

- No third-party templating dependency, consistent with **Simplicity first / add a dependency only
  for a concrete present need**.
- Because directives are comments, template files are close to valid source and can be read, edited,
  and largely tooled as `.java`/`.cc`/`.rs`, which lowers the barrier to editing generated output.
- We own and must maintain the engine: its directive set, indentation handling, and edge cases are
  ours to test and evolve. New templating needs mean extending our engine, not reaching for library
  features.
- The directive syntax is bespoke; contributors must learn it (it is small, and documented by the
  templates themselves).

## Alternatives considered

- **FreeMarker / Velocity / Mustache.** Rejected: adds a dependency and a non-source template syntax
  for a need the current directive set already covers; heavier than warranted.
- **String concatenation in the generators.** Rejected: buries target-language text in Java control
  flow, making the emitted shape hard to see and edit, and duplicating structure across three back
  ends.
</content>
