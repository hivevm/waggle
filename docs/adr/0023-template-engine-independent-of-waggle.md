# ADR-0023: The template engine does not depend on Waggle, and the tested DAG covers every package

- **Status:** 🟢 Accepted <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-21
- **Deciders:** HiveVM Waggle maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

[ADR-0005](0005-custom-template-engine.md) chose a small template engine written in this repository
instead of a third-party dependency. It lives in `org.hivevm.source`, outside
`org.hivevm.waggle.*`, which is what one would expect of a component that knows about templates and
not about parser generation. [ADR-0019](0019-package-layout-and-dependency-dag.md) then stated the
pipeline's dependency graph and made `PackageDagTest` enforce it.

**There is a cycle between the two top-level packages, and the test cannot see it.**
`org.hivevm.source` imports Waggle:

| File | Imports |
| --- | --- |
| `source/Context.java` | `org.hivevm.waggle.api.Options` (`Context extends Options`) |
| `source/TemplateContext.java` | `org.hivevm.waggle.api.Options` |
| `source/TemplateSet.java` | `org.hivevm.waggle.api.Options` |
| `source/SourceProvider.java` | `org.hivevm.waggle.api.Options`, `org.hivevm.waggle.api.WaggleVersion` |

and 34 files under `org.hivevm.waggle` import `org.hivevm.source`. `PackageDagTest` lists
`"org.hivevm.source."` as an allowed *target* for nearly every Waggle package, but its `ALLOWED` map
is keyed by Waggle package names only, and the test walks exactly those keys. `org.hivevm.source` and
`org.hivevm.core` are never inspected, so the back edge that closes the cycle is outside the graph
the test states. ADR-0019's own reasoning applies: "Stating the graph is what keeps the next edge
from being discovered the same way."

**What `source` actually needs from `Options` is small.** `SourceProvider` calls `options.outputSink()`
— and `OutputSink` is itself an `org.hivevm.source` type (ADR-0018) — and passes `options` to
`Template.render` as an `Environment`, which lives in `org.hivevm.core`. `TemplateSet` and
`TemplateContext` use `Options` only as that same pair. The one genuine Waggle dependency is
cosmetic: `SourceProvider` builds the generated-file banner from `WaggleVersion.VERSION`, so the
engine names the product it happens to be shipped with.

In other words, `Context extends Options` imports the whole option surface of a parser generator —
`getLookahead()`, `getNodeClass()`, `getVisitorReturnType()` — into a template engine that uses none
of it.

**The state of the art treats this as a defect, not a style question.** The
[acyclic dependencies principle](https://en.wikipedia.org/wiki/Acyclic_dependencies_principle)
states that the dependency graph of packages must have no cycles; CERT
[DCL60-J](https://wiki.sei.cmu.edu/confluence/display/java/DCL60-J.+Avoid+cyclic+dependencies+between+packages)
records the practical cost — cyclic packages cannot be understood, tested or released
independently, and a change in one can harm the rest of the cycle. The constraint is also
mechanical rather than advisory in modern Java: the module system
[rejects cyclic `requires`](https://opus.ch/en/modularity-patterns-with-jpms-acyclic-dependencies/),
so as long as this cycle exists, `org.hivevm.source` can never become a module or a separately
published artifact — which is the only reason to keep a hand-written template engine in its own
top-level package at all.

## Decision

We will break the cycle and widen the test to the whole tree:

1. **`org.hivevm.source` depends only on `org.hivevm.core` and the JDK.** No file under it imports
   `org.hivevm.waggle.*`.
2. **The engine states its own contract.** `Context` extends `Environment` (`org.hivevm.core`), not
   `Options`, and the one thing the engine needs beyond name lookup — where rendered text goes —
   is expressed by `OutputSink`, which is already an `org.hivevm.source` type. `Options` keeps
   working with the engine by extending that contract rather than being imported by it.
3. **The banner is passed in, not looked up.** `SourceProvider` takes the generated-file title from
   its caller instead of reading `WaggleVersion`. Waggle supplies "HiveVM Waggle v.…" exactly as
   today, so the generated header text does not change.
4. **`PackageDagTest` covers `org.hivevm.source` and `org.hivevm.core`.** Its map is keyed by
   full package name rather than by the `org.hivevm.waggle.` suffix, with
   `source → {core, JDK}` and `core → {JDK}`, and it asserts that every package under
   `src/main/java` appears in the map — so a new package joins the graph deliberately instead of
   being unconstrained by omission.

## Consequences

- `org.hivevm.source` becomes what ADR-0005 described: a template engine that could be extracted,
  modularised or published without dragging a parser generator behind it. Nothing in this ADR
  extracts it; it only stops the cycle from ruling it out.
- The graph ADR-0019 states becomes the whole graph. The test stops being able to pass while an
  uninspected package closes a cycle through an inspected one.
- `Context` no longer offers `getLookahead()` and the other parser settings. Any template-side code
  that reached through `Context` for a Waggle setting has to be handed that setting instead — a
  small, compile-checked change, and the direction ADR-0019 already chose with `ParserOptions`.
- Adding a package now requires an entry in the test's map. That is the intended cost: it is the
  same trade ADR-0019 made for the pipeline packages.
- The generated output does not change. Decision 3 keeps the banner text identical, and nothing else
  here touches rendering.
- Not addressed here: whether `org.hivevm.source` should actually become its own module or artifact.
  This ADR only removes the obstacle; doing it is a separate decision with its own consequences for
  the build and the published coordinates.

## Alternatives considered

- **Move `org.hivevm.source` under `org.hivevm.waggle`.** This also removes the cycle, by declaring
  the engine part of Waggle. Rejected: it contradicts ADR-0005's reason for writing the engine —
  a small, self-contained component — and it would make the cycle disappear by making the
  distinction disappear, which is not the same thing.
- **Leave the cycle and document it.** Rejected: the cost is not hypothetical. It blocks modularity
  (JPMS forbids cyclic `requires`), and it is invisible to the test that exists specifically to
  state the dependency graph, which is worse than not having the test for that edge.
- **Have `source` depend on `Environment` but keep reading `WaggleVersion`.** Rejected: one import
  is all a cycle needs, and a template engine that hard-codes the name of one product is not
  reusable in the sense ADR-0005 claimed.
- **Enforce the graph with a third-party tool such as ArchUnit.** Rejected for the reason ADR-0019
  already gave by writing a plain JUnit test: the check is thirty lines of file reading, and
  AGENTS.md §1 asks for a dependency only on a concrete, present need.
