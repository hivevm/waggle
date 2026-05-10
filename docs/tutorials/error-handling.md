# Error Handling

Two things can go wrong while reading input: the **token manager** can hit characters that form no
valid token, and the **parser** can hit a token that does not fit the grammar. HiveVM CC surfaces
each as its own type, and lets you recover from parser errors so one mistake need not abort the whole
parse. (Class names below are for the **Java** target; other targets provide equivalents.)

## The two failure types

- **`TokenException`** — raised by the token manager when the input cannot be tokenised. In principle
  a complete lexical specification prevents it, so it is treated as a low-level failure rather than
  something you routinely catch.
- **`ParseException`** — raised by the parser when the current token cannot continue any active
  production. It is an ordinary checked exception and is the one you handle and recover from.

A production may also declare that it throws your own exceptions, using normal Java `throws` syntax on
the rule, so application errors raised inside `<? … ?>` actions propagate cleanly.

## What a parse error reports

When the parser is stuck it builds a `ParseException` describing what it *expected*. Internally it
calls `generateParseException()`, which gathers every token that could have legally appeared at the
failure point, since the last successfully matched token. The default message lists the token that was
found and the ones that were expected — enough to point a user at the problem.

To change the wording (localise it, simplify it, add context), customise the message on the generated
`ParseException` — typically by adjusting its `getMessage()` in the generated source or by catching the
exception at the top level and reformatting it for your users.

## Recovering from errors

Sometimes you want to report an error and **keep going** — for instance, to collect several errors in
one pass instead of stopping at the first. The idea is always the same: catch the `ParseException`,
then advance the token stream to a **synchronisation token** (a point where parsing can safely resume,
such as a statement-ending `;`), and continue.

> HiveVM CC has **no `JAVACODE` productions**. Where JavaCC would use a `JAVACODE` routine for
> recovery, write an ordinary helper method in the `BASE_PARSER` class and call it from an action, or
> put the `try/catch` directly in an action.

### Skipping to a synchronisation token

Give the base parser a helper that consumes tokens until it passes a synchronising token:

```java
// in the BASE_PARSER class
protected void skipTo(int kind) {
    Token t;
    do {
        t = getNextToken();          // consume one token
    } while (t.kind != kind && t.kind != EOF);
}
```

### Deep recovery: `try/catch` around a rule body

Wrap the risky part of a production in a Java `try/catch` written inside actions. If a statement fails
to parse, report it and skip to the next `;`, then let the surrounding `(… )*` continue with the next
statement:

```
StatementList =
  ( Statement() )*
  <EOF>
;
Statement =
<?
  try {
?>
    Assignment() < SEMICOLON >
<?
  } catch (ParseException e) {
      System.err.println(e.getMessage());
      skipTo(SEMICOLON);            // resynchronise
  }
?>
;
```

Here the error is contained inside `Statement`: after `skipTo(SEMICOLON)` the loop in `StatementList`
resumes cleanly at the next statement, so a single bad statement does not sink the whole parse.

### Shallow recovery: no alternative matched

When the failure is a *choice* with no matching alternative, the same tactic applies one level up:
catch the `ParseException` at the point of the choice and skip forward to the next safe token before
continuing.

## Practical advice

- **Choose synchronisation points deliberately.** Good synchronisers are unambiguous structural
  tokens: statement terminators, closing braces, `EOF`.
- **Do not over-recover.** Too many recovery points can turn a small mistake into a cascade of
  misleading follow-on errors. Recover at a few well-chosen boundaries.
- **Keep `TokenException` rare.** If the lexer throws, the fix usually belongs in the token
  definitions ([The Token Manager](token-manager.md)), not in a `catch`.
- **Report before you skip.** Emit the message while you still have the failing token's location, then
  resynchronise.

Next: [Lexer Tips](lexer-tips.md) for writing token definitions that stay fast and unambiguous.
</content>
