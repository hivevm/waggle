# ADR-0029: Emitters only write; the front end hands over a finished plan

- **Status:** 🟡 Proposed <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-29
- **Deciders:** Markus Brigl
- **Supersedes:** —
- **Superseded by:** —

## Context

[ADR-0012](0012-lexer-owns-dfa-construction.md) made the lexer stage the owner of NFA/DFA
construction and the generators "pure renderers" that "do not recompute DFA structure while
emitting". [ADR-0017](0017-lexer-emission-by-composition.md) and
[ADR-0021](0021-parser-emission-by-composition.md) then split emission into emitters that compose a
per-language syntax object, so that a target spells and the shared emitter decides. Both are done.
What neither made concrete is *what the emitters read*: they read the front-end model itself —
`NfaStateData`, `NfaState`, `LexerData`, `ParserData`, `Expansion` — and a good part of their code
is still deciding things about it while printing.

On the lexer side, the dry run ADR-0012 set out to remove is still there for the NFA moves:

- `DfaBuilder.getMoves` and `getCompositeStateMoves` walk the automaton to register the
  `jjnextStates` sets; `NfaMoveEmitter` (693 lines) walks it again while printing, keeping a
  `dumped[]` array per byte range, recomputing `nextIntersects`, merging equivalent states into one
  case label and grouping composite states with `asciiPartition`. The label order in the output
  depends on the order of that second walk.
- `StringLiteralDfaEmitter` (602 lines) repeats the plain-skip and ignore-case filtering
  `DfaBuilder.getDfaCode` already did, parses `StopKey` strings back into numbers, tests bits in
  `finalKinds` words, and keeps a `stopAtPosDumped` flag across lexical states. `maxStrKind / 64 + 1`
  is computed six times across the back ends.
- `GetNextTokenEmitter` decides per kind whether it gets an action case, whether it needs the
  empty-match loop check, where its image comes from, and what the skip condition is.
- The C++ header re-derives by hand the conditions under which the `.cc` defines a function, and
  `TargetSyntax.InitStateName` resolves a composite state name — lexer work behind a spelling
  interface.

On the parser side, `ParserPlanner` already fixes the hard numbers (mask slots, `jj_2` numbers,
routine names), but the emitters still compute:

- **order-dependent state**: loop labels come from a counter handed out while
  `DUMP_NORMALPRODUCTIONS` renders; whether a `jj_3` routine has declared `xsp` is a flag threaded
  through the walk; the lookahead chain is a state machine (`NOOPENSTM`/`OPENIF`/`OPENSWITCH`) plus a
  count of blocks to close, advanced as it prints;
- **facts recovered from strings**: a scan call is recognised by
  `internalName(e).startsWith("jj_scan_token")` in four places, because the planner stores the
  Java/C++ call text as the "internal name" — target spelling inside `analysis`, against
  [ADR-0013](0013-break-model-parser-dependency-cycle.md) and
  [ADR-0019](0019-package-layout-and-dependency-dag.md); the `jj_save` slot is recovered with
  `Integer.parseInt(name.substring(1)) - 1` in the Java and Rust back ends;
- **walks repeated**: which sequence units a `jj_3` routine scans (`count -= minimumSize`) is decided
  three times — twice in the planner and once in `Phase3Emitter`; the token mask table is transposed
  from slots to words in the generator;
- **target capabilities as overrides**: C++ forces the mask slot to −1 and `rescans` to false in the
  emitter because it records no expected tokens.

The tree side decides the node class of a scope and whether an arity is a count or a condition when
it prints the scope.

This matters for three reasons. Every emitter that decides is a second place a decision can be wrong
— the drifted bit-count copy ADR-0012 cites was exactly that. Output that depends on the order an
emitter happens to print in is fragile: the Rust indentation drift and the missing Rust node arities
fixed in 31f560e4 were both decisions taken inside emission that one target took differently. And
the emitters cannot be tested apart from what they print; a wrong decision shows up only as a diff.

**The state of the art builds the output model before rendering.** ANTLR's code generator first
builds an output model — `OutputModelController` creates `OutputModelObject`s (`RuleFunction`,
`LL1AltBlock`, `TestSetInline`, …) with every choice made and every number assigned — and only then
walks them with a target's StringTemplate group, which spells and does not decide. The
[target guide](https://github.com/antlr/antlr4/blob/master/doc/creating-a-language-target.md) says of
the per-language part that "there is very little to do here typically".

As in ADR-0017 and ADR-0021, two constraints bound the answer: the generated output must stay
byte-for-byte identical, because that is the only available proof that moving a decision did not
change it; and a general code IR of target statements stays out of scope.

## Decision

We will **make the boundary between the front end and code generation a finished plan**: the
lexer stage and the parser analysis produce immutable plan records in which every decision is
taken and every number assigned, and the emitters only write them out.

1. **The lexer stage produces a `LexerPlan`** at the end of `LexerBuilder.build()`, reachable from
   `LexerData`, holding only numbers, enums, records and model `Action`s — no target identifiers.
   Per lexical state it holds the literal DFA by position (the character cases, their final kinds
   and what each does next), the stop DFA, and the NFA move arms with their labels in output order.
   It also holds the token-loop dispatch (per kind: action case, loop check, image source), the
   skip conditions, the `jjCanMove` cases and the tables. **The NFA arms are recorded by the same
   walk that registers the next-state sets**, so there is one walk, not a walk and a replay.
2. **The parser analysis produces a `ParserPlan`**, fulfilling the rename ADR-0019 already decided
   for `ParserData`. It holds per production a tree of plan nodes (consume, call, code, sequence,
   decide, repeat, scoped) with loop labels assigned; per choice the finished decision steps — how
   each opens, which slot it records, which tokens it switches on, how many blocks the fallback
   closes; the `jj_2` routines with their save slots and the `jj_3` routines with their bodies as a
   list of scan steps, already cut to the tokens they scan; and the mask table in the layout it is
   printed in. A scan call is a typed value, not a name that starts with `jj_scan_token`.
3. **The tree module adds a `ScopePlan` per node scope** to `TreeModel`: node class, variable
   names and an arity that says whether it is always, a count, a condition or a greater-than test
   ([ADR-0016](0016-tree-building-as-an-optional-module.md)).
4. **A back end states what its target can do before planning, not while emitting.** What differs
   by target is a capability (C++ records no expected tokens), and it reaches the planner as a small
   profile record owned by `analysis`, so the package graph does not change. Target validation (the
   Rust name collisions, the rejected options) runs on the plan before emission.
5. **Emitters and syntax objects only write.** An emitter reads plan records and the options, and
   calls its `TargetSyntax`/`ParserSyntax` for spelling; it keeps no counters, flags, marks or maps
   across calls, and parses nothing back out of a string. `TargetSyntax` and `ParserSyntax` stop
   taking `NfaState`, `NfaStateData` or `Expansion`. What stays in code generation is spelling:
   naming (snake case, `RustIdentifier`), text encoding (UTF-8 in C++, Rust escapes), line wrapping
   and the layout of verbatim grammar code.
6. **It is enforced.** `LexerLayeringTest` and `PackageDagTest` are extended so that emitters may
   import the plan records but not the construction model.
7. **It is done in steps that each keep the output byte-for-byte identical**, lexer and parser
   separately, each step its own commit proven by regenerating every repository grammar under every
   option variant for all three targets. Any difference between targets found on the way is a bug,
   reported and fixed in its own commit, not absorbed into the move.

## Consequences

- Each decision about the generated code is taken once, in the front end, and is testable there: a
  plan can be asserted on (labels, slots, arms in order) without generating and diffing source.
- The emitters shrink to loops over records; the lexer emitters are expected to lose roughly 40%.
  The walks do not disappear — they move into `lexer` and `analysis` — so the parser side is about
  net-neutral in lines. A fourth target writes spelling only.
- The front end gains a second representation beside the construction model. The plan must be
  built after everything it records is final — literal images only after `pruneLiteralImages`,
  next-state sets in their registration order — and keeping the two consistent is new work.
- The riskiest moves are those whose order reaches the output: the NFA arm labels and the
  `jjnextStates` registration order, the `statesForPos` hash order, loop label numbering, and where
  `xsp` is declared. They come last in their sequence. The layout of verbatim grammar code (the
  token cursor) is the most fragile of all and is the last, optional step.
- ADR-0012 and ADR-0021 stay in force; this makes their "pure renderer" concrete and moves the
  line between decision and spelling. ADR-0017's `TargetSyntax` loses its model parameters.

## Alternatives considered

- **Leave it.** ADR-0012's goal is written down and the emitters already share one copy per
  routine. Rejected: the remaining dry run is exactly the duplication ADR-0012 was accepted to
  remove, and decisions inside emission are where the targets have drifted apart.
- **Precompute only the hot spots** — for instance the NFA arms and the `jj_scan_token` strings —
  and leave the rest. Rejected as a decision, kept as a sequence: without a rule that emitters only
  write, the next feature puts its decision back into an emitter. The step order above does start
  with the hot spots.
- **Per-target planners** in `codegen`, each building the plan its target needs. Rejected: the
  inventory found no target that needs different *data*, only different spelling and one capability
  flag; three planners would recreate the three copies ADR-0021 removed.
- **A code IR of target statements** that the back ends render. Rejected as out of scope, as in
  ADR-0017 and ADR-0021: the plan describes the grammar's decisions, not target code, and that is
  enough for byte-identical output.
- **Move emission into templates**, as ANTLR does. Rejected for the reason ADR-0021 gives: ADR-0005's
  template engine is for whole-file scaffolding, not control flow.
