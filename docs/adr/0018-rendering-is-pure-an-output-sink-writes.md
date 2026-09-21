# ADR-0018: Rendering produces text; an output sink writes it

- **Status:** 🟡 Proposed
- **Date:** 2026-09-20
- **Deciders:** HiveVM Waggle maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

[ADR-0005](0005-custom-template-engine.md) settled that HiveVM Waggle owns a small template engine
whose directives are line comments, so a template stays close to real target source. It said nothing
about where the rendered text goes, and the engine decided that for itself: rendering *is* writing a
file.

`SourceProvider.render(Options, String)` resolves the template resource, computes the target file,
creates its parent directories, opens a `FileOutputStream` and renders into it. `Template.render`
takes an `OutputStream`. There is no point at which the rendered source exists as a value.

Three things follow.

**A back end cannot be tested without a file system.** Every assertion about what a generator emits
has to write to a temporary directory and read it back. `MultiTargetGenerationTest`,
`CppCompilesTest`, `RustCompilesTest` and `GeneratedCodeCompilesTest` all do exactly that; the tests
added for tree building do too. The pattern is well known and so is the answer: make the output
destination an explicit dependency and
[extract the pure rendering](https://github.com/cdsap/ProjectGenerator/issues/468), so that tests
depend on an [in-memory implementation](https://github.com/Testably/Testably.Abstractions) instead of
temporary folders.

**The engine owns a policy it should not.** `TemplateWriter` computes the MD5 of the rendered bytes
and appends a `// Checksum=…` line, and appends the consumed option names. That belongs to the
rendered text and is fine. But *where* those bytes go, whether a parent directory is created, and
what happens if the file already has the same content, are decisions of the caller, not of a
template engine that [ADR-0005](0005-custom-template-engine.md) deliberately kept "decoupled from any
parser concept and reusable on its own".

**The three template enums have drifted apart.** `JavaTemplate`, `CppTemplate` and `RustTemplate`
each re-implement `getPath`, `getType`, `getTargetFile` and `reservedNames`, with three different
notions of how a template's resource path relates to the file it writes — C++ has a `filetype` for
the `.h`/`.cc` split, Java folds the package into the directory, Rust lower-cases everything. Adding
the `tree/` prefix for [ADR-0016](0016-tree-building-as-an-optional-module.md) meant editing all
three in the same way.

## Decision

We will make **rendering a function from a template and a context to text**, and give the writing to
an explicit sink:

1. **`Template.render(title, environment)` returns a `String`.** It computes the checksum and the
   option list exactly as today — those are part of the rendered text — and it opens nothing.
2. **An `OutputSink` writes it.** `sink.write(file, text)`. Two implementations:
   - `FileSink`, which creates the parent directory and writes UTF-8, as today, and skips a write
     whose content is byte-identical to what the file already holds, so an unchanged file keeps its
     timestamp and Gradle's up-to-date checks see the truth;
   - `InMemorySink`, which keeps the text, so a test can ask what a generation emitted without a
     temporary directory.
3. **The sink is chosen per generation,** carried by the `GenerationContext` that
   [ADR-0015](0015-diagnostics-and-generation-request.md) introduced, and reaches the back ends the
   same way their options and diagnostics do. A caller that does not choose one gets a `FileSink`.
4. **One `TemplateSet` per target replaces the three enums.** It answers the two questions the three
   enums answer differently today — which resource a template comes from, and which file it writes —
   and it is the one place the `tree/` split is expressed. Templates are grouped as `runtime/`,
   `lexer/`, `parser/` and `tree/`, so the directory says what a file is for.

## Consequences

- A generator test asserts on text, not on a directory. The compile tests keep writing files,
  because compiling is the point of them, but nothing else has to.
- The engine stops knowing about files. That is what ADR-0005 claimed for it and what the code did
  not do.
- `FileSink`'s skip-if-unchanged makes regeneration idempotent in a way the build can see: today
  every run rewrites every file even when nothing changed, so every downstream task re-runs.
- Rendering a large template now builds its text in memory before writing. The largest generated
  file in this repository is well under a megabyte, so this is not a concern worth designing around;
  if it ever becomes one, a streaming sink is an addition, not a redesign.
- The `TemplateSet` change touches every `XTemplate.FOO.render(options)` call site — about fifty —
  and the three enums disappear. Generated output must not move: the resource path may change, the
  target file may not.
- Risk: the sink is one more thing threaded through the pipeline. It earns that by being the only
  way a back end can be tested without a disk; if it grows beyond `write`, it has become a file
  system abstraction, which is a larger decision than this one.

## Alternatives considered

- **Keep `render(OutputStream)` and pass a `ByteArrayOutputStream` in tests.** Rejected: it works,
  and it is what a test would have to invent every time. It leaves the back ends unable to say where
  their output goes, so the file path stays hard-wired inside the engine.
- **A file-system abstraction (`FileSystem`, `Path`, `exists`, `delete`, …).** Rejected as larger
  than the need: generation writes files and never reads or deletes them, so `write(file, text)` is
  the whole interface. The JDK's own `FileSystem` SPI would also work and is heavier than a
  one-method sink.
- **Leave the three template enums alone.** Rejected: they already disagree about how a resource path
  maps to an output file, and every cross-cutting change — the `tree/` prefix was the last one — has
  to be made three times and can be made three different ways.
- **Give the sink to the template instead of the generation.** Rejected: a template is a value that
  can be rendered many times; the sink belongs to the run, next to the options and the diagnostics
  it already carries.
