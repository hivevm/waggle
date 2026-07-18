# ADR-0012: The lexer stage owns DFA construction; generators only render it

- **Status:** 🟢 Accepted <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-07-18
- **Deciders:** Markus Brigl
- **Supersedes:** —
- **Superseded by:** —

## Context

The [specification](../SPECIFICATION.md) §4 stages the pipeline so that **stages 1–4 are a
language-independent front end** and **stage 5 (code generation) is the target-specific back end**.
Stage 4 is *lexer construction*: "The lexical specification is compiled into an NFA and then a DFA
for the token manager, per lexical state." The implied dependency rule is that the back end may
depend on the front end, **never the reverse**. [ADR-0004](0004-multi-target-code-generation.md)
reinforces this: each target back end is a pluggable `Generator` that renders a shared model.

The implementation currently violates this at the lexer/generator boundary:

1. **Inverted dependency.** `DfaBuilder` (package `org.hivevm.cc.lexer`, stage 4) imports and calls
   `org.hivevm.cc.generator.LexerGenerator` — `DfaBuilder.reArrange(Hashtable)` delegates up to
   `LexerGenerator.re_arrange`. The front end depends on the back end.

2. **Duplicated computation, run in both phases.** The same pure-computation helpers exist as
   near-identical copies in both `DfaBuilder` (stage 4) and `LexerGenerator` (stage 5): `reArrange` /
   `re_arrange`, `fixStateSets` / `FixStateSets`, `getStateSetForKind` / `GetStateSetForKind`,
   `canStartNfaUsingAscii` / `CanStartNfaUsingAscii`, and the ASCII-move shaping. `LexerGenerator`
   re-runs this DFA shaping while emitting (e.g. `re_arrange(data)`, `FixStateSets(data)`,
   `GetStateSetForKind(...)`, `CanStartNfaUsingAscii(...)`) instead of rendering a finished DFA model
   produced by stage 4. This is a "dry-run then real-run" pattern with two hand-maintained copies.

The duplication is not merely cosmetic: it has already produced a correctness divergence. The
`DfaBuilder` copy of the bit-count helper (`numberOfBitsSet`) dropped bit 63, while the
`LexerGenerator` twin used the correct `Long.bitCount` — the two copies drifted, and only one was
right (fixed separately). Two hand-maintained copies of the same computation make such divergence a
recurring hazard rather than a one-off.

A recent refactor already pulled the *target-specific* lexer **emission** into a shared
`LexerGenerator` base with per-language print hooks (C++ and Rust override only hooks). The remaining
obstacle to a clean back end is this cross-phase duplication of the *language-independent
computation*, which cannot be resolved without first deciding where that computation lives.

## Decision

We will make **stage 4 (the lexer layer) the sole owner of NFA/DFA construction**. Concretely:

1. Stage 4 produces a **complete, language-independent DFA model** stored on `LexerData` /
   `NfaStateData`. All shared NFA/DFA computation — state rearrangement, state-set fixing,
   state-set-for-kind resolution, ASCII start-state analysis, composite-state registration — lives in
   the lexer layer and runs **once**, during stage 4.
2. The code generators (stage 5) become **pure renderers**: they read the finished model and emit
   source, and they do **not** recompute DFA structure while emitting.
3. The dependency direction is corrected: `org.hivevm.cc.generator.*` may depend on
   `org.hivevm.cc.lexer.*`, and **the lexer layer will not import or call any generator**. The
   existing `DfaBuilder → LexerGenerator` delegation is removed by moving the canonical computation
   into the lexer layer.
4. Each duplicated compute pair collapses to a **single implementation** in the lexer layer; the
   generator twins are deleted.

## Consequences

- **Single source of truth.** Each DFA computation exists once; divergence bugs of the
  `numberOfBitsSet` kind become structurally impossible rather than latent.
- **Thinner, clearer back ends.** The generators shrink toward pure rendering over the model, which
  continues the direction of the prior lexer-emission refactor and makes adding/altering a target
  back end simpler (per ADR-0004).
- **Correct, enforceable layering.** With no lexer→generator edge, the front-end/back-end boundary
  from the specification is respected and could be enforced (e.g. by package rules).
- **Cost and risk.** This is a sizable refactor of `LexerGenerator`'s `dump*` paths and
  `DfaBuilder`. The shaping passes carry ordering and mutable-state dependencies (composite-state
  registration, `statesForPos`, `nextStates`), so moving them must preserve behaviour exactly. It
  must be done under a safety net — a byte-for-byte diff of the regenerated bootstrap
  (`./gradlew generateParser`) plus the Java/C++/Rust compile tests — and is best landed as its own
  reviewable change, not folded into unrelated work.
- **Follow-up.** Once the model is authoritative, the remaining generator-side lexer duplication
  (e.g. the Rust `dumpDfaStates`/`dumpMoveNfa` copies) can be revisited against a stable, finished
  model rather than a moving target.

## Alternatives considered

- **Status quo (duplicate + backwards delegation).** Rejected: it keeps the front end depending on
  the back end, keeps two hand-maintained copies of every shaping pass, and leaves the divergence
  hazard in place.
- **A shared static utility in a neutral package that both phases call.** This removes the textual
  duplication but keeps the computation running twice (build and emit) and does not make stage 4's
  model authoritative; it treats the symptom (duplicated code) rather than the cause (recompute
  across the boundary). A reasonable interim step, but not the target state.
- **Formalize the current direction — canonical computation in the generator, lexer delegates
  down.** Rejected: it contradicts the specification's pipeline (stage 4 is the language-independent
  front end that stage 5 consumes) and would make the language-independent DFA computation live in
  the target-specific back end.
