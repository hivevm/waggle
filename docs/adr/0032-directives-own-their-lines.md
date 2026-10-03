# ADR-0032: A directive owns its line, and a placeholder indents what it writes

- **Status:** 🟢 Accepted <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-10-03
- **Deciders:** Markus Brigl
- **Supersedes:** — (replaces §7 of [ADR-0031](0031-templates-render-the-plan-recursively.md); the rest of
  ADR-0031 stands)
- **Superseded by:** —

## Context

[ADR-0031](0031-templates-render-the-plan-recursively.md) moved every generated statement into
templates. What it left is how a template's whitespace becomes the output's, and that rule is
implicit and uniform where it should not be:

- **Every directive eats one line break after it**, wherever it stands. On a line of its own that is
  what one wants — the line disappears. After text it is a trick: `switch (//@if(cached)` followed
  by `jj_nt.kind//@else` on the next line splices three source lines into one output line. The
  templates came to rely on it. 85 lines in 58 templates carry a directive next to text, and the
  parser-body templates turned it into a convention: a statement template starts with a line break
  and ends without one, so that its parent can end a line with `{\t//@apply(action)` and let the
  child break it. Two traps followed, both recorded while ADR-0031 was carried out: an
  `//@apply` at the end of a line eats the break the next line needed, so templates carry empty
  lines to feed it; and a directive directly followed by a word character (`//@fitrace`) or by
  `(` is read as another directive or as a parameter.
- **The tabs before a directive are dropped** — and for `//@apply` and `//@invoke` they become the
  indentation of what is applied. Before any other directive they simply vanish. Before a placeholder
  they are text.
- **A placeholder indents only its first line.** ADR-0031 §7 therefore placed a multi-line value
  with `//@invoke`, a second spelling of the same lookup whose only difference is that its tabs are
  indentation. After ADR-0031 was carried out no template uses `//@invoke` any more: every
  multi-line value now sits in a record of its own and is placed by `//@apply`.

**The state of the art draws the line where a tag stands alone.** The Mustache specification calls
a tag on a line of its own *standalone*: the whole line is removed, and for a partial "any whitespace
preceding the tag should be treated as indentation, and prepended to each line of the partial before
rendering" ([partials.yml](https://github.com/mustache/spec/blob/master/specs/partials.yml)). Jinja
offers the same as two switches, `trim_blocks` ("the first newline after a template tag is removed")
and `lstrip_blocks` ("strip tabs and spaces from the beginning of a line to the start of a block.
Nothing will be stripped if there are other characters before the start of the block") — note the
proviso: a tag after text keeps its surroundings
([whitespace control](https://jinja.palletsprojects.com/en/stable/templates/#whitespace-control)).
StringTemplate, ANTLR's engine, "automatically and naturally indents output by tracking the nesting
level of all attribute expression evaluations and associated whitespace prefixes": a multi-line
value is indented by the whitespace in front of the expression
([indent.md](https://github.com/antlr/stringtemplate4/blob/master/doc/indent.md)). None of them
removes a line break after a tag that shares its line with text.

## Decision

We will make whitespace follow from where a directive stands, and give placeholders the
indentation `//@invoke` existed for.

1. **A standalone directive owns its line.** A directive alone on its line — tabs before it, the line
   break after it, nothing else — is removed together with that line. For `//@apply` the tabs are
   the indentation of everything the applied template writes, every line of it.
2. **An inline directive owns nothing but itself.** A directive that shares its line with text, or
   with another directive, is replaced by what it renders. The tabs before it are text, and the line
   break after it stays.
3. **A placeholder indents what it writes.** When only tabs stand before a placeholder on its line,
   every line of its value after the first is indented by those tabs, as the first is.
4. **`//@invoke` is removed.** It is a placeholder that indents, which (3) makes every placeholder.
5. **A template applied as a statement writes whole lines.** It starts at the beginning of a line
   and ends with a line break; its parent places it with a standalone `//@apply`. A template applied
   inside a line — a condition, a call, a list of arguments — writes no line break at either end,
   and its parent places it inline.
6. **An empty parameter list ends a directive** where text follows it directly: `//@fi()trace_call(…)`.
   Without it, `//@fitrace_call` is read as one unknown name. One-line fragments with branches —
   C++'s `{ if (!jj_rescan) trace_return(…); return true; }` after an `if` — need it.
7. **`//@else`, `//@fi` and `//@end` take no parameter.** A parameter after them is an error. It used
   to be dropped without a word, and the text it was taken from with it: `//@else(jj_ntk == -1) ? …`
   lost its first parenthesis.

## Consequences

- A template reads as the output it produces: a line of the template is a line of the output, or it
  holds only a directive and is not there at all. The splices go: `switch` with a cached and an
  uncached token kind is two standalone branches of one `//@if`, not one statement broken over three
  lines.
- The parser-body and lookahead templates of all three targets turn their line-break convention
  around, from "starts with a break" to "ends with one". 85 lines with a directive next to text in
  58 templates become 35, none of which relies on a line break being eaten; what is left is a
  fragment of one line: a condition, a suffix, a case label.
- **The generated parsers change, in whitespace only.** Measured on the generation corpus (39
  grammars, 15 option variants, three targets), every generated file is the same as before once its
  whitespace is removed, and every lexer, constants file, tree file and runtime file is the same byte
  for byte. In 587 parser files the trick had glued statements together or left blank lines, and
  those are gone:
  - a test that opens a choice on a semantic lookahead stood on the line before it —
    `}if (checkEmptyLA(…)) {` in Waggle's own parser — and now starts its own line;
  - the end of a DEBUG_PARSER trace, a DEPTH_LIMIT guard and a C++ node scope was glued to the last
    statement — `value(1);} finally {`, `{if(++jj_depth > 10) {`, `jj_consume_token(0);} catch (...) {`
    — and starts its own line;
  - a C++ `break` was glued to the action of its case — `value(0);break;` and `;break;` — and stands
    on its own line, as does a C++ `assert(false);` after a `return`;
  - blank lines after `default: {`, `_ => {`, after `try {` inside a trace, after the C++ trace
    guards and after a Rust `open_node_scope` are gone; they were the line break a child template
    opened with, written where the parent had already broken its line.

  So the proof of each step is no longer an unchanged diff but an unchanged token stream and a diff
  whose every hunk falls into one of those categories.
- `BodyModel.If.leadingBlank` goes: it existed to reproduce the glued semantic test. The production's
  node comment (`// Grammar`) moves from `ScopeOpen` into the production's template, which writes it
  on the line of the signature; `ProductionModel.Production` carries the descriptor for it.
- The traps go with the trick they belong to. A directive followed directly by text is ended by
  `()`, and a parenthesis after `//@else` is an error instead of a lost parameter.
- An inline `//@apply` of a statement template is a template error the engine does not catch: it
  writes the child's line break where the parent did not expect it. The corpus diff catches it, as
  it catches any other change of output.
- The engine loses a directive and the code that told `//@invoke`'s tabs from text; the placeholder
  gains the indentation every applied template already had. [ADR-0005](0005-custom-template-engine.md)
  still lists `//@invoke` among the directives; this ADR removes it.

## Alternatives considered

- **Trim markers on each tag**, as Jinja's `{%- … -%}`. Rejected: it adds syntax to a
  directive set ADR-0005 and ADR-0031 keep minimal, and it makes each template author decide per tag
  what the standalone rule decides once.
- **Keep the break-eating rule and document it.** Rejected: the rule is documented in the engine
  already, and it is the source of the traps and of the splices, not a lack of documentation.
- **Allow only placeholders inline, every directive standalone.** Rejected: an inline `//@apply` of a
  condition (`if //@apply(condition)`) and an inline `//@if` around a suffix
  (`jj_consume_token(…)//@if(field).__field__//@fi;`) are the readable form; forbidding them would
  push target text into the output model, which ADR-0031 §4 forbids.
- **Keep `//@invoke` beside indenting placeholders.** Rejected: two spellings of one lookup is what
  this ADR removes, and nothing uses it.
