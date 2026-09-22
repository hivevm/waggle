# ADR-0022: Generation fails with `GenerationException`, not with the generated parser's exception

- **Status:** 🟢 Accepted <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-21
- **Deciders:** HiveVM Waggle maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

[ADR-0011](0011-error-handling-contract.md) settled that a failed generation is reported by
throwing, never by exiting the JVM, and introduced `GenerationException` for it.
[ADR-0015](0015-diagnostics-and-generation-request.md) made the diagnostics a value owned by one
generation. Neither said *which* exception type the stages throw, and the answer today is an
accident of the fork.

**The pipeline's failure type is `java.text.ParseException`.** The back-end SPI declares it:

~~~java
// codegen/Generator.java
void generate(ParserRequest request) throws java.text.ParseException;
~~~

It is thrown from `GeneratorProvider` (three times) and from the analysis stage
(`Semanticize` three times, `ParserPlanner` once). `WaggleCompiler` and `JJDocMain` catch it.

Three things follow, and all three are visible in the code:

1. **The type is the JDK's text-formatting exception.** `java.text.ParseException` is the exception
   of [`Format.parseObject`, `NumberFormat.parse`, `DateFormat.parse` and
   `MessageFormat.parse`](https://developers.google.com/j2objc/javadoc/jre/reference/java/text/ParseException);
   its second constructor argument is an `errorOffset`, "the zero-based character offset into the
   string being parsed at which the error was found". Waggle uses it for things that are not a text
   format failing to parse and have no character offset, so the offset is invented:

   ~~~java
   // codegen/GeneratorProvider.java
   throw new ParseException("Tree building (#Node) is not supported for this target.", 0);
   ~~~

2. **Four of the throws carry nothing at all.** `Semanticize` and `ParserPlanner` use the
   no-argument constructor, so the failure has a `null` message and offset `-1`. The user sees only
   the wrapper `WaggleCompiler` adds; the reason the stage refused is not in the exception, only in
   the diagnostics it reported separately.

3. **The generator depends on the exception of its own product.** The generated
   `org.hivevm.waggle.grammar.ParseException extends java.text.ParseException`, which is the only
   reason `WaggleCompiler`'s single `catch (ParseException | IOException)` catches both the parse
   failures and the generator's internal refusals. The same simple name means two different types in
   this tree, and `PackageDagTest` already carves out `grammar.ParseException` for the `analysis`
   package as a deliberate exception to the layout.

The result is that a failure to *generate* and a failure to *parse the grammar* are indistinguishable
by type, and that the front end's error channel is rooted in a JDK class about dates and numbers.
`GenerationException` — the type ADR-0011 created for exactly this — is used in only seven files,
all at the outer edge.

**The state of the art keeps the two apart.** As ADR-0015 already recorded, javac reports through an
instance `Log` producing `JCDiagnostic` values, and reserves a separate internal `Abort`/`FatalError`
for "this compilation cannot continue"; Roslyn collects into a `DiagnosticBag` and throws only for
internal faults. In both, the *diagnostic* is the value that describes a bad input, and the
*exception* means the run is over. Neither reuses a library exception from an unrelated domain.

## Decision

We will make `GenerationException` the one failure type of the generation pipeline:

1. **`Generator.generate` and `GeneratorProvider` throw `GenerationException`.** `java.text.ParseException`
   disappears from the back-end SPI and from `codegen`.
2. **The analysis stage throws `GenerationException`** instead of the generated
   `grammar.ParseException`, and always with a message. The four no-argument throws become statements
   of why the stage stopped — for `Semanticize` and `ParserPlanner` that is "the grammar has errors",
   with the counts the diagnostics already hold.
3. **`GenerationException` stays unchecked**, as ADR-0011 made it. Stage signatures lose their
   `throws` clauses; the driver keeps one `catch` and adds the grammar file to the message as it
   does today.
4. **The generated parser's `ParseException` is untouched.** It belongs to the product, not to the
   generator, and it keeps extending whatever the templates say. What changes is that the generator
   no longer *uses* it as its own failure type; after this, the only place `grammar.ParseException`
   is caught by the generator is where a grammar actually fails to parse.
5. **`PackageDagTest` drops the `grammar.ParseException` carve-out for `analysis`**, which exists
   only to permit what decision 2 removes.
6. **Messages are unchanged where they exist.** ADR-0011 constrained only the delivery of a failure,
   never its text, and this ADR keeps that.

## Consequences

- "Could the grammar not be parsed, or could the parser not be generated?" becomes a question the
  type answers. Today both arrive as `ParseException`.
- A failure can no longer be thrown empty: decision 2 forces a message at the four sites that have
  none, which is the user-visible part of this change.
- Embedders that catch `java.text.ParseException` around `ParserBuilder` break. This is a source-
  and behaviour-breaking change to the documented entry point of
  [SPECIFICATION §3](../SPECIFICATION.md), and it has to be in release notes. Catching
  `GenerationException` — already thrown by the driver for every other failure — is the replacement,
  and callers that catch it already need no change.
- The stages lose their checked `throws`, so a future stage can no longer forget to declare the
  failure. The compiler also stops pointing at the sites that can fail; the diagnostics remain the
  record of what went wrong.
- `analysis` loses its exception to the package layout, so ADR-0019's graph gets one edge simpler.
- Follow-up, not part of this ADR: `WaggleCompiler` still wraps every failure into a single message.
  Whether a `GenerationException` from a stage should be rethrown unwrapped is a separate question.

## Alternatives considered

- **Keep `java.text.ParseException` and always pass a message and offset.** Rejected: it does not
  address the type meaning something else, and there is no honest offset to pass for "this target has
  no tree support".
- **Introduce a dedicated checked `GenerationFailure`.** Rejected: ADR-0011 chose an unchecked
  exception deliberately, so that a stage cannot silently swallow a failure by declaring it; adding a
  checked type reopens a decision that is already made and accepted.
- **Give the generator its own `ParseException`, distinct from the generated one.** Rejected: it
  keeps two types whose names collide in a tree that already has two `ParseException`s, and the
  distinction that matters is generation-failed versus grammar-did-not-parse, which
  `GenerationException` plus the grammar's own exception already expresses.
- **Convert every stage failure into a diagnostic and return normally.** Rejected: it contradicts
  ADR-0011, which exists because "report and carry on" is how generation used to announce success
  after failing.
