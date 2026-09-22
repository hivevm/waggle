# ADR-0026: One dependency graph, stated once and tested over the whole tree

- **Status:** 🟡 Proposed <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-22
- **Deciders:** Markus Brigl
- **Supersedes:** [ADR-0013](0013-break-model-parser-dependency-cycle.md),
  [ADR-0019](0019-package-layout-and-dependency-dag.md),
  [ADR-0023](0023-template-engine-independent-of-waggle.md) — all three in full. They are the same
  decision reached in three steps.
- **Superseded by:** —

## Context

Three ADRs govern how the packages of this project may depend on each other, each written after one
more edge had been discovered the hard way:

- **ADR-0013** broke the cycle between `model` and the parser, keeping the generated `Token` as a
  named exception.
- **ADR-0019** named the stages as packages and made `PackageDagTest` assert the graph instead of
  discovering it.
- **ADR-0023** removed the last cycle — the template engine importing Waggle's options — and widened
  the test to packages its map had never inspected.

Together they now say one thing, and a reader has to assemble it from three documents and subtract
what each later one changed. The layout is settled and the test is one file, so the reasoning is
worth keeping as history but no longer worth reading as three current decisions.

Nothing in the code changes with this ADR. It restates what `PackageDagTest` already enforces.

## Decision

We will state the dependency rule once:

1. **The graph is a DAG, and `PackageDagTest` is its statement.** The test's `ALLOWED` map is the
   normative form: a package may use the JDK, `org.hivevm.core`, itself, and what the map lists for
   it. A key covers its sub-packages, so `codegen` states the rule for its back ends.
2. **Every package under `src/main/java` is in the map.** A new package joins the graph
   deliberately; a package that is merely absent is a hole, which is how the engine's cycle stayed
   invisible.
3. **`org.hivevm.source` and `org.hivevm.core` name nothing of Waggle.** The template engine is a
   leaf (ADR-0005); what it needs from a caller it declares itself.
4. **Two carve-outs, both named in the map**: `model` and `diag` may name the generated
   `grammar.Token`, because positions and verbatim token chains are what they hold it for.
5. **The model emits no tree code.** `theModelEmitsNoTreeCode` keeps the language-independent model
   from writing target source, which is the other thing the test guards.

## Consequences

- One place to read the rule, and it is executable: the test fails before the reasoning goes stale.
- ADR-0013, ADR-0019 and ADR-0023 become historical record. Their context — why the cycle existed,
  what it cost — is worth reading once and is not repeated here.
- The ADR directory grows rather than shrinks: three superseded documents stay, and this one is
  added. That is the price the process asks for numbers that never move
  ([README](README.md) §1), and it is paid once.
- Changing the graph now means changing this ADR by superseding it *and* the test, together.

## Alternatives considered

- **Leave the three as they are.** Rejected: they read as three current decisions when they are one,
  and the two later ones each correct the earlier's scope.
- **Merge them into one file and drop the numbers.** Not available: numbers are permanent and ADRs
  are never merged or deleted ([README](README.md) §1, AGENTS.md §3.3).
- **Consolidate the other clusters too** — the lexer/parser composition ADRs (0012, 0017, 0021) and
  the grammar-format ones (0008, 0010, 0025). Rejected: those are genuine successive decisions about
  different things, not one decision restated. Only the dependency graph was written three times.
