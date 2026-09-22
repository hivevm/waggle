# ADR-0017: The lexer back ends compose emitters over a target syntax, instead of inheriting one

- **Status:** 🟢 Accepted
- **Date:** 2026-09-20
- **Deciders:** HiveVM Waggle maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

[ADR-0012](0012-lexer-owns-dfa-construction.md) made the lexer stage the owner of NFA/DFA
construction and the back ends pure renderers. That is now true: no generator changes the lexer
model, and `LexerLayeringTest` keeps it that way. What ADR-0012 named as follow-up — "the remaining
generator-side lexer duplication … can be revisited against a stable, finished model" — is what is
left, and it is the largest single thing in the code base.

`generator/LexerGenerator` is **3064 lines**. It renders the token manager by printing target source
directly, behind roughly **109 overridable print hooks** — `charEquals`, `bitIsSet`, `printIf`,
`printCasesOpen`, `printBreak`, `printCheckNAddStates`, `printCanMoveArm` and so on. The back ends
are subclasses that override the hooks they need: **C++ overrides 67, Rust 117, Java 1**. Two
consequences follow.

**The base class is not a base class.** With 109 hooks it is a class that knows every dialect
difference between three languages and asks a subclass which one applies. A hook is added whenever
one target needs a different character, so the count only grows, and the hooks are indistinguishable
from the algorithm that calls them: `dumpDfaStates` is 100+ lines of control flow interleaved with
`printSwitchOnChar`, `printActiveCheck`, `printEofBailout`.

**Rust could not use it anyway.** `RustLexerGenerator` (1537 lines) overrides 117 hooks *and*
re-implements `dumpDfaStates` and `dumpMoveNfa` outright. The inheritance did not save the
duplication it exists to prevent; it produced a second copy of the two biggest routines, one of which
can now drift from the other exactly as the DFA shaping did before ADR-0012.

The pattern is Template Method, and the classical answer when the hook count runs away is to
[replace it with delegation](https://xp123.com/refactor-template-method-to-strategy/): Template
Method alters an algorithm by *extending* it,
[Strategy by *composing* it](https://refactoring.guru/design-patterns/template-method), which moves
the variation from a class hierarchy into an object the algorithm holds. Almost all 109 hooks are
one thing — how a target spells a comparison, a branch, a switch arm, a break. They are a
*vocabulary*, not a set of behaviours.

Two constraints bound the answer. The output must stay byte-for-byte identical, because that is the
only proof available that moving emission code did not change what it emits. And a general code IR —
an abstract syntax the three back ends render — is out of scope: it is a much larger design, it
would swallow the parser generator too, and nothing here needs it.

## Decision

We will **split the lexer emission into emitters that compose a target syntax**, rather than a base
class that subclasses extend:

1. **`TargetSyntax` is one object per language.** It answers the dialect questions the emission asks
   — how a comparison, a branch, a switch, a break, a state-set call is spelled — and it is passed
   to the emitters, not inherited by them. The 109 hooks become its methods; nothing is added that
   no emitter calls.
2. **The algorithm splits into emitters**, each owning one routine that is today a method of the
   3064-line class:
   - `StringLiteralDfaEmitter` — the per-position string-literal DFA (`dumpDfaStates`),
   - `NfaMoveEmitter` — the NFA move loop (`dumpMoveNfa`, the ASCII and non-ASCII move code),
   - `GetNextTokenEmitter` — `getNextToken`, the lexical-state switch, the skip/more/token branches,
     the lexical actions and the error epilogue.
   Each takes a `TargetSyntax` and the finished lexer model, and writes to a `LinePrinter`. The
   tables the lexer emits (state sets, non-ASCII moves, static declarations) stay with the generator:
   they are a handful of short methods, and a `TableEmitter` for them would be a class with nothing
   to decide.
3. **A back end is a `TargetSyntax` plus, where it must, its own emitter.** Rust keeps its own
   string-literal and NFA-move emitters as long as they genuinely differ; it stops being a subclass
   that overrides 117 hooks in order to reach them.
4. **No class in the generator packages exceeds 1000 lines.** This is the measurable form of the
   decision, and the number is a limit, not a target to sit under by moving code sideways.
5. **No code IR.** The emitters print target source through `TargetSyntax`. Introducing an abstract
   instruction set that all three targets render is a different decision, and would need its own ADR.

## Consequences

- Each of the three routines can be read, and tested, without the other two and without a subclass.
  Today reading `dumpMoveNfa` means holding 109 hook implementations in mind across three files.
- Adding a target becomes writing a `TargetSyntax`, and only writing an emitter where the target
  genuinely differs — which is what ADR-0004 promises and what the current hierarchy does not
  deliver.
- Rust's duplicated `dumpDfaStates`/`dumpMoveNfa` become explicit alternatives rather than accidental
  overrides. They are not removed by this decision; making them unnecessary needs the emitters to
  exist first.
- The change is large and entirely mechanical in the risky direction: every line moved must print
  the same characters. It is landed in steps, each verified by a byte-for-byte diff of the generated
  Java, C++ and Rust.
- Risk: a syntax object with 125 methods is not obviously better than a base class with as many
  hooks. It is better only if the methods are a vocabulary — answers to "how is this spelled" — and
  not behaviour. Any method that turns out to carry algorithm belongs in an emitter instead, and if
  most of them do, this decision was wrong and the split should be reconsidered rather than forced.
  A dozen methods that looked like hooks did carry algorithm and moved into the emitters instead.
- `LexerGenerator` implements `TargetSyntax` rather than holding one, so the C++ and Rust back ends
  still reach their dialect by extending it. The emitters compose it, which is what removed the
  3064-line class; making the generator compose it too would mean delegating 125 methods by hand and
  is a separate step, worth taking only if the syntax objects turn out to be worth naming on their
  own.

## Alternatives considered

- **Leave the hierarchy and only split the file.** Rejected: the 1000-line limit would be met by
  moving methods into sibling classes while every one of them still depends on being a subclass. The
  size is the symptom; the coupling is the defect.
- **Introduce a code IR and render it per target.** Rejected as out of scope: it is a far larger
  design that would have to cover the parser generator as well, and none of the present problems
  need an abstract instruction set to be solved. It stays available as a later decision.
- **Generate the token manager from templates, as the runtime classes are.** Rejected: the emitted
  code is derived from the DFA, not from a fixed skeleton with holes; the template engine of
  [ADR-0005](0005-custom-template-engine.md) has directives and substitutions, not the control flow
  this needs.
- **Give Rust its own generator with no shared base at all.** Rejected: it is where the code already
  drifts to, and it gives up the sharing that C++ and Java genuinely get. The point is to make the
  sharing explicit, not to abandon it.
