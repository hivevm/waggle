# Architecture

Where things are and how a grammar becomes a parser — the **current** state, in one page.

This is a reading path, not a decision. Every "why" below links to the ADR that decided it; the
[ADR log](adr/README.md) is the record, the [specification](SPECIFICATION.md) is the constitution,
and where this page and one of those disagree, they win and this page is wrong.

## The pipeline

One grammar file in, generated source out, in five stages. Stages 1–4 know nothing about the target
language; only stage 5 does ([SPECIFICATION §4](SPECIFICATION.md)).

| # | Stage | Package | What it produces |
| --- | --- | --- | --- |
| 1 | Read | `waggle.api` | the grammar text — one `.waggle` file, tokens included ([ADR-0025](adr/0025-one-grammar-file.md)) |
| 2 | Parse | `waggle.grammar` | the model: productions, expansions, regular expressions, node scopes |
| 3 | Analyse | `waggle.analysis` | lookahead plans, ambiguity and sanity diagnostics |
| 4 | Build the lexer | `waggle.lexer` | the NFA and the DFA, finished ([ADR-0012](adr/0012-lexer-owns-dfa-construction.md)) |
| 5 | Generate | `waggle.codegen` | parser, token manager, tree classes, in Java, C++ or Rust |

The model (`waggle.model`, 34 types) is what stages 2–5 pass around. It is language-independent and
emits no target code itself.

## The packages

~~~
org.hivevm.core       Environment, Version            — depends on nothing
org.hivevm.source     the template engine             — depends on core only
org.hivevm.waggle
  api                 ParserBuilder, WaggleCompiler, Options, Diagnostics context
  grammar             the generated parser of the grammar language, plus its base class
  model               productions, expansions, regular expressions
  diag                Diagnostic, Diagnostics, DiagnosticSink
  analysis            semantic checks, lookahead planning
  lexer               NFA, DFA, lexical states
  tree                the tree model and the TreeEmitter SPI
  codegen             the back ends: java/, cpp/, rust/
  doc                 JJDoc, the grammar-documentation tool
  jjtree              the tested reference consumer of tree building
~~~

The dependency graph is a DAG and `PackageDagTest` is its normative statement — every package is
listed, a key covers its sub-packages, and two carve-outs are named
([ADR-0026](adr/0026-one-dependency-graph.md), consolidating 0013, 0019 and 0023).

`org.hivevm.source` is a leaf: a small template engine written here rather than depended on
([ADR-0005](adr/0005-custom-template-engine.md)). It names nothing of Waggle; what it needs from a
caller it declares itself as `RenderContext`.

## The back ends

A target contributes a `Generator`, found through a `ServiceLoader`
([ADR-0004](adr/0004-multi-target-code-generation.md)). What a back end writes is split from how its
language spells it:

- **the algorithm** lives once, in emitters — `StringLiteralDfaEmitter`, `NfaMoveEmitter`,
  `GetNextTokenEmitter` for the lexer, `Phase3Emitter` and `LookaheadEmitter` for the parser;
- **the spelling** lives in one object per language — `TargetSyntax` for the lexer
  ([ADR-0017](adr/0017-lexer-emission-by-composition.md)), `ParserSyntax` for the parser
  ([ADR-0021](adr/0021-parser-emission-by-composition.md)). Java's answers are the defaults.

Whole files come from templates under `src/main/resources/templates/<target>/`. Rendering produces
text; an `OutputSink` decides where it goes, which is what lets a caller generate into memory
([ADR-0018](adr/0018-rendering-is-pure-an-output-sink-writes.md)).

Tree building is optional: a back end supplies a `TreeEmitter` or refuses grammars that use `#Node`
([ADR-0016](adr/0016-tree-building-as-an-optional-module.md)). Targets are not at parity — the gaps
are listed under **Known limitations** in the [README](../README.md).

## Failure and diagnostics

Two channels, deliberately distinct:

- **A diagnostic** describes the grammar. `Diagnostics` is a value owned by one generation, not a
  static counter, and a `DiagnosticSink` decides whether the user sees it as it happens
  ([ADR-0015](adr/0015-diagnostics-and-generation-request.md)).
- **An exception** ends the run. `GenerationException`, never `System.exit`
  ([ADR-0011](adr/0011-error-handling-contract.md),
  [ADR-0022](adr/0022-generation-failure-type.md)). `ParseException` means only one thing: the
  grammar did not parse.

## Entry points

| Entry point | For |
| --- | --- |
| `org.hivevm.gradle.WagglePlugin` — `parserProject { task { … } }` | builds ([ADR-0006](adr/0006-gradle-plugin-interface.md)) |
| `org.hivevm.waggle.api.ParserBuilder` | embedding the generator |
| `org.hivevm.waggle.api.ParserInterpreter` | running a grammar without generating code ([ADR-0020](adr/0020-interpreted-mode.md)) |
| `org.hivevm.waggle.doc.JJDocMain` | grammar documentation |

The build generates its own parser with a *published* release of its own plugin
([ADR-0009](adr/0009-self-hosting-bootstrap.md)), so the grammar of the grammar language is itself
the largest test.

## What the tests assert about the architecture

| Test | Keeps |
| --- | --- |
| `PackageDagTest` | the dependency graph, and that every package is in it |
| `SourceHygieneTest` | no unused and no own-package imports under `src/main/java` |
| `GeneratorSizeTest` | no class under `codegen` above 1000 lines — `LexerGenerator` was 3064 |
| `MultiTargetGenerationTest` | every target generating, and its debug output referring to things that exist |
| `JJTreeAstTest` | the tree features, end to end, against a golden file |
