package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A failed generation must be reported to the caller (ADR-0011).
 *
 * <p>Before, there were only two outcomes and both were wrong: an I/O error was caught, printed and
 * ignored — after which generation announced "Parser generated successfully." and returned normally —
 * or a grammar error triggered {@code System.exit()}, which kills the Gradle daemon and the test
 * executor instead of failing the task. Neither test below could even be written back then: the first
 * one would have passed silently, the second one would have taken the JVM down with it.
 */
class GenerationFailureTest {

    @Test
    void missingGrammarFails(@TempDir Path dir) {
        var missing = dir.resolve("DoesNotExist.waggle");

        assertThrows(GenerationException.class, () -> generate(missing, dir),
                "a missing grammar must fail, not report success");
    }

    @Test
    void brokenGrammarFails(@TempDir Path dir) throws IOException {
        var source = dir.resolve("Broken.waggle");
        // References a token that is never defined -> a semantic error.
        Files.writeString(source, """
                grammar Broken;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = < UNDEFINED_TOKEN > <EOF> ;
                """);

        var thrown = assertThrows(GenerationException.class, () -> generate(source, dir),
                "a grammar with a semantic error must fail");
        assertTrue(thrown.getMessage().contains("Broken.waggle"),
                "the failure should name the grammar: " + thrown.getMessage());
    }

    /**
     * The C++ back end names its files after the grammar, so a grammar called {@code Tree} wrote its
     * parser over the generated {@code Tree.h} — the AST base class. The output then did not compile,
     * and nothing said why. Java is unaffected: it writes {@code Parser.java} whatever the grammar is
     * called.
     */
    @Test
    void aGrammarNamedAfterARuntimeClassFails(@TempDir Path dir) throws IOException {
        var source = dir.resolve("Tree.waggle");
        Files.writeString(source, """
                grammar Tree;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = < WORD > <EOF> ;

                TOKEN = < WORD: (["a"-"z"])+ > ;
                """);

        var thrown = assertThrows(GenerationException.class,
                () -> generate(source, dir, Language.CPP),
                "a C++ grammar named Tree would overwrite the generated Tree.h");
        assertTrue(thrown.getCause().getMessage().contains("Tree"),
                "the failure should name the offending grammar: " + thrown.getCause().getMessage());

        generate(source, dir, Language.JAVA);
    }

    /**
     * The Rust parser template still carries JavaCC's Java trace runtime — {@code trace_call} and
     * friends are Java methods on a Java class — and the generator matched it: it wrapped every
     * production in {@code try}/{@code finally}, which Rust does not have, and escaped the trace
     * strings with {@code Language.JAVA}, so a non-ASCII production name came out as {@code \\uXXXX},
     * which is not Rust escape syntax either. None of it could compile, and nothing said so.
     */
    @Test
    void rustRejectsTheDebugOptions(@TempDir Path dir) throws IOException {
        for (var option : new String[] { "DEBUG_PARSER", "DEBUG_LOOKAHEAD" }) {
            var source = dir.resolve("Traced.waggle");
            Files.writeString(source, """
                    grammar Traced;

                    options {
                      RUST_MODULE: "traced",
                      %s: true
                    }

                    Input = < WORD > <EOF> ;

                    TOKEN = < WORD: (["a"-"z"])+ > ;
                    """.formatted(option));

            var thrown = assertThrows(GenerationException.class,
                    () -> generate(source, dir, Language.RUST),
                    option + " must be refused for the Rust target, not emitted as Java");
            assertTrue(thrown.getMessage().contains(option),
                    "the failure should name the option: " + thrown.getMessage());

            generate(source, dir, Language.JAVA);
        }
    }

    private static void generate(Path source, Path dir) {
        generate(source, dir, Language.JAVA);
    }

    private static void generate(Path source, Path dir, Language language) {
        new ParserBuilder()
                .setLanguage(language)
                .setParserFile(source.toFile())
                .setTargetDir(dir.resolve("out-" + language).toFile())
                .build().parse();
    }
}
