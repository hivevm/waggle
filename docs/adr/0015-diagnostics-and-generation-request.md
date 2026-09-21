# ADR-0015: Diagnostics and the generation request are values, not process-global state

- **Status:** 🟡 Proposed
- **Date:** 2026-09-20
- **Deciders:** HiveVM Waggle maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

[ADR-0011](0011-error-handling-contract.md) settled *how* a failed generation is reported: by throwing,
with **one** error channel so that "were there errors?" has a single answer. It did not say *where*
that channel lives. It lives in `org.hivevm.waggle.parser.JavaCCErrors`: a final class with three
private `static int` counters, static reporting methods that print to `System.err`, and a static
`reInit()`.

**The channel is per-JVM, not per-generation.** Seventeen hand-written classes call it — across
`parser`, `parser/jjtree`, `semantic`, `lexer`, `generator`, `generator/cpp`, `generator/rust`, `doc`
and the driver — and six more call sites sit in the grammar actions of `Waggle.waggle`, whose text is
copied verbatim into the generated parser. `Parser.parse()` opens with
`JavaCCErrors.reInit()`, so a second generation in the same JVM silently resets the first one's state
and a concurrent one corrupts both. This is not a distant scenario: `generateParser` already runs two
generation units (`tree`, `parser`) in one task, and the test suite generates dozens of times in one
JVM. Apart from `doc/JJDocGlobals`, these counters are the only mutable static state left under
`org.hivevm.waggle`.

**The build is moving toward exactly the conditions that break it.** `ParserGenerator` is a
`@CacheableTask`, and with Gradle's [configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html)
tasks execute in parallel even when `org.gradle.parallel=false`; because Gradle caches classloaders
by classpath, concurrently executing tasks share the statics loaded through them
([build-logic requirements](https://docs.gradle.org/current/userguide/configuration_cache_requirements.html)).
That is an observed failure mode, not a theoretical one —
[kotlinx-kover #822](https://github.com/Kotlin/kotlinx-kover/issues/822) is the same bug in another
plugin: concurrent report tasks of different projects cross-contaminating through shared statics.
Gradle's own answer for state that is *deliberately* shared between tasks is a Build Service; error
counters are not deliberately shared state, they belong to a single generation.

**The state of the art does not use global counters.**
[javac](https://openjdk.org/groups/compiler/doc/hhgtjavac/diagnostics.html) reports through an
instance `Log` facade, which creates `JCDiagnostic` values rendered by a `DiagnosticFormatter`, and
exposes a [`DiagnosticListener`](https://docs.oracle.com/javase/8/docs/api/javax/tools/JavaCompiler.html)
SPI so an embedder can collect diagnostics itself. Roslyn collects into a
[`DiagnosticBag`](https://github.com/dotnet/roslyn/blob/main/src/Compilers/Core/Portable/Diagnostic/DiagnosticBag.cs)
instance. In both, a diagnostic is a *value* with a severity, a message and a location, and the
collection is owned by one compilation.

Two further forces point the same way:

- **A CLI round trip with no CLI.** `Parser.parse()` assembles `-CODE_GENERATOR=…`,
  `-OUTPUT_DIRECTORY=…` and `-NODE_CUSTOM=…` strings and hands them to
  `WaggleOptions.setCmdLineOption`, which parses them back. The Gradle plugin already holds these as
  `Language`, `File` and `List<String>`. [SPECIFICATION §3](../SPECIFICATION.md) makes a general-purpose
  CLI a non-goal, so typed values are stringified and re-parsed for no consumer, and a wrong value is
  found at runtime instead of by the compiler.
- **Names that collide.** `org.hivevm.waggle.ParserBuilder` (the supported fluent entry point of
  [SPECIFICATION §3](../SPECIFICATION.md)) and `org.hivevm.waggle.generator.ParserBuilder` (an internal
  stage that computes lookahead plans into `ParserData`) share a name, as do
  `org.hivevm.gradle.ParserGenerator` (the Gradle task) and
  `org.hivevm.waggle.generator.ParserGenerator` (the back end). The driver itself is called `Parser`,
  which is also the name of the class it generates (`org.hivevm.waggle.parser.Parser`).

## Decision

We will make the error channel and the generation settings **values owned by one generation**:

1. **`Diagnostics` is an instance.** A `Diagnostic` is a record of severity (error or warning),
   message and optional source location. `Diagnostics` collects them and answers `hasError()`,
   `errorCount()` and `warningCount()` from what it collected. It writes each diagnostic to a
   `DiagnosticSink` as it is reported, defaulting to `System.err` in the format used today, so
   message text and ordering are unchanged ([ADR-0011](0011-error-handling-contract.md): only the
   delivery of a failure changes, never the messages).
2. **A `GenerationContext` carries it.** One context per generation holds the `Diagnostics` and the
   resolved options, and is passed to every stage — parse, semantic analysis, lexer construction,
   code generation — and to `parser/jjtree`. No stage reaches for a global.
3. **`JavaCCErrors` is deleted**, along with every static call site, including the grammar actions in
   `Waggle.waggle` and `JJTree.waggle`. The base parser (`AbstractJavaCCParser`) exposes the context's
   `Diagnostics` so grammar actions report through it.
4. **No mutable static state under `org.hivevm.waggle`.** This includes `doc/JJDocGlobals`, whose
   `input_file`, `output_file` and `generator` fields become state of one JJDoc run.
5. **`GenerationRequest` replaces the CLI round trip.** A record of grammar file, target language,
   output directory and custom nodes, built by the Gradle plugin and by `ParserBuilder`, and consumed
   directly. `WaggleOptions.setCmdLineOption` keeps serving grammar `options { … }` blocks and JJDoc's
   own entry point; nothing constructs option strings to parse them back.
6. **The driver is renamed `WaggleCompiler`**, and the internal `generator.ParserBuilder` becomes
   `generator.ParserPlanner`, so each of the two colliding pairs keeps one name. The public
   `org.hivevm.waggle.ParserBuilder` — a supported entry point — keeps its name.

## Consequences

- Two generations in one JVM become independent, whether they run in sequence or in parallel. The
  build survives the configuration cache and parallel task execution without a Build Service.
- The generator becomes testable in process: a test asserts on the diagnostics a generation collected
  instead of scraping `System.err`, and can run generations concurrently.
- The plugin's settings are checked by the compiler rather than by a string parser at runtime.
- The diff is large and mostly mechanical: about eighteen classes, both self-hosted grammars, and the
  driver. It is worth reviewing as one change because a half-migrated channel is worse than either
  end state — two channels is the exact defect [ADR-0011](0011-error-handling-contract.md) removed.
- **Bootstrap.** Grammar *action text* is copied verbatim by the published plugin, so changing the
  actions in `Waggle.waggle` takes effect in the same build; unlike a change to what the templates
  emit, this needs no second release ([ADR-0013](0013-break-model-parser-dependency-cycle.md)
  amendment). The generated parser must compile against the new API, which is why the accessor lives
  on the hand-written `AbstractJavaCCParser`.
- Generated output is expected to be byte-for-byte unchanged: nothing here alters what a back end
  emits.
- Risk: `Diagnostics` makes it cheap to add a diagnostic where the code used to fail silently, which
  would change what a build prints. Keeping the sink format identical bounds that risk but does not
  remove it.

## Alternatives considered

- **Keep `JavaCCErrors`, make the counters `ThreadLocal`.** Rejected: it hides the coupling instead of
  removing it, still makes the channel ambient, breaks as soon as a generation spans threads, and
  leaves the API untestable — a test still cannot ask what a particular generation reported.
- **Hold the error state in a Gradle Build Service.** Rejected: a Build Service is for state that is
  deliberately shared *across* tasks, which error counters are not, and it would tie the library to
  Gradle, contradicting `ParserBuilder` as a supported non-Gradle entry point
  ([SPECIFICATION §3](../SPECIFICATION.md)).
- **Thread a `Diagnostics` parameter through every stage without a context.** Rejected: the same
  argument would be added to every signature, and the next piece of per-generation state would repeat
  the exercise. One context is the object the pipeline already implies.
- **Let each stage return its own `Diagnostics` and merge them.** Rejected: merging is machinery for
  no gain, and [ADR-0011](0011-error-handling-contract.md) already settled that the verdict is
  central.
- **Keep the CLI-string round trip and only fix the error channel.** Rejected: the two are the same
  defect seen twice — a typed value forced through an untyped global — and the round trip is what
  keeps the driver unable to state what it needs.
