# ADR-0007: Java 21 as the implementation baseline

- **Status:** 🟢 Accepted
- **Date:** 2026-07-05
- **Deciders:** HiveVM CC maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

HiveVM CC is a Gradle plugin and library. It needs a Java language level for its own sources. A
newer level buys modern language features (records, pattern matching, sealed types, `switch`
patterns) that directly serve the "more maintainable code" goal from
[ADR-0003](0003-fork-javacc-as-baseline.md); the codebase already uses records and modern `switch`.
The constraint is that the chosen level must be supported by the Dev Container image
([ADR-0002](0002-dev-container-runtime.md)) and by CI, and that Gradle-plugin consumers must run a
JDK at least this new.

## Decision

The implementation targets **Java 21**:

1. `build.gradle` sets `sourceCompatibility` and `targetCompatibility` to `VERSION_21`.
2. CI builds on Temurin **JDK 21**; the Dev Container base image provides a compatible JDK.
3. Modern language features (records, pattern matching, enhanced `switch`) are embraced where they
   reduce boilerplate.

The **output language toolchains are independent** of this: the generated Java/C++/Rust parsers have
their own runtime requirements and are not bound to JDK 21.

## Consequences

- The codebase can use current Java features, aiding readability and the maintainability goal.
- Consumers of the Gradle plugin must build with **JDK 21 or newer**; this is a hard floor.
- The Dev Container image tag and the CI JDK version are coupled to this baseline — raising the
  baseline means bumping all three together (build config, image, CI).
- Backporting to an older JDK would require removing modern-feature usage and is out of scope.

## Alternatives considered

- **An older LTS (e.g. Java 17).** Rejected: forgoes language features already used for
  maintainability, for a wider-compatibility benefit no current consumer needs.
- **A non-LTS / bleeding-edge JDK.** Rejected: narrows the supported runtime for consumers without a
  concrete need; an LTS baseline is the safer default.
</content>
