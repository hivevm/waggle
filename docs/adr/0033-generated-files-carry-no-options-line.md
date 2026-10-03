# ADR-0033: Generated files carry no options line

- **Status:** 🟢 Accepted <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-10-03
- **Deciders:** Markus Brigl
- **Supersedes:** —
- **Superseded by:** —

## Context

Every generated file ends with two lines inherited from JavaCC's `OutputFile`: a checksum of the
text above it, and `// Options: …`. JavaCC used both to decide whether a file could be regenerated
without losing a user's edits. Nothing in Waggle reads either.

The options line is not what its name says. The template writer records every name a template
*looks up* while rendering and lists those names, with their values, at the end. So the line is a
trace of the template's internals: it holds the names of values a back end binds for one file
(`JJ2_ROUTINES=10`, `PRODUCTIONS=35`), it held the names of the functions the engine used to apply
to list elements (`TOKENS_LABEL`, `JJ2_OFFSET_VALUE`) until those went, and it misses every option
the grammar set that this one template happened not to read. Its content changes whenever a template
is reorganised: the change to how lists are iterated changed it in 3840 files of the generation
corpus and forced an update of the AST golden file, while not one generated statement changed.

What a reader could use instead is which settings produced a file. That information already has a
home: the grammar's `options` block states what the grammar sets, and the build that runs the
generator states what the caller sets. A line that repeated both in every file would cost a method
on the engine's `RenderContext` ([ADR-0023](0023-template-engine-independent-of-waggle.md) keeps the
engine from reading Waggle's options itself) and a formatter in `WaggleOptions` whose only reader is
that line.

## Decision

1. **A generated file ends with its checksum.** No options line follows it.
2. **The template writer stops recording what a template looks up.** It is a printer and nothing
   else; rendering reads the caller's environment directly.
3. **The checksum line stays.** Nothing reads it either, but removing it is a decision of its own,
   and it is the line that tells a reader not to edit the file.

## Consequences

- Every generated file loses its last line once. Measured on the generation corpus (39 grammars, 15
  option variants, three targets) that is the only change, in all 17465 files.
- Reorganising a template no longer touches any generated file whose statements it does not change,
  and the AST golden file no longer has to follow such a change.
- A file no longer states which settings produced it. A reader who wants to know reads the grammar
  and the build.
- `TemplateWriter` loses its second role as an `Environment`.

## Alternatives considered

- **List the options that were set**: those of the grammar's `options` block and those of the
  caller, minus `OUTPUT_DIRECTORY`, which would put a path of the generating machine into the
  output. Stable across template changes, and briefly implemented. Rejected: it copies information
  that has a home, and it needs the engine to ask its caller for a footer, which nothing else uses.
- **List every option with its effective value.** Stable as well, but about forty entries per file,
  most of them defaults, for the same lack of a reader.
- **Remove the checksum as well.** See decision 3.
