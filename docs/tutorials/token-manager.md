# The Token Manager

The **token manager** (also called the *lexer*) is the layer between raw characters and the parser.
It reads the character input (see [The Character Input](charstream.md)) and groups characters into
**tokens** — the atomic symbols the productions are written in terms of. This page describes how you
specify tokens and how the token manager decides what to match.

Lexical rules live in a `.lex` file (or after the productions in a `.jj`). A rule associates a
**kind** with one or more **regular expressions**.

## The four token kinds

```
TOKEN =          /* produces a token the parser can see            */
SPECIAL_TOKEN =  /* produces a token the parser ignores but can inspect */
SKIP =           /* matches and discards; nothing reaches the parser    */
MORE =           /* matches and holds; continues into the next match     */
```

- **`TOKEN`** — the normal case. Each match becomes a token with a name and an `image` (the matched
  text), and it flows to the parser.
- **`SKIP`** — the match is thrown away. Use it for insignificant whitespace and separators.
- **`SPECIAL_TOKEN`** — the match is a real token but is *not* handed to the parser. It stays reachable
  as a side chain (see [Special tokens](#special-tokens)); comments are the classic use.
- **`MORE`** — the match is not a token yet. Its characters are remembered and prepended to whatever
  is matched next, until a `TOKEN` or `SPECIAL_TOKEN` finally completes. Use it to build a token up in
  stages (see [Lexical states](#lexical-states)).

## Regular expressions

A token's pattern is a regular expression built from:

| Construct | Meaning |
| --------- | ------- |
| `"abc"` | a string literal |
| `["a"-"z", "A"-"Z"]` | a character class (list and ranges) |
| `~["\n", "\r"]` | any character *except* those listed |
| `~[]` | any character at all |
| `( … )` | grouping |
| `( … )*` `( … )+` `( … )?` | zero-or-more, one-or-more, optional |
| `a \| b` | alternation |
| `<NAME>` | reference to another named expression |

Named expressions can be **private** by prefixing the name with `#`. A private expression is only a
building block — it never becomes a token on its own, and it may not carry an action or a state
change:

```
TOKEN =
  < INTEGER: (<DIGIT>)+ >
| < #DIGIT: ["0"-"9"] >
;
```

Here `INTEGER` is a token; `DIGIT` is not — it merely factors out the digit class.

## How a match is chosen

At each position the token manager applies two rules, in order:

1. **Longest match wins.** Among all patterns that match at the current position, the one consuming
   the most characters is chosen. This is why `>=` beats `>` and why keywords must be handled with
   care (see [Lexer Tips](lexer-tips.md)).
2. **Earlier declaration breaks ties.** If two patterns match the *same* longest length, the one
   declared first wins. So list a keyword *before* the general identifier token if they can overlap.

If nothing matches, the token manager raises a lexical error (see [Error Handling](error-handling.md)).

## Case-insensitive matching

Add `[IGNORE_CASE]` after the kind to make a whole block case-insensitive. Note that `IGNORE_CASE` is
a reserved word and therefore **cannot** be used as a key inside `options { … }` — the block modifier
is the way to express it:

```
TOKEN [IGNORE_CASE] =
  < IF:   "if" >
| < ELSE: "else" >
;
```

Apply case-insensitivity consistently within a lexical state; mixing sensitive and insensitive
expressions in one state costs performance ([Lexer Tips](lexer-tips.md)).

## Lexical states

The token manager is a state machine. Every rule belongs to one or more **lexical states**; the
default state is `DEFAULT`. A state prefix in angle brackets restricts a rule to certain states, and
`<*>` applies it to *all* states:

```
<STATE_NAME> ...      /* only in STATE_NAME              */
<S1, S2> ...          /* in S1 and S2                    */
<*> ...               /* in every state                  */
```

A rule can **switch** the manager into another state after matching, by writing `: STATE` after the
expression. Combined with `MORE`, this is how multi-part constructs like comments are recognised
without a single monster regular expression.

### Example: comments

The following mirrors how HiveVM CC's own grammar lexes comments. A `//` or `/*` starts accumulating
with `MORE` and switches state; inside the comment state everything is consumed until the terminator
completes a `SPECIAL_TOKEN` and returns to `DEFAULT`:

```
MORE =
  "//" : IN_LINE_COMMENT
| "/*" : IN_BLOCK_COMMENT
;

SPECIAL_TOKEN <IN_LINE_COMMENT>  = < LINE_COMMENT:  "\n" | "\r" | "\r\n" > : DEFAULT ;
SPECIAL_TOKEN <IN_BLOCK_COMMENT> = < BLOCK_COMMENT: "*/" > : DEFAULT ;

MORE <IN_LINE_COMMENT, IN_BLOCK_COMMENT> = < ~[] > ;
```

Read it as: begin a comment (`MORE`, switch state), swallow any character while inside
(`MORE < ~[] >`), and finish on the terminator, emitting a `SPECIAL_TOKEN` and switching back to
`DEFAULT`. Because the comment token is a `SPECIAL_TOKEN`, the parser never sees it, but tools can
still retrieve it.

## Special tokens

A `SPECIAL_TOKEN` is fully tokenised — it has a name and an `image` — but it is not part of the
grammar the parser matches. Instead, each ordinary token keeps a link to the run of special tokens
that immediately preceded it, so a tool (a formatter, a documentation generator) can walk them.
Comments are the canonical example: ignored by parsing, available for processing.

## Lexical actions

A token expression may be followed by a Java action block that runs when the match completes — for
example to normalise the `image` or maintain a counter. Actions are **not** permitted on private
(`#`) expressions, and — as a rule — avoid attaching them to `SKIP` and `MORE`; attach them to the
final `TOKEN`/`SPECIAL_TOKEN` instead ([Lexer Tips](lexer-tips.md) explains why).

## Checklist

- Pick the right kind: visible symbol → `TOKEN`; noise → `SKIP`; comment-like → `SPECIAL_TOKEN`;
  staged build-up → `MORE`.
- Remember longest-match, then declaration order.
- Reach for **lexical states** rather than one enormous regular expression.
- Keep case-insensitivity uniform per state.

Continue with [Lookahead](lookahead.md) to see how the parser uses these tokens to make decisions.
</content>
