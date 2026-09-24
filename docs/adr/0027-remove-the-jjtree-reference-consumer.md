# ADR-0027: Remove the JJTree reference consumer

- **Status:** 🟡 Proposed <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-22
- **Deciders:** Markus Brigl
- **Supersedes:** [ADR-0016](0016-tree-building-as-an-optional-module.md) — decision 5 only. The
  rest of ADR-0016 (the tree model, the `tree` stage, `TreeOptions`, `ExpansionDecorator`,
  `TreeEmitter`) stands unchanged.
- **Superseded by:** —

## Context

`org.hivevm.waggle.jjtree` is the last remnant of JavaCC's separate pre-processor. It has not been a
stage of this pipeline since [ADR-0010](0010-unified-grammar-and-actual-surface-syntax.md) folded
`#Node` into the one grammar and the one pass; its own `package-info.java` says so. What it is today
is a **second grammar of the same language** — `src/main/resources/JJTree.waggle`, 564 lines
describing what `Waggle.waggle` already describes in 870 — plus the hand-written classes that walk
its tree (`ASTNode`, `ASTParser`, `ASTWriter`, `TreeGenerator`, `JJTreeVisitor`, `NodeScopeHooks`)
and a `tree` task in `build.gradle` that regenerates a parser for it on every build.

[ADR-0016](0016-tree-building-as-an-optional-module.md) decision 5 kept it deliberately, and gave it
the entry point and the tests it had never had. The reason was coverage: it was *"the only grammar in
the repository that exercises `#Node`, `NODE_MULTI`, `VISITOR`, `NODE_SCOPE_HOOK` and `BASE_PARSER`
end to end"*. That statement was true when it was written and is no longer true of most of it. The
inline grammars in `GeneratedCodeCompilesTest`, `CppCompilesTest` and `RustCompilesTest` now cover
`#Node`, `NODE_MULTI`, `NODE_DEFAULT_VOID`, `VISITOR` and `VISITOR_DATA_TYPE` for all three targets,
and they go one step further than JJTree ever did: they *compile* the generated code.
`TreeDetectionTest` covers the presence and absence of a tree. What is left that only JJTree reaches
is `NODE_SCOPE_HOOK` and `BASE_PARSER` together, in Java.

What the package costs is not the 1095 hand-written lines. It is the duplication:

- **Two grammars of one language drift.** ADR-0016 already recorded this — running JJTree's path for
  the first time showed *"the grammar of the grammar language had drifted away from the language the
  tool accepts"*, and the whole lexical half had to be added to it. Nothing prevents the next drift;
  the two files have no shared source and no test that compares what they accept.
- **It is a consumer with no users.** No production code path reaches it (`PackageDagTest` records
  that nothing depends on `jjtree`). It exists to be tested.
- **It is a permanent tax on the tree API.** `JJTreeLayeringTest`, the `jjtree` row in
  `PackageDagTest`, the golden file `src/test/resources/jjtree/JJTree.waggle.java.txt` (1642 lines)
  and the `tree` build task all exist to keep one non-shipping consumer honest.
- **It re-exports a JavaCC name the project has otherwise retired.**
  [ADR-0014](0014-rename-project-to-waggle.md) removed the `.jjt` alias and
  [ADR-0024](0024-javacc-heritage-vocabulary.md) confined `JJ*` names to the inside of the
  generator. A package named `jjtree` that is explicitly *not* JJTree is the one place a reader must
  be told in prose that the name means nothing.

**State of the art.** Dogfooding a generator on its own grammar is standard and worth keeping — but
once, not twice. ANTLR bootstraps from a single `ANTLRv4Parser.g4`; JavaCC itself has one grammar for
its input language. This repository already bootstraps from `Waggle.waggle` through the *published*
plugin ([ADR-0009](0009-self-hosting-bootstrap.md)), which is the stronger form of the same check: it
regenerates the parser the tool actually runs on, and a regression breaks the build rather than one
test.

## Decision

We will **remove `org.hivevm.waggle.jjtree` in full**, and with it the second grammar of the grammar
language.

1. **Deleted**: `src/main/java/org/hivevm/waggle/jjtree/` (14 files),
   `src/main/resources/JJTree.waggle`, the generated parser under
   `src/main/generated/org/hivevm/waggle/jjtree/`, the `tree` task and the `JJTree.waggle` jar
   exclusion in `build.gradle`, `JJTreeLayeringTest`, `ASTWriterTest`, the `jjtree` row in
   `PackageDagTest` and `WaggleParserTest.generatesTheTreeParser`. `JJTreeAstTest` and its golden
   file are already gone: `AstTest` replaced them (decision 3).
2. **`src/main/generated0/` and `src/main/generated1/` are left alone.** They are snapshots no
   source set reads and no build task writes, kept by hand; the `jjtree` half of `generated1` is now
   a snapshot of a package that no longer exists. Removing them is a separate decision about what
   they are for, not part of this one.
3. **The coverage it uniquely held moves to Waggle itself.** `AstTest` generates `Waggle.waggle`
   through `ParserBuilder` with `USE_AST`, `NODE_MULTI`, `NODE_DEFAULT_VOID`, `NODE_SCOPE_HOOK` and
   `VISITOR` switched on, checks the node classes, diffs the parser against a golden file and
   compiles the result against `AbstractGrammarParser` — the tree of the largest real grammar in
   the repository, produced by the generator users run rather than by a second consumer.
   `GeneratedCodeCompilesTest.scopeHooksResolveAgainstTheBaseParser` generates a grammar with
   `NODE_SCOPE_HOOK`, `BASE_PARSER`, `NODE_MULTI` and `VISITOR`, supplies the base class the option
   names, and compiles the result. That is what JJTree proved, without a second grammar to keep in
   step — and, unlike JJTree, it holds the generated code to compiling.
4. **`Waggle.waggle` is the only grammar of the grammar language.** The self-hosting check is
   ADR-0009's bootstrap plus `WaggleParserTest.generatesItsOwnParser`.
5. **The name `jjtree` survives only where it is emitted.** The generated tree runtime keeps its
   JavaCC field name `jjtree` and the scope variables `jjtn000`/`jjtc000`/`jjte000`
   ([ADR-0024](0024-javacc-heritage-vocabulary.md) draws that line at generated code); apart from
   the snapshot of decision 2, no package, grammar or test is named after it any more.

## Consequences

- About 3600 lines go: 1095 hand-written, 564 of grammar, 1642 of golden file and 252 of test,
  against 210 added — plus the 6626 generated lines the build no longer produces. One build task
  fewer, one grammar fewer to keep in step with the language.
- **No test runs a tree-building parser any more.** `JJTreeAstTest` parsed `Waggle.waggle` and
  `JJTree.waggle` into a tree at run time and checked that every node scope opened and closed in
  pairs. `AstTest` holds the generated tree of the same 800-line grammar to a golden file and to
  compiling, but does not execute it: a scope that is opened and never closed at run time would
  show only in the golden diff. This is the real cost of the change and it is accepted knowingly,
  not overlooked.
- `ASTWriter`'s indentation regression test goes with its subject. The bug it pinned cannot recur,
  because the code cannot.
- The tree API keeps its consumers: the three `TreeEmitter`s, which is what ADR-0016 built it for.
  Decision 5 of ADR-0016 was about *evidence*, and the evidence is now the compile tests.
- `NodeScopeHooks` disappeared as a written contract. `NODE_SCOPE_HOOK` is documented in
  `TreeOptions` and in the README, and the compile tests of decision 3 is what breaks if the emitted
  call and the expected signature drift apart.
- A grammar in the wild that sets `JAVA_PACKAGE: "org.hivevm.waggle.jjtree"` would now generate into
  a package this repository no longer owns. There is no such grammar outside this repository.
- Two accepted ADRs describe a package that is gone, and neither is edited for it:
  [ADR-0019](0019-package-layout-and-dependency-dag.md)'s table lists `jjtree` as "the reference
  consumer" and its decision 3 explains how to move it, and
  [ADR-0024](0024-javacc-heritage-vocabulary.md)'s consequences record the decision of 2026-09-21 to
  *keep* the package's name. Both remain accurate as history. The normative form of the package
  graph is `PackageDagTest` ([ADR-0026](0026-one-dependency-graph.md)), and it no longer has the
  row.

## Alternatives considered

- **Keep it and delete only the duplication** — run JJTree's rewrite on a tree parser generated from
  `Waggle.waggle` with tree options on. Rejected: generating that parser works (`AstTest` does it),
  but JJTree's walk needs a tree shaped for rewriting, and `Waggle.waggle` carries semantic actions
  that build the model (`setParserName`, `addProduction`); a variant for JJTree would need a
  different action set, which is exactly the second grammar again, now entangled with the first.
- **Keep the package, drop the grammar, test the tree API directly.** Rejected: without a grammar
  the `ASTNode` hierarchy has nothing to hold, and what would remain is a hand-written tree walked
  by a hand-written visitor — a test fixture wearing a package's clothes.
- **Keep everything and accept the cost.** Rejected: the cost is not idle. ADR-0016 had to repair
  the grammar's drift before its tests could run at all, and the repair took the whole lexical half
  of the language. The next feature in `Waggle.waggle` pays that again.
- **Move it out of `src/main` into `src/test`.** Rejected: it would still be a second grammar with
  the same drift, moved somewhere the drift is noticed later.
