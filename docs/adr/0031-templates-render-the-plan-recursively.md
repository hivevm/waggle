# ADR-0031: Templates render the plan recursively; a back end writes no control flow

- **Status:** 🟢 Accepted <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-29
- **Deciders:** Markus Brigl
- **Supersedes:** —
- **Superseded by:** —

## Context

[ADR-0029](0029-emitters-only-write-a-finished-plan.md) made the boundary between the front end and
code generation a finished plan: every decision is taken and every number assigned before anything
is written, and an emitter reads records and spells them. It rejected moving emission into templates
with one sentence — that [ADR-0005](0005-custom-template-engine.md)'s engine
"is for whole-file scaffolding, not control flow". That sentence no longer describes the engine.

**What the engine does today.** It is 1180 lines in `org.hivevm.source`, and its directives are
`//@if`, `//@elif`, `//@else`, `//@fi`, `//@foreach`, `//@end`, `//@invoke`, with `__NAME__` for
substitution. Across 54 template files those appear 292, 45, 43 and 38 times. It has conditionals
and it has iteration over a list. What it does not have is a template that applies another template,
so a template cannot follow a nested structure.

**What that costs.** `codegen` holds 868 `print`/`println` calls. 281 of them sit in `TargetSyntax`,
`ParserSyntax` and the two per-target syntax classes, where they spell one statement each; the rest
sit in the emitters, which place those statements. Measured on this project's own grammar, generated
for Java:

| file | template | generated | of which |
| --- | ---: | ---: | --- |
| `Lexer.java` | 321 | 1761 | `jjMoveNfa` 654, string-literal DFA 815, tables 15 |
| `Parser.java` | 560 | 2361 | production bodies 1303, `jj_3` routines 436, tables 2 |

So 83% of a generated lexer and 74% of a generated parser is structure the grammar dictates, and it
is written by hand-placed `print` calls. The scaffolding a template can already fill is filled. What
is left needs a template that can invoke itself for a nested plan node, and the engine's `@foreach`
iterates a flat list.

**The state of the art.** ANTLR's code generator is the closest working system, and it is built on
the separation ADR-0029 already reached: `OutputModelController` builds output model objects with
every choice made, and `OutputModelWalker` then renders them. The walker's mechanism is small — each
output model object has a template *of the same name*; the walker reads the object's public fields
that carry `@ModelElement`, matches them against the template's formal arguments, and calls itself on
each value, so a collection becomes a repeated attribute and the template structure mirrors the model
hierarchy without any traversal code in the model classes. Of the per-language part ANTLR's
[target guide](https://github.com/antlr/antlr4/blob/master/doc/creating-a-language-target.md) says
"there is very little to do here typically", and the advised route to a new target is to copy the
closest `.stg` file and tweak it.

[StringTemplate](https://github.com/antlr/stringtemplate4/blob/master/doc/motivation.md), which
renders it, is deliberately not a programming language. It restricts templates to four operations —
attribute reference, template reference, conditional inclusion of a subtemplate, and template
application to a list — and forbids assignment, loops, arithmetic and calls back into the model, so a
template cannot compute. Its documentation calls recursion "absolutely required for generative
programming", on the ground that an engine without it cannot produce nested output. Three of those
four operations are what this engine already has.

The counter-evidence is worth stating precisely, because it is about a different thing.
[KotlinPoet](https://square.github.io/kotlinpoet/code-control-flow/) models files, classes,
properties and function signatures but deliberately does not model function *bodies*: it has no
expression or statement class, because "creating complex graphs of syntax nodes" is "quite a
challenge for even simple procedural code", and bodies are strings with `beginControlFlow` /
`endControlFlow`. That argues against a general IR of target statements — which ADR-0029 also
rejected and this decision does not revisit. The plan describes the grammar's decisions, not target
statements, and that does not change here.

## Decision

We will **give the template engine recursive template application, and move every generated
statement out of the emitters into templates**, so that code generation holds no decision and no
`print`.

1. **A template may apply another template to a plan record.** The engine gains one directive,
   `//@apply(<attribute>)`, which renders the template named after the record's type against that
   record, and renders each element in turn when the attribute holds a list. Template selection is by
   type name, as ANTLR's walker does it, so a new plan record is served by adding a file, not by
   editing a dispatcher.
2. **The engine gains nothing else.** No assignment, no arithmetic, no expression language, no call
   back into the front end. A template reads attributes, includes a subtemplate conditionally,
   iterates a list and applies a template — StringTemplate's four operations and no more. A
   computation that a template appears to need is a decision that belongs in the plan (ADR-0029).
3. **A back end builds an output model from the plan, and templates render that.** A template cannot
   spell a target's call to a lookahead routine, and the plan may not carry one: target spelling
   inside `analysis` is what ADR-0013 and ADR-0029 removed. So `codegen` derives, per target, a small
   record per thing a template renders, in which every value is already a string of that target,
   the grammar's own code included. This is where ANTLR puts it too: its
   output model is built in `org.antlr.v4.codegen`, not in the analysis. The plan stays the
   language-independent record of decisions; the output model is its target-resolved view, and it
   decides nothing the plan has not already decided.
4. **The per-target syntax objects become spellers.** `TargetSyntax`, `ParserSyntax` and their
   per-target classes stop writing statements: they take no `LinePrinter` and return strings. What
   is left in them is spelling that a template cannot do and that decides nothing — a Rust raw
   identifier, a snake-case name, a hexadecimal literal with its suffix, an escaped string or
   character, the layout of the grammar's own code from its line and column. They are called while
   the output model is built, and a statement, a keyword or a brace is template text. Code generation
   is then the plan, the functions that map it to the output model, those spellers, and the
   templates; a `print` or a test that chooses what to write is in none of them.
5. **Every structure moves, nested or flat.** A nested structure is what `//@apply` is for: the
   `jj_3` routines, whose alternatives hold the alternatives after them; a production's body, whose
   choice chain and plan nodes hold one another; the frame of a loop, whose branches hold what runs
   in them; the string-literal DFA, whose character cases hold an if/else-if chain over the kinds
   they end, each with a block of its own; and the NFA move arms, whose composite states hold such a
   chain as well. A flat run moves too, as a `//@foreach`, and a table as a list. The shapes one
   record can take are branches of one template, selected by `//@if` on the record's components,
   not a file per shape: a template is added per output model record and target, never per case of
   a `switch`.
6. **It is done per structure, and each structure is a step of its own.** A step is finished when
   the generated output of every repository grammar, under every option variant, for all three
   targets, is unchanged apart from differences the step's own commit message lists and justifies.
7. **Indentation is the template's, and comes from one place per level.** A template's own lines
   carry literal tabs; what it applies below takes its level from the tabs the directive captures.
   Mixing the two for one level is what silently shifts a nested block. A spelling of more than one
   line is placed with `//@invoke`, not with a placeholder, because a placeholder indents only its
   first line.

## Consequences

- A target's generated shape becomes readable as target source. Adding a fourth target moves from
  overriding 113 of `TargetSyntax`'s 115 methods to copying a template directory, editing it and
  writing the handful of spellers the target's names and literals need, which is what ANTLR's guide
  describes.
- All of the `print` calls in `codegen` are the population this decision addresses, those in the
  syntax classes included; `LinePrinter` leaves code generation. A decision an emitter still takes
  while it prints — a position test, a sentinel, a choice between two tables — is an ADR-0029 gap,
  and it moves into the plan before its structure moves into a template.
- **Byte-for-byte identity stays the proof.** Moving *placement* into a template moves who writes the
  whitespace, so it was not clear that a step could be proven by an unchanged diff, as every step of
  ADR-0029 was. It can, under §7: with indentation taken from one place per level, the three steps
  done so far left all 1529 generated files unchanged. A step that cannot hold that has most likely
  mixed the two sources of indentation, and the diff says where.
- The engine grows by recursion and a render context per application. It stays far short of a
  language, but ADR-0005's "no third-party templating dependency" is now carrying more weight: the
  directive set, the recursion and its edge cases are ours to test.
- A grammar's own code is copied verbatim into the output at positions the grammar fixed. Its layout
  stays in `TokenCursor`, which becomes a speller: it returns the run as a string, and the template
  writer indents the lines after the first as it does for any multi-line value.
- "No logic" is not literally reached, and the rule says so: the spellers of §4 are code. What they
  may hold is fixed — a pure function from a name, a number or a run of grammar text to a string,
  the same for every grammar — and what they may not hold is a choice of what gets written.
- A second model appears between the plan and the templates. It is mechanical — one record per
  rendered thing, whose components are strings the spellers produced — but it is a layer, and
  keeping it free of decisions is a rule that only review enforces.
- The first version of §5 kept the NFA move arms and the string-literal DFA in their emitters, as
  flat runs with no arm inside an arm. The code says otherwise: a character case of the DFA holds an
  if/else-if chain over the kinds that end there, one of whose actions opens a block of its own, and
  a composite NFA state holds a chain of moves with an accept block inside. They are nested in the
  sense of §5, and their shape variants fit `//@if` branches of one template per record, so the
  "forty files of four lines" the first version feared do not arise.
- Template count grows to roughly one file per output model record per target — around ten records
  each for the parser body, the token loop and the two automata, so on the order of a hundred small
  files. This is a relocation, not a reduction, and it is worth doing only for what it buys: the
  shape lives where a target author reads it, and nothing in code generation decides what is
  written.

## Alternatives considered

- **Leave it.** The prints are spelling, not decisions; ADR-0029 already pulled the decisions out, and
  a generator that writes statements is a normal generator. Rejected as the answer but real as a
  position: nothing is broken, and the gain is in what a fourth target costs, not in correctness.
- **Move only the tables.** The flat lists — masks, next states, literal images, state names — are
  what `@foreach` can already do. Rejected: measured on this project's grammar that is 15 lines of a
  1761-line lexer and 2 lines of a 2361-line parser. The work would not show.
- **Move only what nests; keep the syntax objects writing single statements.** This was the first
  version of §4 and §5. Rejected: its premise that the two automata are flat does not hold (see
  Consequences), and a boundary drawn at "single statement" leaves a `print` behind every hook, so
  the shape of a target stays split between a template and a class that a target author has to
  read together. Spelling that a template cannot do is a narrower and checkable line.
- **Render the plan records themselves**, with no output model in between. Rejected once the first
  structure was attempted: a `jj_3` step holds a `ScanCall`, and what that call is written as is the
  target's business — `jj_3_5()` in Java, `self.jj_3_5()?` in Rust. Giving a plan record that string
  puts target spelling back into `analysis`.
- **A general code IR of target statements**, rendered by templates. Rejected for the reason ADR-0017,
  ADR-0021 and ADR-0029 give, and now with outside support: KotlinPoet deliberately refuses to model
  statement bodies for exactly this reason. The plan describes the grammar's decisions and stops
  there.
- **Adopt StringTemplate instead of extending our engine.** It is the reference implementation of what
  §1 asks for, and ANTLR's experience with it is the evidence this ADR rests on. Rejected on
  ADR-0005's ground, which has not changed: the missing piece is one directive, not a templating
  system, and the directives-as-line-comments form keeps template files readable as target source,
  which `.stg` group syntax does not.
- **Generate through a builder API** per target, as JavaPoet and KotlinPoet do. Rejected: it moves the
  output's shape from a template into a target-specific program, which is where it is today, and it
  gives up the property that a template file reads like the source it produces.
