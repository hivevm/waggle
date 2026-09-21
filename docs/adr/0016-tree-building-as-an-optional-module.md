# ADR-0016: Tree building is an optional module with its own model and emitter

- **Status:** 🟡 Proposed
- **Date:** 2026-09-20
- **Deciders:** HiveVM Waggle maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

[ADR-0010](0010-unified-grammar-and-actual-surface-syntax.md) folded tree building into the one
grammar and the one pass: `#Node` descriptors annotate productions, and the node classes are emitted
beside the parser. That decision is about the *input*. It left open how tree building is arranged
inside the tool, and today it is not arranged at all — it is spread across four layers, none of which
can be removed, tested or replaced on its own.

**The model contains target source text.** `model/NodeDescriptor.openNode()` returns the string
`"jjtree.openNodeScope(" + nodeVar + ");"` and `closeNode()` returns
`"jjtree.closeNodeScope(…, jjtree.nodeArity() > N);"`; `model/NodeScope` builds the identifiers
`jjtn000`, `jjtc000` and `jjte000`. The model is the language-independent artifact that
[ADR-0013](0013-break-model-parser-dependency-cycle.md) exists to protect, and `ModelLayeringTest`
guards its imports — but not its string literals. The text is not even right for every target: the
Rust back end ignores both methods and writes `self.jjtree.open_node_scope(…)` itself, so what the
model offers is Java and C++ only.

**The analysis has no stage.** `generator/NodeData` — the node ids, node names and the set of node
classes to write — is analysis output, but it lives in `generator/` and is filled inside
`GeneratorProvider.generate`, as a side effect of emitting code rather than as a step with an input
and a result. Nothing can ask "does this grammar build a tree?" without starting a generation.

**Tree options are not separable.** `NODE_MULTI`, `NODE_DEFAULT_VOID`, `NODE_SCOPE_HOOK`,
`NODE_CLASS`, `VISITOR`, `VISITOR_DATA_TYPE` and the rest sit in the single option set together with
`LOOKAHEAD` and `JAVA_PACKAGE`, and `WaggleOptions.validate` checks the `VISITOR_*` combinations for
every grammar, including one that declares no node at all.

**Emission is woven into the parser generator.** `ParserGenerator.generate_phase1_expansion` inserts
the open, close and catch code inline, and `ParserGenerator` declares three abstract methods —
`insertOpenNodeCode`, `insertCloseNodeCode`, `insertCatchBlocks` — that every back end must
implement. A target without tree support cannot exist; the Rust back end expresses its gap by
throwing a string from `RustNodeGenerator` instead.

**The reference consumer reaches into back-end internals.** `parser/jjtree` is the only grammar that
exercises `#Node`, `NODE_MULTI`, `VISITOR`, `NODE_SCOPE_HOOK` and `BASE_PARSER` end to end, and the
build regenerates it on every run. Its `TreeGenerator` calls `ParserGenerator.insert*NodeCode`
directly and also uses `CodeGenerator.can_replace`/`replace` and `generator.NodeData`. So the one
thing that would prove the tree API works can only run through a back end — which means there is no
tree API, only a back end with tree code in it.

**State of the art.** ANTLR 4 went the whole way and
[removed grammar-level AST construction](https://github.com/antlr/antlr4/blob/master/doc/faq/general.md):
the parser always emits a parse tree, and users build their own AST with a
[listener or visitor](https://github.com/antlr/antlr4/blob/master/doc/faq/parse-trees.md) written in
the target language. The lesson worth taking is the separation — tree construction is not the
parser's business — not the removal: `#Node` in the grammar is settled by ADR-0010 and is part of the
feature set in [SPECIFICATION §2](../SPECIFICATION.md).

## Decision

We will make tree building a **module with its own model, its own options and its own emitter**,
which a grammar and a target may each do without.

1. **The model holds tree annotations as data.** `NodeDescriptor` keeps the name, the arity
   expression, the `>` flag and the descriptor text; `NodeScope` keeps the descriptor and the scope
   number. Neither produces target source text or identifiers. `ModelLayeringTest` is extended to
   reject the literals `jjtree.` and `jjtn` anywhere under `model/`.
2. **A `tree` stage produces an optional model.** `org.hivevm.waggle.tree.TreeAnalyzer` returns
   `Optional<TreeModel>`, empty when the grammar declares no `#Node` and `NODE_SCOPE_HOOK` is off.
   `TreeModel` absorbs `generator/NodeData` — node ids, node names, the nodes to generate, the custom
   nodes — and `generator/` no longer declares it. The template flag `USE_AST` derives from the
   model's presence.
3. **Tree options are their own record.** `TreeOptions` carries the `NODE_*` and `VISITOR_*`
   settings and is validated only when a tree is present. The option names in grammars do not change.
4. **Emission goes through an explicit hook.** `ParserGenerator` calls an `ExpansionDecorator`
   (`before`, `after`, `onError`), which does nothing when there is no `TreeModel`. Each target that
   supports trees provides a `TreeEmitter` (`openScope`, `closeScope`, `catchBlocks`, `emitRuntime`)
   under `generator/<lang>/tree/`, and `Generator` exposes `Optional<TreeEmitter> treeSupport()` —
   extending the SPI of [ADR-0004](0004-multi-target-code-generation.md) without changing its three
   sub-generators. The abstract `insert*` methods are removed, and the node, node-state and visitor
   templates move to `templates/<lang>/tree/`, rendered only when a `TreeModel` is present.
5. **JJTree is the tested reference consumer.** It depends on `model`, `tree`, `diag`,
   `org.hivevm.source`, `org.hivevm.core` and the JDK — never on `generator`. `CodeGenerator`'s
   `$NODE`/`$BOOL` rewriting moves to `tree.ActionRewriter`; a hand-written `NodeScopeHooks`
   interface (`jjtreeOpenNodeScope`, `jjtreeCloseNodeScope`) states the `NODE_SCOPE_HOOK` contract
   the generated parser expects; `JJTree.parse(text)` and `JJTree.write(ast, emitter, writer)` give
   it an entry point, so its runtime path is executed by tests and not only compiled.

The scope-variable names (`jjtn000`, `jjtc000`, `jjte000`) are identical in all three targets. They
move **out of the model into `tree`**, once, rather than into each back end: they are shared tree
vocabulary, not target syntax, and three copies would be three chances to drift.

## Consequences

- A grammar without `#Node` produces no tree runtime, no tree templates and no tree-option warnings;
  today it produces warnings about `VISITOR_*` it never asked for.
- A target may support trees or not, and say so in the type system. The Rust back end's missing
  visitor stops being a thrown string and becomes an absent `treeSupport()`.
- The tree API becomes testable without a back end, which is what lets JJTree's runtime path — the
  only end-to-end exercise of tree building in the repository — actually run in a test.
- The change is four steps, and **each one must leave the generated output byte-for-byte
  unchanged**; that is the only available proof that moving code did not change what it emits.
- Moving the templates changes where they live, not what they render, and the bootstrap reads the
  *published* plugin's templates ([ADR-0009](0009-self-hosting-bootstrap.md)) — so the checked-in
  generated parser is unaffected either way.
- `Generator` gains a method, so every back end is touched; a back end outside this repository would
  have to implement it. There is no such back end today, and the SPI is explicitly an extension
  point ([ADR-0004](0004-multi-target-code-generation.md)).
- Risk: `ExpansionDecorator` is an abstraction added before a second consumer exists. It earns its
  place only because it is what removes tree code from `ParserGenerator`; if it grows hooks that no
  emitter uses, it has failed and should be inlined again.

## Alternatives considered

- **Leave the code strings in the model and only move `NodeData`.** Rejected: the model is the layer
  ADR-0013 protects, its strings are already wrong for Rust, and leaving them means every future
  target inherits Java syntax it must work around.
- **Emit a parse tree always and drop `#Node`, as ANTLR 4 did.** Rejected: it contradicts ADR-0010
  and SPECIFICATION §2, and it would break every existing grammar — a migration this project has no
  reason to impose.
- **One `TreeEmitter` for all targets, parameterised by a syntax object.** Rejected: the targets
  differ by more than syntax. Rust threads `self.jjtree` through `Rc` and has no exceptions, so its
  catch blocks have no counterpart; a parameterised emitter would grow a flag per difference.
- **Keep JJTree calling the back ends and only tidy the rest.** Rejected: JJTree is the evidence that
  the tree API works. An API whose only consumer must bypass it is not an API.
- **Do all four steps in one change.** Rejected: the byte-identical check is what makes this safe,
  and it is only informative if each step is small enough to attribute a diff to.
