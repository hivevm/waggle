// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.api.ParserBuilder;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What the tests that generate a grammar and then build or run the result have in common:
 * generating, compiling the Java output, and running the compilers and programs of the other
 * targets without letting one that never ends hang the build.
 */
final class GeneratedSources {

    /** The modules every generated Rust grammar has. */
    static final String RUST_MODULES = "pub mod token;\npub mod charstream;\n"
            + "pub mod parserconstants;\npub mod lexer;\npub mod parser;\n";

    /** How long a compiler may take; a generated program gets far less. */
    private static final long BUILD_SECONDS = 600;

    private GeneratedSources() {
    }

    /** Generates {@code grammar} for {@code language} into {@code target}, and returns it. */
    static Path generate(Path grammar, Language language, Path target) {
        return GeneratedSources.generate(grammar, language, target, null);
    }

    /**
     * @param customNodes the node classes the author supplies, without their {@code AST} prefix
     */
    static Path generate(Path grammar, Language language, Path target, List<String> customNodes) {
        new ParserBuilder()
                .setLanguage(language)
                .setParserFile(grammar.toFile())
                .setTargetDir(target.toFile())
                .setCustomNodes(customNodes)
                .build().parse();
        return target;
    }

    /** Writes {@code text} to {@code grammar}, then generates it as {@link #generate} does. */
    static Path generate(Path grammar, String text, Language language, Path target)
            throws IOException {
        Files.createDirectories(grammar.getParent());
        Files.writeString(grammar, text);
        return GeneratedSources.generate(grammar, language, target);
    }

    /**
     * Compiles every {@code .java} file below {@code sources} into {@code classes}, fails with the
     * compiler's errors unless it succeeds, and returns a loader for the result.
     */
    static ClassLoader javac(Path sources, Path classes) throws IOException {
        List<File> files;
        try (Stream<Path> paths = Files.walk(sources)) {
            files = paths.filter(p -> p.toString().endsWith(".java")).map(Path::toFile).toList();
        }
        assertFalse(files.isEmpty(), "no Java sources below " + sources);

        Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var ok = compiler.getTask(null, manager, diagnostics, List.of("-d", classes.toString()),
                    null, manager.getJavaFileObjectsFromFiles(files)).call();

            var errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(Object::toString).collect(Collectors.joining("\n"));
            assertTrue(ok, "the Java below " + sources + " does not compile:\n" + errors);
        }
        return new URLClassLoader(new URL[] {classes.toUri().toURL()},
                GeneratedSources.class.getClassLoader());
    }

    /** Whether {@code command} runs and succeeds, e.g. a compiler asked for its version. */
    static boolean onPath(String... command) {
        try {
            return new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** What a process printed, and how it ended. */
    record Outcome(boolean finished, int status, String out, String err) {
    }

    /**
     * Runs {@code builder} for at most {@code seconds}, and stops it after that.
     *
     * <p>Its output goes to files, not to a pipe read to its end: a program that loops would block
     * that read forever, and the timeout would never be reached. Only the first MiB of each is
     * kept, since such a program may print until it is stopped.
     */
    static Outcome runBounded(ProcessBuilder builder, long seconds)
            throws IOException, InterruptedException {
        var out = Files.createTempFile("waggle", ".out");
        var err = Files.createTempFile("waggle", ".err");
        try {
            var process = builder.redirectOutput(out.toFile()).redirectError(err.toFile()).start();
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
                return new Outcome(false, -1, GeneratedSources.head(out), GeneratedSources.head(err));
            }
            return new Outcome(true, process.exitValue(), GeneratedSources.head(out),
                    GeneratedSources.head(err));
        } finally {
            Files.delete(out);
            Files.delete(err);
        }
    }

    /** Runs a compiler over generated sources and fails with what it printed unless it succeeds. */
    static void assertBuilds(ProcessBuilder builder, String failure)
            throws IOException, InterruptedException {
        var outcome = GeneratedSources.runBounded(builder.redirectErrorStream(true),
                GeneratedSources.BUILD_SECONDS);
        assertTrue(outcome.finished(), failure + ": the compiler did not end");
        assertEquals(0, outcome.status(), failure + ":\n" + outcome.out());
    }

    /**
     * Declares the {@link #RUST_MODULES} of {@code module} below {@code target}, where its sources
     * were generated, builds {@code main} into a program and returns its path.
     */
    static Path rustProgram(Path target, String module, String main, String... flags)
            throws IOException, InterruptedException {
        Files.writeString(target.resolve(module.replace("r#", "")).resolve("mod.rs"),
                GeneratedSources.RUST_MODULES);
        Files.writeString(target.resolve("main.rs"), main);

        var command = new ArrayList<>(List.of("rustc", "--edition", "2024"));
        command.addAll(List.of(flags));
        command.addAll(List.of("-o", "program", "main.rs"));
        GeneratedSources.assertBuilds(new ProcessBuilder(command).directory(target.toFile()),
                "the generated Rust does not build");
        return target.resolve("program");
    }

    private static String head(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            return new String(in.readNBytes(1 << 20), StandardCharsets.UTF_8);
        }
    }
}
