// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.source.InMemorySink;
import org.hivevm.waggle.api.GenerationException;
import org.hivevm.waggle.api.GenerationRequest;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.api.WaggleCompiler;
import org.hivevm.waggle.diag.Diagnostic;
import org.hivevm.waggle.diag.DiagnosticSink;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.diag.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A faulty grammar is reported as a diagnostic that says what is wrong and where. */
class GrammarDiagnosticsTest {

    @TempDir
    Path dir;

    /**
     * The loop check marked the tokens it walked through their ordinal instead of their walk status.
     * A named token always has an ordinal, so the walk stopped at the first reference: only a token
     * that named itself was caught, and a loop through two tokens overflowed the stack in stage 4.
     */
    @Test
    void aLoopThroughTwoTokensIsAnError() throws IOException {
        var errors = errors(generateFailing("""
                TOKEN =
                  < A: "x" <B> >
                | < #B: "y" <A> >
                ;
                """));

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().message().contains("Loop in regular expression"),
                errors.toString());
    }

    @Test
    void aLoopThroughThreeTokensIsAnError() throws IOException {
        var errors = errors(generateFailing("""
                TOKEN =
                  < A: "x" <B> >
                | < #B: "y" <C> >
                | < #C: "z" <A> >
                ;
                """));

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().message().contains("Loop in regular expression"),
                errors.toString());
    }

    /** A token that is reached twice without a loop is not one. */
    @Test
    void aSharedTokenIsNotALoop() throws IOException {
        var diagnostics = generate("""
                TOKEN =
                  < A: <D> "x" <D> >
                | < #D: ["0"-"9"] >
                ;
                """);

        assertFalse(diagnostics.hasError(), diagnostics.collected().toString());
    }

    /** The error used to name the lexer's visitor as its location, which has no position. */
    @Test
    void anEmptyCharacterSetIsReportedWhereItIsWritten() throws IOException {
        var errors = errors(generateFailing("""
                TOKEN = < A: [] > ;
                """));

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.getFirst().message().contains("Empty character set"), errors.toString());
        assertTrue(errors.getFirst().hasPosition(), errors.toString());
    }

    /**
     * A value of the wrong type used to be stored as written, and failed later as a
     * ClassCastException that named neither the option nor the grammar. JAVA_IMPORTS is stored as a
     * list but written as a string, and must still be accepted.
     */
    @Test
    void anOptionOfTheWrongTypeIsIgnoredWithAWarning() throws IOException {
        var diagnostics = generate("""
                TOKEN = < A: "a" > ;
                """, "CHOICE_AMBIGUITY_CHECK: \"3\"", "NO_DFA: 1",
                "JAVA_IMPORTS: \"java.util.List\"");

        assertFalse(diagnostics.hasError(), diagnostics.collected().toString());
        var warnings = diagnostics.collected().stream()
                .filter(d -> d.message().startsWith("Bad option value")).toList();
        assertEquals(2, warnings.size(), diagnostics.collected().toString());
        assertTrue(warnings.stream().allMatch(Diagnostic::hasPosition), warnings.toString());
    }

    /**
     * A token without a label or a literal is named by its kind in a lookahead warning. The warning
     * printed the token's position within the common prefix instead, so it named the wrong token --
     * here kind 0, which is EOF.
     */
    @Test
    void aConflictOnAnUnnamedTokenNamesItsKind() throws IOException {
        var diagnostics = new Diagnostics(DiagnosticSink.SILENT);
        compileOrExplain(diagnostics, """
                grammar Example;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( Letter() Letter() "x" | Letter() Letter() "y" ) <EOF> ;

                Letter = < ["a"-"z"] > ;
                """);

        var warning = diagnostics.collected().stream()
                .filter(d -> d.message().contains("common prefix")).findFirst()
                .orElseThrow(() -> new AssertionError(diagnostics.collected().toString()));
        assertTrue(warning.message().matches("(?s).*<token of kind (\\d+)> <token of kind \\1>.*"),
                warning.message());
        assertFalse(warning.message().contains("<token of kind 0>"), warning.message());
    }

    private static List<Diagnostic> errors(Diagnostics diagnostics) {
        return diagnostics.collected().stream().filter(d -> d.severity() == Severity.ERROR).toList();
    }

    private Diagnostics generateFailing(String tokens) throws IOException {
        var diagnostics = new Diagnostics(DiagnosticSink.SILENT);
        assertThrows(GenerationException.class, () -> run(diagnostics, tokens));
        return diagnostics;
    }

    private Diagnostics generate(String tokens, String... options) throws IOException {
        var diagnostics = new Diagnostics(DiagnosticSink.SILENT);
        run(diagnostics, tokens, options);
        return diagnostics;
    }

    private void run(Diagnostics diagnostics, String tokens, String... options) throws IOException {
        compileOrExplain(diagnostics, """
                grammar Example;

                options {
                  JAVA_PACKAGE: "org.example"%s
                }

                Input = < A > <EOF> ;

                %s""".formatted(
                options.length == 0 ? "" : ",\n  " + String.join(",\n  ", options), tokens));
    }

    /** A failure names the diagnostics, which the exception only counts. */
    private void compileOrExplain(Diagnostics diagnostics, String grammar) throws IOException {
        try {
            compile(diagnostics, grammar);
        } catch (GenerationException e) {
            throw new GenerationException(e.getMessage() + " " + diagnostics.collected(), e);
        }
    }

    private void compile(Diagnostics diagnostics, String grammar) throws IOException {
        var source = this.dir.resolve("Example.waggle");
        Files.writeString(source, grammar);
        new WaggleCompiler(new GenerationRequest(source.toFile(), Language.JAVA,
                this.dir.resolve("out").toFile(), List.of()), diagnostics, new InMemorySink())
                .parse();
    }
}
