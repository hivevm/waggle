# ADR-0001: Agent governance model

- **Status:** 🟢 Accepted
- **Date:** 2026-07-05
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

HiveVM CC is developed with the help of coding agents (Claude Code and others) working inside a Dev
Container. Different agents read different configuration files by convention: Claude Code reads
`.claude/CLAUDE.md`, other tools look for their own dot-files. If each agent carried its own copy of
the project rules, the rules would drift out of sync and no single document would be authoritative.

The project is also **specification- and ADR-driven** (see the [specification](../SPECIFICATION.md)):
both humans and agents must follow the same authority chain and the same review process. That only
works if there is exactly one place the rules live.

## Decision

We adopt a **single source of truth** for all coding-agent rules:

1. [`AGENTS.md`](../../AGENTS.md) at the repository root holds every rule an agent must follow —
   principles, the ADR process, working style, quality bar, and project rules. It is
   vendor-neutral and is **not** edited per agent.
2. Where a specific agent needs a pointer file, that file only **redirects** to `AGENTS.md` and
   never restates rules. `.claude/CLAUDE.md` exists solely to send Claude Code to `AGENTS.md`.
3. Human-facing setup (prerequisites, Dev Container, build/test/run) lives in
   [`README.md`](../../README.md); `AGENTS.md` links to it rather than duplicating it.
4. The authority chain is **specification → accepted ADRs → individual task**, and it applies
   identically to humans and agents. Git/GitHub writes require explicit, single-use human approval.

## Consequences

- There is one authoritative document; onboarding a new agent means adding a thin pointer, not a new
  rule set.
- Rules cannot silently diverge between agents, because the pointer files contain no rules to diverge.
- `AGENTS.md` and `README.md` must stay in their lanes: rules in `AGENTS.md`, setup in `README.md`,
  build commands only in `README.md`'s *Build, Test & Run* section. Duplication is a defect.
- Adding an agent that cannot be pointed at `AGENTS.md` (only supports an inline rule file) forces a
  copy; such a copy must be generated from `AGENTS.md`, not hand-maintained.

## Alternatives considered

- **Per-agent rule files.** Rejected: guarantees drift and multiplies maintenance by the number of
  agents.
- **Rules in `README.md`.** Rejected: the README is human onboarding documentation; mixing binding
  agent rules into it blurs the audience and bloats it.
- **Rules only in the specification.** Rejected: the specification describes *what* the product is;
  agent working rules (approval gates, quality bar, review flow) are a separate concern that changes
  on a different cadence.
</content>
