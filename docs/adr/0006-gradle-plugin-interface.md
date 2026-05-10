# ADR-0006: Gradle plugin as the primary tool interface

- **Status:** 🟢 Accepted
- **Date:** 2026-07-05
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

Parser generation is a **build step**: a grammar changes, sources must be regenerated before
compilation. Classic JavaCC is driven from a command line and bolted onto builds with ad-hoc glue.
HiveVM CC's users build with Gradle, need incremental/cacheable behaviour, and want to configure
several generation units (e.g. a tree pass and a parser pass, or several targets) declaratively next
to the rest of their build.

## Decision

We ship a **first-class Gradle plugin** (`org.hivevm.gradle`, plugin id `org.hivevm.cc`) as the
primary way to run the generator, published to the Gradle Plugin Portal:

1. The plugin registers a `parserProject { … }` DSL extension and a `generateParser` task.
2. `parserProject` carries project-wide defaults (`target`, `file`, `output`) and a list of nested
   `task { name; target; file; output; treeNodes }` blocks — one per generation unit — so multiple
   grammars/targets are configured declaratively.
3. Each task resolves the effective language/output/file (task value, else project default, else
   `JAVA`) and drives the programmatic `org.hivevm.cc.ParserBuilder` API.
4. The task is annotated `@CacheableTask` to participate in Gradle's up-to-date checks and build cache.

The underlying `ParserBuilder` fluent API remains public, so embedding the generator without Gradle
(tests, other tools) stays possible; the JJDoc documentation tool keeps its own `main`.

## Consequences

- Generation is a declarative, cacheable part of the build; contributors configure it in
  `build.gradle` rather than wiring command-line invocations.
- The project **dogfoods** the plugin: its own `build.gradle` uses `parserProject` to regenerate its
  bootstrap parser (see [ADR-0009](0009-self-hosting-bootstrap.md)).
- Gradle is the supported integration path; a standalone fat-jar CLI is not a maintained deliverable
  (the `ParserBuilder` API is the escape hatch for non-Gradle use).
- Plugin inputs must be declared for caching to stay correct; adding an input that affects output
  without wiring it into the task can cause stale, cached generations.

## Alternatives considered

- **Command-line JAR only.** Rejected: pushes incremental-build and configuration concerns onto every
  user and loses Gradle caching.
- **Maven plugin.** Rejected for now: the users and this repository build with Gradle (YAGNI — add a
  Maven front end only when a concrete need appears; the `ParserBuilder` API keeps that option open).
- **Library API only, no build plugin.** Rejected: every consumer would re-invent task wiring and
  up-to-date checks.
</content>
