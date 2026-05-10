# Lookahead

HiveVM CC generates **top-down, recursive-descent** parsers. At every point where the grammar offers
more than one way forward, the parser must decide which way to go *without* backtracking. It does so
by looking a little way ahead in the token stream. This page explains where those decisions happen,
how the default works, and how to guide it with `LOOKAHEAD`.

## Choice points

Four constructs force a decision:

| Construct | The decision |
| --------- | ------------ |
| `( a \| b \| c )` | which alternative to take |
| `( a )?` and `[ a ]` | whether to match `a` at all |
| `( a )*` | whether to go round again or stop |
| `( a )+` | after the first, whether to go round again or stop |

## The default: `LL(1)`

By default the parser looks at exactly **one** token — it is an `LL(1)` parser. For each choice it
computes the set of tokens that can legally begin each alternative and picks the alternative whose set
contains the current token.

This works whenever the alternatives start with distinct tokens. When two alternatives can begin with
the *same* token, one token is not enough to tell them apart, and the generator prints a **choice
conflict warning** at build time. The parser still runs, but it resolves the conflict by taking the
**first** matching alternative — which may not be what you meant. A warning is a signal to fix the
grammar, not to ignore.

## Fix it by left-factoring first

Often the cleanest fix is to remove the ambiguity rather than paper over it. If two alternatives share
a common prefix, factor the prefix out so the choice becomes `LL(1)`:

```
/* Ambiguous: both alternatives start with < ID >           */
Ref =
  ( < ID > < LPAREN > Args() < RPAREN >
  | < ID > )
;
/* Left-factored: decide AFTER the shared < ID >             */
Ref =
  < ID > [ < LPAREN > Args() < RPAREN > ]
;
```

Prefer this whenever it is natural; it is faster and clearer than an explicit lookahead hint.

## Guiding the parser with `LOOKAHEAD`

When refactoring is not practical, place a `LOOKAHEAD( … )` hint at the start of the alternative (or
of the `?`/`*`/`+` body) to tell the parser how to decide. HiveVM CC supports the same forms as
JavaCC.

### Numeric lookahead

`LOOKAHEAD(n)` lets the parser inspect up to `n` tokens for this choice:

```
Statement =
  ( LOOKAHEAD(2) LabeledStatement()
  | ExpressionStatement()
  )
;
```

Use the smallest `n` that resolves the conflict. A large `n` costs runtime and can hide a deeper
grammar problem.

### Syntactic lookahead

`LOOKAHEAD(expansion)` is more precise: the parser tentatively tries to match `expansion`, and takes
the alternative only if it succeeds. This decides on a *pattern* rather than a fixed token count:

```
Unit =
  ( LOOKAHEAD( Name() < ASSIGN > ) Assignment()
  | Expression()
  )
;
```

Take the assignment branch only if the upcoming tokens look like `Name =`.

### Semantic lookahead

`LOOKAHEAD({ booleanExpression })` decides with your own Java condition. The alternative is taken only
if the expression evaluates to `true`. The condition is written in a `{ … }` block (unlike ordinary
actions, which use `<? … ?>`):

```
Item =
  ( LOOKAHEAD({ inHeader() }) HeaderItem()
  | BodyItem()
  )
;
```

### Combining them

The general form takes an optional amount, an optional syntactic expansion, and an optional semantic
condition:

```
LOOKAHEAD( amount, expansion, { booleanExpression } )
```

A common idiom is `LOOKAHEAD(0, { condition })` — zero syntactic tokens, decision made purely by the
semantic condition. Syntactic and semantic lookahead can be combined so the branch is taken only when
the pattern matches *and* the condition holds.

## A global default

The generator has a grammar-wide `LOOKAHEAD` option (the default is `1`, i.e. `LL(1)`), but you
**cannot set it from the grammar**: `LOOKAHEAD` is a reserved word, so it is not accepted as a key
inside `options { … }`, and the Gradle plugin does not expose it either.

That is no great loss. A grammar-wide lookahead greater than 1 is a blunt instrument: it slows the
parser everywhere and masks the conflicts you would rather see and fix. Use local hints at the
specific choice points that need them — which is the only option here anyway.

## Cost and good practice

- Every non-trivial lookahead is extra work at parse time; syntactic lookahead can, in the worst case,
  scan far ahead. Keep hints local and minimal.
- A conflict warning means *one* token was not enough. First try to left-factor; only then add a hint.
- Reach for semantic lookahead when the decision genuinely depends on data, not just on the token
  shape.
- A `LOOKAHEAD` hint at a spot that is **not** a choice point has no syntactic effect (only its
  semantic part, if any, is considered) and the generator will warn you.

Next: [Error Handling](error-handling.md) — what happens when the input does not fit the grammar.
</content>
