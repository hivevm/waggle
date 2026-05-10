# HiveVM CC Tutorials

A hands-on guide to writing grammars for **HiveVM CC**. These tutorials follow the structure of the
classic JavaCC tutorials, but every grammar example is written in **HiveVM CC's own syntax**: the
`grammar Name;` header with an `options { … }` block, `name = … ;` productions, `<? … ?>` actions, and
tree building via `#Node` annotations in the same grammar — see
[ADR-0010](../adr/0010-unified-grammar-and-actual-surface-syntax.md). For the conceptual model and
vocabulary, see the [specification](../SPECIFICATION.md).

> These pages are an original rewrite of the JavaCC tutorial material adapted to this project; they
> are not a verbatim copy. Where HiveVM CC deliberately differs from JavaCC, the difference is called
> out.

## Contents

1. [Examples](examples.md) — a complete grammar from the ground up, in five small steps.
2. [The Token Manager](token-manager.md) — lexical analysis: token kinds, regular expressions,
   lexical states, and actions.
3. [Lookahead](lookahead.md) — how the parser resolves choices, and how to guide it with
   `LOOKAHEAD`.
4. [The Character Input](charstream.md) — feeding characters to the parser via `Provider`s, encodings
   and Unicode.
5. [Error Handling](error-handling.md) — `ParseException`, `TokenException`, and error recovery.
6. [Lexer Tips](lexer-tips.md) — practical advice for fast, correct token definitions.

## How HiveVM CC differs from JavaCC at a glance

| JavaCC | HiveVM CC |
| ------ | --------- |
| `options { … }` before the parser class | `grammar Name;` then an `options { KEY: value, … }` block |
| `PARSER_BEGIN(N) … PARSER_END(N)` with an embedded class | no embedded class; shared code lives in the class named by `BASE_PARSER` |
| `void Name() : {} { … }` | `Name = … ;` |
| `{ java action }` in productions | `<? java action ?>`; the empty block `{}` is an empty expansion |
| JJTree: a separate `.jjt` pre-processing pass producing an intermediate `.jj` | `#Node` annotations in the *same* grammar; parser and tree classes are generated in **one** step |
| `JAVACODE` productions | not supported — use a normal production with actions |
| run the `javacc` CLI | apply the `org.hivevm.cc` Gradle plugin (`generateParser`) |
| Java output only | Java, C++, or Rust (`target` / `CODE_GENERATOR`) |

A JavaCC grammar therefore does **not** run under HiveVM CC — it has to be migrated.

## The shape of a grammar

```text
grammar Example;          // declares the grammar name

options {                 // optional; comma-separated KEY: value pairs
  JAVA_PACKAGE: "org.example"
}

Input =                   // productions
  MatchedBraces() <EOF>
;

TOKEN =                   // token definitions (or in a sibling Example.lex)
  < LBRACE: "{" >
;
```

Note that `LOOKAHEAD` and `IGNORE_CASE` are reserved words and therefore cannot be used as keys inside
`options { … }`; use the `LOOKAHEAD(…)` construct in a production and the `[IGNORE_CASE]` modifier on a
token block instead.

## Running the generator

Every example is generated with the Gradle plugin. Point a `task` at the grammar file and run
`./gradlew generateParser` (see the **Configure Settings** section of the [README](../../README.md)):

~~~
parserProject {
  target = 'java'
  output = 'src/main/generated'

  task {
    name = 'example'
    file = 'src/main/resources/Example.jj'
  }
}
~~~

The generator reads `Example.jj` (and, if present, the sibling `Example.lex`) and writes the parser,
the token manager, and — if the grammar uses `#Node` — the tree-node classes into the output
directory. There is no second tool and no intermediate grammar to keep in sync.
