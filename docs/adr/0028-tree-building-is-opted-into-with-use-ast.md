# ADR-0028: Tree building is opted into with `USE_AST`

- **Status:** 🟡 Proposed <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-23
- **Deciders:** Markus Brigl
- **Supersedes:** [ADR-0016](0016-tree-building-as-an-optional-module.md), in part: the rule in its
  decision 2 that a tree is present when the grammar declares a `#Node` or sets `NODE_SCOPE_HOOK`,
  and that the template flag `USE_AST` derives from that. The rest of ADR-0016 stands.
- **Superseded by:** —

## Context

Under ADR-0016, `TreeAnalyzer` returns a `TreeModel` as soon as the grammar contains one `#Node`
descriptor or sets `NODE_SCOPE_HOOK`, and the template flag `USE_AST` is derived from that. The
annotations themselves are the switch.

This makes it impossible to annotate a grammar without generating a tree. `Waggle.waggle`, the
grammar of the grammar language, is meant to carry node descriptors (`grammar_input #Grammar`) while
still being generated as a plain parser, until a tree for it is wanted. Removing the annotations
each time you switch is error-prone, and one stray `#Node` quietly adds the tree runtime, the node
constants and the tree state to the generated parser.

Other generators make tree building an explicit setting, and that setting is separate from the
annotations. CongoCC has the grammar option `TREE_BUILDING_ENABLED`, which turns tree building off
while the grammar keeps its node annotations (default `true`). ANTLR has
`Parser.setBuildParseTree`. JavaCC made it a separate tool, JJTree, that you had to run
deliberately.

## Decision

We will make tree building an explicit grammar option.

1. **`USE_AST` is a grammar option**, a boolean that defaults to `false`. It sits with the other
   tree options (`NODE_*`, `VISITOR_*`) and is read into `TreeOptions.useAst`.
2. **`TreeAnalyzer.analyze` returns a `TreeModel` only when `USE_AST` is `true`** and the grammar
   declares a `#Node` or sets `NODE_SCOPE_HOOK`. Otherwise it returns nothing. The template flag
   `USE_AST` still derives from whether the model is present. So it is true exactly when a tree
   is built.
3. **If `USE_AST` is off, node descriptors and `NODE_SCOPE_HOOK` are ignored, with one warning.**
   The generated parser then has no tree code. The author is told once, not once per descriptor.
   This is the same approach as the existing `VISITOR_*`-without-`VISITOR` warnings.
4. **The tree options are validated only when a tree is built.** This keeps ADR-0016 decision 3,
   and the check moves into `TreeAnalyzer.analyze`.

## Consequences

- A grammar can carry `#Node` annotations before it builds a tree. `Waggle.waggle` can be
  annotated now.
- **Breaking for existing tree grammars:** each one must add `USE_AST: true`. In this repository
  that means `JJTree.waggle` (and its golden file) and the tree grammars in the tests. External
  grammars that rely on `#Node` alone lose their tree and get the warning instead of a silent
  change.
- Action code that uses `jjtThis` or `$NODE` in a grammar without `USE_AST` no longer compiles. The
  warning names the cause.
- [ADR-0027](0027-remove-the-jjtree-reference-consumer.md) (proposed, on `feature/jjtree`) removes
  `JJTree.waggle` and its golden file; if it is accepted first, the edits to those two files here
  fall away.
- The build's `generateParser` task runs the published plugin, not this source tree. The generated
  parsers pick up the new rule only after the plugin is republished.

## Alternatives considered

**Keep the annotations as the switch (ADR-0016).** This is simpler, but you cannot annotate without
generating a tree, and that is the need behind this ADR.

**Default `USE_AST` to `true`.** This would not break existing grammars, and it matches CongoCC. But
`#Node` would then still switch tree building on by itself, and every grammar that wants
annotations without a tree would have to opt out. Opting in keeps the tree runtime out of a parser
unless the author asked for it.

**Reject `#Node` without `USE_AST` as an error.** This is the strictest option, but it rules out
the case this ADR is for: annotating first.

**Ignore silently.** This avoids the warning in `Waggle.waggle`'s own build. But an author who
forgot the option would get no tree and no reason.
