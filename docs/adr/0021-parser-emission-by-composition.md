# ADR-0021: The parser back ends compose over a target syntax, as the lexer back ends do

- **Status:** 🟢 Accepted <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-21
- **Deciders:** HiveVM Waggle maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

[ADR-0017](0017-lexer-emission-by-composition.md) replaced the lexer back ends' Template Method with
composition: `TargetSyntax` answers how a language spells a comparison, a branch or a switch arm,
and emitters take one instead of inheriting it. That is done. The parser back ends were not touched,
and they are the same shape ADR-0017 argued against.

`codegen/ParserGenerator` is 376 lines and abstract. The three subclasses are
`JavaParserGenerator` (519 lines), `CppParserGenerator` (491) and `RustParserGenerator` (507) —
1517 lines of back end over a 376-line base. Each declares roughly the same twenty methods, in the
same order, with the same names:

| | Java | C++ | Rust |
| --- | --- | --- | --- |
| `generate_phase1_head` / `_body` / `_regexp` / `_choice` / `_nonterminal` / `_more` | ✓ | ✓ | ✓ |
| `print_lookahead` / `_amount0` / `_amount1` / `_tail` | ✓ | ✓ | ✓ |
| `generate_phase2`, `generate_phase3_routine` | ✓ | ✓ | ✓ |
| `buildPhase3RoutineRecursive`, `declareXsp`, `printScanLoop` (all `private`) | ✓ | ✓ | ✓ |

The last row is the telling one. `buildPhase3RoutineRecursive` — the routine that walks an expansion
and writes the lookahead code for it — exists three times as a *private* method: 116 lines in Java,
102 in C++, 109 in Rust. A diff of the Java and C++ copies is 85 lines, and every one of those lines
differs only in spelling:

~~~java
// JavaParserGenerator
printer.println("if (" + genjj_3Call(ntexp) + ")");
printer.indent();
printer.println(genReturn(jj3_expansion, true, data));
printer.outdent();

// CppParserGenerator — same branch, same order, same operands
printer.println("    if (" + genjj_3Call(ntexp) + ") " + genReturn(jj3_expansion, true, data));
~~~

The control flow — which expansion kinds are visited, in what order, what is emitted for each — is
identical. Only the brace, indent and statement style differ. This is the duplication ADR-0017
removed on the lexer side, one layer up: there, inheritance produced a second copy of `dumpDfaStates`
and `dumpMoveNfa`; here it produced a third copy of the phase-3 walk, and nothing keeps the three in
step. The lexer back ends now stand at 337 shared lines plus 88 (Java), 700 (C++) and 802 (Rust),
against a 869-line `TargetSyntax` that holds the dialect vocabulary for all three.

**The state of the art separates the algorithm from the spelling.**
[ANTLR](https://github.com/antlr/antlr4/blob/master/doc/creating-a-language-target.md) has no
per-language code generators: one tool generates for every target, the shared algorithm builds an
output model, and a target contributes a `Target` subclass that "describes language specific details
about escape characters and strings and so on — there is very little to do here typically", plus a
StringTemplate `.stg` group that says how each construct is written. A new target author writes the
spelling, never the traversal. The
[`Target`](https://www.antlr.org/api/JavaTool/org/antlr/v4/codegen/Target.html) /
[`CodeGenerator`](https://www.antlr.org/api/JavaTool/org/antlr/v4/codegen/CodeGenerator.html) split
is the same division ADR-0017 arrived at independently for the lexer.

Two constraints bound the answer, as in ADR-0017. The output must stay byte-for-byte identical,
because that is the only available proof that moving emission code did not change what it emits. And
a general code IR — an abstract syntax tree of target code that the back ends render — stays out of
scope: it is a much larger design, and nothing here needs it.

## Decision

We will **split the parser emission into emitters that compose a parser syntax**, mirroring
ADR-0017:

1. **`ParserSyntax` is one object per language**, the parser-side counterpart of `TargetSyntax`. It
   answers the spelling questions the emission asks — how a call, an `if`, a `switch` arm, a
   `return`, a local declaration and a scan loop are written — and it is *passed* to the emitters,
   not inherited by them. Nothing is added to it that no emitter calls.
2. **The shared traversal moves into emitters** that exist once:
   - `Phase3Emitter` — the lookahead routine walk that is today `buildPhase3RoutineRecursive`,
     `declareXsp` and `printScanLoop`, three times over;
   - `LookaheadEmitter` — `print_lookahead`, `print_lookahead_amount0`, `print_lookahead_amount1`
     and `print_lookahead_tail`;
   - the `generate_phase1_*` family needs **no** emitter of its own: its traversal
     (`generate_phase1_expansion`, about 100 lines) already exists once, on `ParserGenerator`, and
     only its eight spelling hooks were per-target. Those move to `ParserSyntax` —
     `consumeToken`, `noAlternativeMatched`, `callProduction`, `openRepetition` and so on — so the
     vocabulary is one object per language. Wrapping an already-single traversal in a class would
     move code without removing any, which AGENTS.md §1 asks us not to do.
3. **A back end keeps only what is genuinely its own.** `RustParserGenerator`'s snake-case naming of
   internal names, and each target's file and class preamble, stay with the back end; they are not
   spelling questions with a shared answer.
4. **The output does not change.** The refactor is complete when the generated sources for every
   grammar in the repository, for all three targets, are byte-for-byte what they were before it.

## Consequences

- One copy of the phase-3 walk instead of three. A fix to the lookahead traversal — the part of the
  generator most likely to be wrong — lands once and reaches every target, rather than being applied
  to Java and forgotten in C++ and Rust.
- `codegen` gains a second vocabulary interface beside `TargetSyntax`, and the two will overlap
  (both know how a target spells a branch). Merging them is *not* part of this decision: the lexer
  and parser emissions have different callers and different shapes, and a premature merge would
  recreate the 109-hook class ADR-0017 dismantled. If the overlap proves real once both exist, it is
  its own ADR.
- A fourth target becomes markedly cheaper: a `ParserSyntax` and a `TargetSyntax`, not a
  1500-line parser back end.
- The diff is large and mechanical, and it touches the generator's least-tested area. It is only
  safe against the byte-for-byte check in decision 4, which must run before the change is proposed
  for merge, not after.
- Risk: the three copies may have silently drifted already. Any behavioural difference found while
  unifying them is a bug in one of the targets, and must be reported and fixed as its own change —
  not absorbed into the refactor, where the byte-for-byte check would hide it.

## Alternatives considered

- **Leave it.** The lexer was the larger duplication and it is done. Rejected: the parser back ends
  are 1517 lines with a three-times-duplicated private core, and every future parser feature is
  three edits. The argument of ADR-0017 does not become weaker one layer up.
- **Add more hooks to `ParserGenerator`.** This is what the code does today and how it reached three
  copies of one routine: when a target needed a different shape rather than a different character,
  the method was overridden whole. Rejected for the reason ADR-0017 gives — Template Method alters an
  algorithm by extending it, and the hook count only grows.
- **Move parser emission into templates**, as ANTLR does with `.stg` groups. Rejected: ADR-0005
  chose a deliberately small template engine for whole-file scaffolding, not for per-construct
  emission driven by a traversal; making it express control flow would turn it into the general
  template language that ADR-0005 declined to depend on.
- **Introduce a code IR** that the three back ends render. Rejected as out of scope, for the reasons
  ADR-0017 already recorded: it is a much larger design, and nothing in the current targets needs it.
- **Reuse `TargetSyntax` directly for the parser.** Rejected: it is the lexer's vocabulary, shaped by
  DFA emission (`printCheckNAddStates`, `printCanMoveArm`). Loading parser concerns into it would
  rebuild the class that knows every dialect difference in the project.
