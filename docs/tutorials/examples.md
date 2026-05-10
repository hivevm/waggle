# Examples

This tutorial builds a complete HiveVM CC grammar in small steps, each adding one idea. The running
example is the classic *matched braces* language: strings such as `{}` and `{{{}}}` are legal; `{`,
`}{`, and `{{}` are not.

If you have not seen the syntax before, skim the [README](README.md) difference table first. Every
grammar here uses HiveVM CC's `grammar Name;` header, `name = … ;` productions, and `<? … ?>` actions.

## Anatomy of a grammar

A HiveVM CC grammar is **one** file:

- a **grammar file** (`.jj`) — the `grammar Name;` line, an optional `options { … }` block, the
  productions, and (usually) the `TOKEN` / `SKIP` / `SPECIAL_TOKEN` / `MORE` definitions;
- optionally, the token definitions can be moved to a sibling **lexical file** (`.lex`) of the same
  base name, which the generator appends to the grammar during a build.

`grammar Example;` declares the grammar's name. For the Java target the generated classes are always
`Parser`, `Lexer` and `ParserConstants`, placed in the package given by `JAVA_PACKAGE` — the grammar
name does *not* become the class name. Shared helper code (a `main`, fields, methods called from
actions) lives in the class named by `BASE_PARSER`, which the generated parser extends; nothing is
embedded in the grammar itself.

## Step 1 — structure only

Two productions describe the language. `Input` is the start rule; `MatchedBraces` is recursive, with
the inner pair made optional by `[ … ]`.

```text
grammar Example;

options {
  JAVA_PACKAGE: "org.example"
}

Input =
  MatchedBraces() <EOF>
;

MatchedBraces =
  < LBRACE > [ MatchedBraces() ] < RBRACE >
;
```

At this point the grammar refers to two tokens, `LBRACE` and `RBRACE`, but has not defined them yet.
`<EOF>` is the built-in end-of-input token.

## Step 2 — define the tokens and skip whitespace

`SKIP` throws its matches away *between* tokens, so spaces and newlines never reach the parser.
`TOKEN` names the two brace characters. Append this to the grammar (or put it in `Example.lex`):

```
SKIP =
  " "
| "\t"
| "\n"
| "\r"
;

TOKEN =
  < LBRACE: "{" >
| < RBRACE: "}" >
;
```

Now `{ }` and

```
{
  {}
}
```

parse, because the whitespace is skipped. Note that `SKIP` only discards whitespace *between*
tokens; whitespace *inside* a token definition is significant (see [SKIP vs TOKEN](#a-note-on-skip-vs-token)
below).

## Step 3 — actions and return values

A production can carry a return type (`: Type` before the `=`) and run host-language code in
`<? … ?>` actions. Here `MatchedBraces` returns the nesting depth and `Input` prints it.

```ebnf
grammar Example;

options {
  JAVA_PACKAGE: "org.example",
  BASE_PARSER: "ExampleBase"
}

Input =
<?
	int depth;
?>
  depth=MatchedBraces() <EOF>
  <? System.out.println("depth = " + depth); ?>
;

MatchedBraces() : int =
<?
	int inner = 0;
?>
  < LBRACE > [ inner=MatchedBraces() ] < RBRACE >
<?
	return inner + 1;
?>
;

SKIP = " " | "\t" | "\n" | "\r" ;

TOKEN =
  < LBRACE: "{" >
| < RBRACE: "}" >
;
```

A few things to notice:

- A declaration action at the top of a production introduces local variables the rest of the
  production can use.
- `depth=MatchedBraces()` captures a production's return value; `t=< LBRACE >` captures a matched
  token.
- A production with a return type must `return` a value in a **trailing** action, after the expansion.
  Do **not** put a `return` inside one alternative of a choice — the generator appends a `break` after
  each alternative, so the result would not compile. Assign to a local in each alternative and return
  once at the end (see `Factor` below).

## Step 4 — a driver

The generated parser reads characters from a `Provider` (see
[The Character Input](charstream.md)). Put a `main` in the base class, or in a separate driver, and
hand the parser a provider:

```java
package org.example;

public final class Main {
  public static void main(String[] args) throws Exception {
    Parser parser = new Parser(new StreamProvider(System.in));
    parser.Input();
  }
}
```

`Input()` is the start production. To parse a string instead of a stream, use
`new StringProvider("{{}}")`.

## Step 5 — richer tokens

Real grammars match more than single characters. Regular expressions use character classes
(`["a"-"z"]`), negation (`~["\""]`), repetition (`*`, `+`, `?`), alternation (`|`), and references to
other named expressions. Private expressions — prefixed with `#` — are building blocks that never
become tokens of their own.

`Calc.jj` — a small expression grammar that evaluates as it parses:

```ebnf
grammar Calc;

options {
  JAVA_PACKAGE: "org.example"
}

Expr() : int =
<?
	int a, b;
?>
  a=Term() ( < PLUS > b=Term() <? a += b; ?> )*
<?
	return a;
?>
;

Term() : int =
<?
	int a, b;
?>
  a=Factor() ( < TIMES > b=Factor() <? a *= b; ?> )*
<?
	return a;
?>
;

Factor() : int =
<?
	int v = 0;
?>
  (
    <NUMBER> <? v = Integer.parseInt(getToken(0).image); ?>
  | < LPAREN > v=Expr() < RPAREN >
  )
<?
	return v;
?>
;

SKIP = " " | "\t" | "\n" | "\r" ;

TOKEN =
  < PLUS:   "+" >
| < TIMES:  "*" >
| < LPAREN: "(" >
| < RPAREN: ")" >
| < NUMBER: (<DIGIT>)+ >
| < #DIGIT: ["0"-"9"] >
;
```

`Term` binds tighter than `Expr`, so `2 + 3 * 4` evaluates to `14`. `getToken(0)` is the token just
matched, and its `image` field holds the text. Note how `Factor` returns *once*, at the end, rather
than from inside the choice.

## Step 6 — build a syntax tree

Evaluating during the parse is fine for a calculator, but most tools want a tree. In HiveVM CC tree
building is part of the **same grammar**: annotate a production or an expansion with `#Node`, and the
node classes and the visitor are generated together with the parser — in one step. There is no `.jjt`
file, no separate JJTree run, and no intermediate grammar (this is a deliberate departure from
JavaCC — see [ADR-0010](../adr/0010-unified-grammar-and-actual-surface-syntax.md)).

```ebnf
grammar Tree;

options {
  JAVA_PACKAGE: "org.example",
  NODE_MULTI: true,
  NODE_DEFAULT_VOID: true,
  NODE_CLASS: "ASTNode",
  VISITOR: true,
  VISITOR_DATA_TYPE: "Object"
}

Input() #Root =
  expr() <EOF>
;

expr =
  term() ( < PLUS > term() #Add(2) )*
;

term =
  <NUMBER> #Number
| < LPAREN > expr() < RPAREN >
;

SKIP = " " | "\n" ;

TOKEN =
  < PLUS: "+" >
| < LPAREN: "(" >
| < RPAREN: ")" >
| < NUMBER: (["0"-"9"])+ >
;
```

- `#Root` on a production makes a node covering the whole production.
- `#Add(2)` is a *definite* node: it takes the top **2** nodes off the stack and makes them its
  children — so `1 + 2 + 3` builds a left-leaning tree of `ASTAdd` nodes.
- `NODE_DEFAULT_VOID: true` means productions build **no** node unless annotated; without it every
  production would produce one.

Two further options appear above; both have sensible defaults, so you can leave them out:

- **`NODE_CLASS`** names a base class *you* supply, which the generated `ASTRoot`, `ASTAdd`, … extend;
  it must itself extend the generated `Node`. Omit the option and the node classes extend `Node`
  directly, which is usually what you want. A hand-written base class looks like this:
  ```java
  package org.example;

  public class ASTNode extends Node {
    public ASTNode(Parser p, int id) { super(p, id); }
  }
  ```
- **`VISITOR_DATA_TYPE`** is the type of the payload threaded through `jjtAccept`. It defaults to
  `Object`, so `VISITOR: true` on its own already yields a usable visitor.

Generating this grammar yields the parser plus `ASTRoot`, `ASTAdd`, `ASTNumber`, `Node`,
`NodeVisitor`, and `NodeDefaultVisitor` — from the single file above.

## A note on SKIP vs TOKEN

Whitespace listed in `SKIP` is ignored **only between tokens**. Inside a token it is significant.
For example, an identifier-list token manager that skips spaces still treats the space in
`< PAIR: <IDENT> " " <IDENT> >` as a required character, because it is part of the token's own
definition. Keep this distinction in mind: `SKIP` shapes the gaps between tokens, not the tokens
themselves.

## Where to go next

- Understand token matching in depth: [The Token Manager](token-manager.md).
- Resolve grammar ambiguities: [Lookahead](lookahead.md).
- Report and recover from bad input: [Error Handling](error-handling.md).
