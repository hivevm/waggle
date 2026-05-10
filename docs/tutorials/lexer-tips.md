# Lexer Tips

A grammar spends most of its runtime in the [token manager](token-manager.md). A few habits keep it
fast, small, and unambiguous. These tips are adapted to HiveVM CC's lexical syntax (`.lex` files with
`TOKEN` / `SKIP` / `SPECIAL_TOKEN` / `MORE`, lexical states, and `[IGNORE_CASE]`).

## Regular expressions

**1. Prefer string literals.** Fixed strings compile to a compact deterministic automaton (DFA);
open-ended regular expressions compile to a slower non-deterministic one (NFA). Spell out the literals
you can:

```
TOKEN =
  < IF: "if" >
| < WHILE: "while" >
;
```

**2. Don't fold unrelated literals into one token.** Rather than one token that matches several
distinct spellings, give each its own token and choose between them in a production. Instead of

```
TOKEN = < NONE: "\"none\"" | "'none'" > ;
```

define two tokens and let the grammar pick — it keeps the lexer simple and the choice visible where it
belongs.

**3. Order alternatives short-to-long.** Within a token's alternation, listing shorter literals before
longer ones helps the generator build tighter matching tables. (Recall that *matching* still obeys
longest-match, then declaration order — this tip is about generation efficiency, not match choice.)

## Lexical states

**4. Use as few states as you can.** Each state multiplies the lexer's tables. Keep the elaborate
patterns in one state and let the others hold only what they must.

**5. Skip generously.** Anything the parser does not need — whitespace, most separators — belongs in
`SKIP`. It never becomes a token, so it cannot cause an unexpected-token error and keeps the token
stream lean.

**6. Don't put actions or state changes on `SKIP`.** Simple skips (especially single characters)
compile to tight loops that cannot run an action or switch state. If you need an action or a
`: STATE` transition, use a `TOKEN`/`SPECIAL_TOKEN`/`MORE` instead.

**7. Reach for `MORE` sparingly.** `MORE` is the right tool for staged matches (comments, strings via
states), but each `MORE` step carries the accumulated image forward. Where you would attach an action,
attach it to the **final** `TOKEN` that completes the match, not to the intermediate `MORE` steps.

## Other considerations

**8. Prefer `< ~[] >` over `(~[])+` when a state allows it.** Inside a dedicated state — for example
the body of a comment — matching one arbitrary character at a time (`MORE ... = < ~[] > ;`) lets the
terminator win by longest-match cleanly, and avoids a greedy "any-run" that can swallow the
terminator. This is exactly the shape used for comment bodies in [The Token Manager](token-manager.md).

**9. Keep `IGNORE_CASE` uniform per state.** Apply case-insensitivity consistently — ideally once in
a whole block (`TOKEN [IGNORE_CASE] = …`); `IGNORE_CASE` is a reserved word and cannot be an
`options { … }` key.
Mixing case-sensitive and case-insensitive expressions within one lexical state forces slower matching.

## Keywords vs. identifiers

A classic trap: a keyword like `if` also matches a general `IDENTIFIER`. Two patterns matching the
same text are resolved by **declaration order**, so declare the keywords **before** the identifier
token:

```
TOKEN =
  < IF:   "if" >
| < ELSE: "else" >
;

TOKEN =
  < IDENTIFIER: <LETTER> (<LETTER> | <DIGIT>)* >
| < #LETTER: ["a"-"z", "A"-"Z", "_"] >
| < #DIGIT:  ["0"-"9"] >
;
```

Because `if` and `identifier` both match the text `if` at the same length, the earlier declaration
(`IF`) wins — exactly what you want. If instead you want `iffy` to remain an identifier, longest-match
already handles it: `IDENTIFIER` matches all four characters and beats the two-character `IF`.

Return to [The Token Manager](token-manager.md) for the underlying rules, or the
[tutorial index](README.md).
</content>
