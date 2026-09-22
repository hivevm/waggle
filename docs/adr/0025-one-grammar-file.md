# ADR-0025: A grammar is one file; the sibling `.lex` is removed

- **Status:** 🟢 Accepted <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-21
- **Deciders:** Markus Brigl
- **Supersedes:** [ADR-0010](0010-unified-grammar-and-actual-surface-syntax.md) in part — only
  decision 4's "or in an optional sibling `.lex` file that the driver appends"; the rest of ADR-0010
  stands. Through it, the last live part of
  [ADR-0008](0008-grammar-syntax-and-lexical-file.md) decision 4.
- **Superseded by:** —

## Context

[ADR-0008](0008-grammar-syntax-and-lexical-file.md) split a grammar into two files: productions in
one, token definitions in a sibling `*.lex`. [ADR-0010](0010-unified-grammar-and-actual-surface-syntax.md)
kept that as an *option* — tokens inline in the grammar, or in a `.lex` the driver appends — and
[SPECIFICATION §4](../SPECIFICATION.md) and §5 document both forms.

What the split actually costs, measured in this repository:

- **The driver concatenates two files before parsing.** `WaggleCompiler` derived the sibling name by
  replacing the extension, read it if it existed, and appended its text. A grammar's meaning
  therefore depended on a file that is never named anywhere in it: no `include`, no option, no
  diagnostic when the sibling is missing, misspelled or silently not picked up.
- **Every consumer has to know the rule.** The Gradle task re-implemented the same
  extension-replacement to declare the sibling as a task input, because a `@CacheableTask` that does
  not list an input it reads recomputes nothing when that input changes (ADR-0006).
- **One grammar in the repository used it**, `JJTree.waggle` with `JJTree.lex`, and its content is
  ordinary `TOKEN` / `SKIP` / `MORE` / `SPECIAL_TOKEN` blocks that the grammar file accepts verbatim.
  Appending them to the grammar is a pure move of 179 lines.
- **It was never a stage.** ADR-0010 already removed the two-step pre-processing model; this is the
  last place where "what the generator reads" is not "the file you gave it".

The separation ADR-0008 wanted — the lexical specification legible on its own — is a matter of where
the blocks sit in a file, which the grammar syntax already allows: the token productions go at the
end, under their own heading. Nothing about the *syntax* changes here, only how many files carry it.

**The state of the art is a single unit.** ANTLR does support a split, but as an explicit
declaration: a lexer grammar is its own named grammar and a parser grammar names it with
`options { tokenVocab = … }`, so the dependency is written in the file rather than inferred from its
name. Waggle has no such declaration and would have to invent one to keep the split honest. Yacc/Bison
and Lex/Flex are two files because they are two *tools* with two languages; Waggle is one tool with
one language, so the analogy does not carry.

## Decision

We will make a grammar exactly one file:

1. **`.waggle` is the whole input.** Token definitions live in the grammar file, in the same
   `TOKEN` / `SKIP` / `MORE` / `SPECIAL_TOKEN` blocks as today. The syntax is unchanged.
2. **The driver reads one file.** `WaggleCompiler` no longer derives, reads or appends a sibling.
3. **The Gradle task declares one input per generation unit**, the grammar, and drops the
   extension-replacement helper it used to find the sibling.
4. **`JJTree.lex` is merged into `JJTree.waggle`** and deleted. It was the only `.lex` in the tree.
5. **[SPECIFICATION §4 and §5](../SPECIFICATION.md) are amended** to describe one input file and to
   drop the "Lexical file (`.lex`)" vocabulary entry. This ADR does not get to change the
   specification by itself: the amendment is part of accepting it.

## Consequences

- A grammar says what it is. There is no second file whose absence changes the result silently, and
  no naming convention a reader or a tool has to know.
- **Breaking for any grammar that uses a `.lex`.** Such a grammar now generates a parser with no
  tokens at all rather than failing, because the token blocks simply are not there — the lexer
  reports the grammar's own errors, not a missing file. The migration is mechanical (append the
  `.lex` to the `.waggle`), and the release notes have to say so.
- One less thing for a new back end, a new tool or an editor integration to replicate. The Gradle
  task's input declaration gets simpler and cannot drift from the driver's rule, because there is no
  rule.
- The `tokenVocab`-style option ANTLR offers is **not** introduced. If sharing a lexical
  specification between grammars becomes a real need, it is its own decision and its own ADR, and it
  would be a declaration inside the grammar rather than a convention over file names.
- ADR-0008's original motivation — a lexical specification that can be read on its own — is served by
  keeping the token blocks together at the end of the grammar, which is a convention this repository
  follows and does not enforce.

## Alternatives considered

- **Keep `.lex` as an option.** Rejected: an optional input that is discovered by name is the part
  that costs, not the part that helps. Every consumer must re-implement the discovery, and a missing
  or misspelled sibling is indistinguishable from a grammar that defines no tokens.
- **Keep the split and make it explicit**, with an `include` or a `tokenVocab` option. Rejected as
  YAGNI (AGENTS.md §1): it adds grammar syntax and a resolution rule to serve one grammar in the
  repository and no known user need. It stays available if sharing ever becomes real.
- **Require the split instead of removing it** — every grammar must have a `.lex`. Rejected: it is
  the same coupling with less freedom, and it breaks every grammar that defines tokens inline, which
  is what the tutorials teach.
