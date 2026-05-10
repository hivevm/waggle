# ADR-0003: Fork JavaCC 7.0.13 as the baseline

- **Status:** 🟢 Accepted
- **Date:** 2026-07-05
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

HiveVM needs a parser generator that produces top-down `LL(k)` recursive-descent parsers with the
mature feature set contributors already rely on — token manager with lexical states, syntactic and
semantic lookahead, an integrated tree builder (JJTree), and grammar documentation (JJDoc). Building
such a tool from scratch would discard decades of proven design. JavaCC 7.0.13 provides exactly this
feature set and is released under the permissive BSD-3-Clause license.

The maintainers' stated goals for HiveVM CC are a **more maintainable** codebase and support for
**multiple output languages** (see [ADR-0004](0004-multi-target-code-generation.md)) — goals that
require deep, structural changes to JavaCC's internals rather than a plugin or a thin wrapper.

## Decision

We **fork JavaCC 7.0.13** as the starting point for HiveVM CC rather than depending on it or starting
from zero:

1. The project retains JavaCC's **conceptual model and feature set** — `LL(k)` recursive-descent
   parsing, a token manager with lexical states, `TOKEN`/`SKIP`/`MORE`/`SPECIAL_TOKEN` kinds, BNF
   productions and expansions, syntactic/semantic `LOOKAHEAD`, tree building (JJTree) and grammar
   documentation (JJDoc). "Mostly compatible with JavaCC" is understood at this **conceptual/feature**
   level; the *grammar surface syntax* is deliberately redesigned (see
   [ADR-0008](0008-grammar-syntax-and-lexical-file.md)), so JavaCC grammars are **not** accepted
   verbatim. Full source or grammar compatibility is an explicit non-goal.
2. The fork is **re-architected in place** (new `org.hivevm` package namespace, restructured model,
   lexer, semantic, and generator packages) to serve maintainability and multi-target codegen.
3. The project is licensed under **BSD-3-Clause**, preserving JavaCC's license and attribution to the
   original authors.

## Consequences

- We inherit a proven parser-generation design and a grammar language users already know.
- We own the full source and can restructure aggressively — there is no upstream to track or stay
  binary-compatible with.
- We also inherit the maintenance burden and any latent complexity of the original code; reducing
  that complexity is an ongoing, explicit goal.
- Because the grammar syntax diverges ([ADR-0008](0008-grammar-syntax-and-lexical-file.md)), existing
  JavaCC grammars must be migrated, not merely dropped in; the compatibility claim is about concepts,
  not copy-paste. Divergences are decided and recorded, not left implicit.
- Attribution and the BSD-3-Clause terms must be preserved in source headers and `LICENSE`.

## Alternatives considered

- **Depend on JavaCC unmodified** and extend via configuration. Rejected: multi-target code
  generation and a maintainability overhaul both require changing internals a dependency does not
  expose.
- **Start a new parser generator from scratch.** Rejected: throws away a proven design and a familiar
  grammar language for no proportional benefit (Simplicity first — reuse what works).
- **Fork a different generator (ANTLR, etc.).** Rejected: JavaCC's `LL(k)` recursive-descent model,
  grammar syntax, and BSD license best match the existing grammars and goals.
</content>
