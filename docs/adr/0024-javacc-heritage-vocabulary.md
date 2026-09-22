# ADR-0024: Rename the JavaCC-era names that stay inside the generator; keep the ones it emits

- **Status:** 🟢 Accepted <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-21
- **Deciders:** HiveVM Waggle maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

[ADR-0014](0014-rename-project-to-waggle.md) renamed the product and every surface carrying the "CC"
brand, and put the **JavaCC heritage vocabulary** explicitly out of scope: the `JJ*`/`jj*`
identifiers, the `JJPARSER_*`/`JJTREE_*` option keys, the C++ typedefs, and classes such as
`JavaCCData` and `AbstractJavaCCParser`. Its reasoning was that these are "several hundred
occurrences, most of them emitted into user code", with "no user-visible benefit and a large diff
that would hide the actual rename", and it recorded that "a later ADR may revisit them". This is
that ADR.

Two of the three reasons have expired, and the third was never uniform. ADR-0014's rename is
implemented, so there is no longer a rename for this diff to hide. And "most of them emitted into
user code" is true of the *identifiers*, not of the *types*: the heritage names fall into two groups
that ADR-0014 counted together.

**Group A — names that leave the generator.** `jj_consume_token`, `jjmatchedKind`, `jjstateSet` and
the rest of the `jj*` family are written into every generated lexer and parser; `JJChar` and
`JJString` are typedefs in the C++ runtime header that user code includes. Renaming these churns
every generated file in every downstream project and breaks C++ users who named the types.

**Group B — names that never leave it.** `JavaCCData`, `JavaCCParserDefault` and the
`JJPARSER_*`/`JJTREE_*` constants of `org.hivevm.waggle.api.Waggle` appear only in this repository's
own Java sources: 141 references to the constants, six files for `JavaCCData`. Critically, the
constants' **values are already the real option names** —

~~~java
String JJPARSER_LOOKAHEAD = "LOOKAHEAD";
String JJTREE_MULTI       = "NODE_MULTI";
~~~

— so a grammar author writing `options { LOOKAHEAD: 2 }` never sees `JJPARSER_`. The prefix survives
only as the Java identifier that holds the string.

`AbstractJavaCCParser` and the start production `javacc_input` sit between the two groups and belong
to B on inspection. They do appear in `src/main/generated/.../Parser.java` — but only because
*this project's own grammars* choose them: `Waggle.waggle` and `JJTree.waggle` declare
`BASE_PARSER: "AbstractJavaCCParser"` and name their start production `javacc_input`. A user's
grammar names its own base parser and its own productions; nothing in the generator imposes either.
Renaming them regenerates this repository's bootstrap parser and reaches no one else.

This matters because AGENTS.md §3.5 requires the project vocabulary from the
[specification](../SPECIFICATION.md) to be used consistently in code, and the specification's
vocabulary for these concepts is "grammar", "production", "lexer", "parser" — not "JavaCC". The
exception is **JJDoc**, which the specification itself names as the grammar-documentation tool
(§4, §5); it is project vocabulary, not clutter.

## Decision

We will rename the heritage names of group B and keep group A:

1. **Types.** `JavaCCData` → `GrammarData`, `JavaCCParserDefault` → `GrammarParser`,
   `AbstractJavaCCParser` → `AbstractGrammarParser`.
2. **Option constants.** The `JJPARSER_*` and `JJTREE_*` prefixes in
   `org.hivevm.waggle.api.Waggle` are dropped: `JJPARSER_LOOKAHEAD` → `LOOKAHEAD`,
   `JJTREE_MULTI` → `NODE_MULTI`, and so on, so the constant is named after the option it holds.
   **The string values do not change**, so no grammar and no build file changes.
3. **The self-hosted grammars** rename their start production `javacc_input` → `grammar_input` and
   follow decision 1 in their `BASE_PARSER` option. The bootstrap parser under
   `src/main/generated/` is rebuilt on the next build — `.gitignore` excludes that directory, so
   nothing is committed there — and the change is confined to this repository (ADR-0009).
4. **Group A is kept, deliberately.** The `jj*`/`JJ*` identifiers in generated code, `JJChar` and
   `JJString`, and the *values* of the option keys stay as they are. They name the lineage
   ([ADR-0003](0003-fork-javacc-as-baseline.md)) at the one place where renaming would be a
   breaking change for every downstream project, for no benefit to this code base.
5. **`JJDoc` keeps its name** in the tool, its package and its CLI: the specification uses it as
   project vocabulary.
6. **Generated output does not change**, apart from this repository's own bootstrap parser under
   decision 3, which changes only where its grammar named the renamed things.

## Consequences

- The generator's own sources stop reading as a JavaCC fork in the places where they are not one.
  `GrammarData` says what the class is; `JavaCCData` said where it came from.
- `Waggle`'s constants are a **source-breaking change for embedders** who reference them by name.
  This is a public interface (SPECIFICATION §3 names `ParserBuilder` as the supported entry point,
  and `Waggle` is beside it), so it belongs in release notes. No grammar, build file or generated
  artifact is affected, because the values are untouched.
- The lineage stays legible where it should be: a reader of generated code still sees `jj_*` and can
  connect it to JavaCC, which ADR-0003 wants.
- The diff is wide (149 constant references) but mechanical and compile-checked; nothing here is a
  behaviour change, so the byte-for-byte comparison of generated output is available as the check —
  except for the renamed start production of decision 3, which reaches the generated parser of any
  grammar that names it, and must be reviewed as a rename and nothing else.
- The split between "emitted" and "internal" becomes a rule this project can apply again, instead of
  a judgement re-made per name.
- The package `org.hivevm.waggle.jjtree` **keeps its name**, decided 2026-09-21. By the rule above
  it is group B and could be renamed, but the maintainer chose to leave it: its role is stated where
  a reader meets it — `package-info.java`, [ADR-0016](0016-tree-building-as-an-optional-module.md)
  decision 5, [ADR-0019](0019-package-layout-and-dependency-dag.md)'s package table and the README —
  and a rename would move `JAVA_PACKAGE` in `JJTree.waggle`, the generated sources under it, two
  tests and `PackageDagTest` for a name nothing depends on. It is an heirloom like `jj_consume_token`,
  and it is documented rather than hidden.

## Alternatives considered

- **Rename everything, including `jj*` in generated code.** This is what ADR-0014 rejected, and the
  reason still holds for group A: several hundred occurrences emitted into user code, breaking every
  downstream project and the C++ runtime header, with no benefit to this repository.
- **Rename nothing, keep ADR-0014's decision.** Rejected: two of its three reasons were about the
  timing of the rename, which has passed, and the third does not apply to names that never leave the
  generator. AGENTS.md §3.5 asks for the specification's vocabulary in the code.
- **Keep the constant prefixes, rename only the types.** Rejected: the prefixes are the larger and
  more misleading half — `JJPARSER_LOOKAHEAD` suggests the option is called `JJPARSER_LOOKAHEAD`,
  which is exactly the confusion the specification's vocabulary rule exists to prevent.
- **Change the option values too**, so grammars write `WAGGLE_LOOKAHEAD`. Rejected: the values are
  the grammar surface that users type; changing them breaks every existing grammar, which is a far
  larger decision than this one and is not needed to fix the naming inside the generator.
