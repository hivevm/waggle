// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.api.GenerationRequest;

import org.hivevm.waggle.api.WaggleCompiler;

import org.hivevm.waggle.api.Language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.source.InMemorySink;
import org.hivevm.waggle.diag.DiagnosticSink;
import org.hivevm.waggle.diag.Diagnostics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A generation can be asked what it emitted, without a directory to emit into (ADR-0018).
 *
 * <p>Rendering used to be writing a file, so every assertion about generated source had to go
 * through a temporary directory and read it back.
 */
class InMemoryGenerationTest {

    private static final String GRAMMAR = """
            grammar Example;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input =
              < WORD > <EOF>
            ;

            SKIP = " " | "\\t" | "\\n" ;

            TOKEN = < WORD: (["a"-"z"])+ > ;
            """;

    @ParameterizedTest
    @EnumSource(Language.class)
    void everyTargetEmitsIntoTheSink(Language language, @TempDir Path dir) throws IOException {
        var sink = generate(language, dir);

        assertFalse(sink.files().isEmpty(), language + " emitted nothing");
        var out = dir.resolve("out");
        try (Stream<Path> paths = Files.walk(out)) {
            assertTrue(paths.noneMatch(Files::isRegularFile),
                    "nothing may be written to disk: " + sink.files().keySet());
        }
    }

    /** The parser is the file the whole pipeline ends in; its text is what a back end produces. */
    @Test
    void theGeneratedParserIsAvailableAsText(@TempDir Path dir) throws IOException {
        var sink = generate(Language.JAVA, dir);
        var parser = sink.endingWith("Parser.java")
                .orElseThrow(() -> new AssertionError("no parser among " + sink.files().keySet()));

        assertTrue(parser.contains("package org.example;"), parser.substring(0, 200));
        assertTrue(parser.contains("Input()"), "the grammar's production is missing");
        assertTrue(parser.contains("// Checksum="), "the checksum belongs to the rendered text");
    }

    /**
     * The same grammar rendered twice must produce the same text, which is what lets
     * {@code FileSink} leave an unchanged file alone.
     */
    @Test
    void renderingIsReproducible(@TempDir Path dir) throws IOException {
        assertEquals(relative(generate(Language.JAVA, dir.resolve("a")), dir.resolve("a")),
                relative(generate(Language.JAVA, dir.resolve("b")), dir.resolve("b")));
    }

    /** The emitted files keyed by their path below {@code dir}, so two runs can be compared. */
    private static Map<String, String> relative(InMemorySink sink, Path dir) {
        return sink.files().entrySet().stream().collect(Collectors.toMap(
                e -> dir.relativize(Path.of(e.getKey())).toString(), Map.Entry::getValue));
    }

    private static InMemorySink generate(Language language, Path dir) throws IOException {
        Files.createDirectories(dir);
        var source = dir.resolve("Example.waggle");
        Files.writeString(source, InMemoryGenerationTest.GRAMMAR);

        var sink = new InMemorySink();
        new WaggleCompiler(
                new GenerationRequest(source.toFile(), language, dir.resolve("out").toFile(),
                        List.of()),
                new Diagnostics(DiagnosticSink.SILENT), sink).parse();
        return sink;
    }
}
