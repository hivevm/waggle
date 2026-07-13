# ADR-0011: Generation reports failure by throwing, never by exiting the JVM

- **Status:** 🟢 Accepted
- **Date:** 2026-07-12
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

`ParserBuilder` and `Parser.parse()` are the supported programmatic entry points
([SPECIFICATION §3](../SPECIFICATION.md)), and the Gradle plugin ([ADR-0006](0006-gradle-plugin-interface.md))
drives them. Today neither can report a failure in a way a caller can act on. There are exactly two
outcomes, and both are wrong:

1. **A silent lie.** `Parser.parse()` catches `ParseException | IOException`, prints the stack trace,
   and then falls through to the success/failure evaluation, which only looks at the *static*
   `JavaCCErrors` counters. Those are untouched by an I/O failure, so a missing grammar file prints
   “Parser generated successfully.”, returns normally, and produces no output. The Gradle task goes
   green. *(This is not hypothetical: `generateParser` regenerated nothing for a long time and still
   reported BUILD SUCCESSFUL, and `H3QLTest` is green today only because its grammar paths do not
   exist and the resulting exception is swallowed.)*
2. **A JVM kill.** When `JavaCCErrors` *does* hold an error, `Parser.parse()` calls `System.exit()`.
   Inside the Gradle daemon that kills the whole JVM (“Gradle build daemon disappeared unexpectedly”)
   instead of failing the task; inside the JUnit executor it kills the test run.

There is no path that produces an ordinary error. `System.exit()` appears in `Parser.java:105,149`,
`ParserInterpreter.java:46`, `parser/jjtree/JJTreeParser.java:33` and six times in `doc/JJDocMain`.

Two independent error channels make this worse: the static counters in `JavaCCErrors` and
`SemanticContext.errorCount`. Errors raised through `SemanticContext` never increment `JavaCCErrors`,
so the final verdict in `Parser.parse()` can disagree with what actually happened.

## Decision

We will treat a failed generation as a **thrown exception**, and remove process control from the
library:

1. **`Parser.parse()` throws.** A parse error, a semantic error, or an I/O failure results in an
   exception that propagates to the caller. It no longer prints a stack trace and continues, and it
   no longer decides the outcome from static counters alone.
2. **No `System.exit()` outside the CLI.** `System.exit()` is permitted only in a `main` method
   (`doc/JJDocMain`). Library code signals failure by throwing.
3. **One error channel.** Errors collected via `SemanticContext` and via `JavaCCErrors` are counted in
   the same place, so “were there errors?” has a single answer.
4. **The Gradle task fails the build.** `generateParser` turns a generation failure into a task
   failure (a `GradleException`), rather than logging and returning.

## Consequences

- Callers of `ParserBuilder`/`Parser.parse()` must handle (or propagate) an exception. This is a
  **breaking change to a public entry point**, which is why it is recorded here rather than done
  quietly.
- A broken grammar or a missing file now fails the build instead of silently producing nothing. Some
  builds that are “green” today will legitimately turn red — that is the point of the change.
- `H3QLTest` will start failing, because it points at paths that do not exist on any machine. It is
  to be deleted or genuinely disabled as part of the change; keeping it green would mean keeping the
  bug.
- The generator becomes usable as a library and testable in-process: today any error inside a test
  kills the test JVM.
- Error *messages* are unaffected; only the way a failure is delivered changes.

## Alternatives considered

- **Return a status object instead of throwing.** Rejected: every existing caller ignores return
  values, so a status would be as easy to overlook as the current silence. An exception cannot be
  ignored by accident.
- **Keep `System.exit()` and merely fix the swallowed exception.** Rejected: it leaves the generator
  unusable as a library and keeps killing the Gradle daemon and the test executor.
- **Leave the two error counters separate and only reconcile them at the end.** Rejected: the
  duplication is the reason the final verdict can be wrong; reconciling after the fact preserves the
  trap.
