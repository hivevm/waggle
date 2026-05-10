# Architecture Decision Records

This directory holds the **Architecture Decision Records (ADRs)** for HiveVM CC. An ADR captures a
single architecturally significant decision — one that is *costly to reverse* or *constrains future
choices* — together with its context and consequences.

Authority runs **[specification](../SPECIFICATION.md) → accepted ADRs → individual change**. The
specification is the constitution; accepted ADRs derive from it; every change respects both. The
working rules that reference this process live in [`AGENTS.md`](../../AGENTS.md).

## Index

**Status legend:** 🟢 Accepted · 🟡 Proposed · 🔴 Rejected · ⚪ Superseded

| ADR | Title | Status |
| --- | ----- | ------ |
| [0001](0001-agent-governance-model.md) | Agent governance model | 🟢 Accepted |
| [0002](0002-dev-container-runtime.md) | Dev Container runtime | 🟢 Accepted |
| [0003](0003-fork-javacc-as-baseline.md) | Fork JavaCC 7.0.13 as the baseline | 🟢 Accepted |
| [0004](0004-multi-target-code-generation.md) | Multi-target code generation via a `Generator` SPI | 🟢 Accepted |
| [0005](0005-custom-template-engine.md) | Custom template engine instead of a third-party dependency | 🟢 Accepted |
| [0006](0006-gradle-plugin-interface.md) | Gradle plugin as the primary tool interface | 🟢 Accepted |
| [0007](0007-java-21-baseline.md) | Java 21 as the implementation baseline | 🟢 Accepted |
| [0008](0008-grammar-syntax-and-lexical-file.md) | Redesigned grammar syntax with a separate lexical specification | 🟢 Accepted |
| [0009](0009-self-hosting-bootstrap.md) | Self-hosting bootstrap with a checked-in generated parser | 🟢 Accepted |
| [0010](0010-unified-grammar-and-actual-surface-syntax.md) | Unified grammar — integrated tree building and the actual surface syntax | 🟡 Proposed |

## Process

1. **Numbering.** ADRs are numbered sequentially, zero-padded to four digits (`0001`, `0002`, …).
   Numbers are **permanent** — never renumber, delete, or merge an ADR. The filename is
   `NNNN-kebab-case-title.md`.
2. **Creating one.** Copy [`template.md`](template.md), fill it in, set status `proposed`, and add a
   row to the index **in the same change**. Then stop and ask a human reviewer to accept it before
   implementing.
3. **Status lifecycle.** `proposed → accepted | rejected`, and an accepted ADR may later become
   `superseded`. **Only a human reviewer promotes `proposed` to `accepted`.** An agent may only set
   `proposed`.
4. **Changing a decision.** Never edit an accepted ADR to reverse it. Write a *new* ADR that
   supersedes it; cross-link both with the `Supersedes` / `Superseded by` fields and keep the old one
   as historical record.
5. **Keep the index current.** Any add, supersede, or status change updates this table in the same
   change.

> The initial ADRs 0001–0009 are **retroactive**: they document decisions already embodied in the
> repository at the time the specification and this log were introduced, so the reasoning is captured
> and reviewable going forward.
</content>
