// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.api.ParserBuilder;

import org.hivevm.waggle.api.WaggleCompiler;

import org.hivevm.waggle.api.GenerationRequest;

import org.hivevm.waggle.api.Language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.waggle.diag.DiagnosticSink;
import org.hivevm.waggle.diag.Diagnostics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tree building follows from the grammar's options, not from a marker in its text: a tree is built
 * only when {@code USE_AST} is set and the grammar declares a {@code #Node} (ADR-0028).
 *
 * <p>The driver used to scan the grammar for an {@code @generated(JJTree)} comment — the stamp
 * JavaCC's JJTree pre-processor left on the intermediate grammar it wrote. There is no such
 * pre-processor and no intermediate grammar (ADR-0010), so the marker could only ever arrive by
 * accident, and when it did it switched the parser template's tree code on for a grammar that
 * declares no node at all.
 */
class TreeDetectionTest {

    /** Carries the marker in a comment, but declares no {@code #Node}. */
    private static final String MARKED = """
            grammar Marked;

            /* @generated(JJTree) */

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input = < WORD > <EOF> ;

            TOKEN = < WORD: (["a"-"z"])+ > ;
            """;

    /** The same grammar with a node descriptor and USE_AST, so tree building is requested. */
    private static final String WITH_NODE = """
            grammar Noded;

            options {
              USE_AST: true,
              JAVA_PACKAGE: "org.example"
            }

            Input #Root = < WORD > <EOF> ;

            TOKEN = < WORD: (["a"-"z"])+ > ;
            """;

    @Test
    void theMarkerDoesNotSwitchOnTreeBuilding(@TempDir Path dir) throws IOException {
        var target = generate(dir, "Marked.waggle", TreeDetectionTest.MARKED);

        assertFalse(Files.readString(target.resolve("org/example/Parser.java")).contains("jjtree"),
                "a grammar without #Node must not build a tree, whatever its comments say");
        assertFalse(Files.isRegularFile(target.resolve("org/example/Node.java")),
                "no node runtime may be written for a grammar without #Node");
    }

    @Test
    void aNodeDescriptorWithUseAstSwitchesOnTreeBuilding(@TempDir Path dir) throws IOException {
        var target = generate(dir, "Noded.waggle", TreeDetectionTest.WITH_NODE);

        assertTrue(Files.readString(target.resolve("org/example/Parser.java")).contains("jjtree"),
                "a grammar with #Node and USE_AST must build a tree");
        assertTrue(Files.isRegularFile(target.resolve("org/example/Node.java")),
                "the node runtime must be written for a grammar with #Node and USE_AST");
    }

    /**
     * A node descriptor alone does not build a tree: without USE_AST it is ignored, and the author
     * is told once, so a grammar can carry its annotations before it opts in (ADR-0028).
     */
    @Test
    void withoutUseAstNodeDescriptorsAreIgnored(@TempDir Path dir) throws IOException {
        var grammar = TreeDetectionTest.WITH_NODE.replace("  USE_AST: true,\n", "");
        var target = generate(dir, "Noded.waggle", grammar);

        assertFalse(Files.readString(target.resolve("org/example/Parser.java")).contains("jjtree"),
                "a grammar without USE_AST must not build a tree");
        assertFalse(Files.isRegularFile(target.resolve("org/example/Node.java")),
                "no node runtime may be written for a grammar without USE_AST");

        var diagnostics = diagnose(dir.resolve("diag"), "Noded.waggle", grammar);
        assertEquals(1, diagnostics.collected().stream()
                        .filter(d -> d.message().contains("USE_AST")).count(),
                "the ignored node descriptors must be reported once: " + diagnostics.collected());
    }

    /** A tree option the grammar will never use, next to a grammar that builds no tree. */
    private static final String IGNORED_VISITOR_OPTION = """
            grammar Plain;

            options {
              USE_AST: true,
              JAVA_PACKAGE: "org.example",
              VISITOR_DATA_TYPE: "Payload"
            }

            Input%s = < WORD > <EOF> ;

            TOKEN = < WORD: (["a"-"z"])+ > ;
            """;

    /**
     * VISITOR_DATA_TYPE without VISITOR is worth a warning — but only to an author who asked for a
     * tree. It used to be checked for every grammar, and in fact never at all: the check ran before
     * the grammar's own options block had been read (ADR-0016).
     */
    @Test
    void treeOptionsAreCheckedOnlyForAGrammarWithATree(@TempDir Path dir) throws IOException {
        var withoutTree = diagnose(dir.resolve("plain"), "Plain.waggle",
                TreeDetectionTest.IGNORED_VISITOR_OPTION.formatted(""));
        assertFalse(withoutTree.collected().stream()
                        .anyMatch(d -> d.message().contains("VISITOR")),
                "a grammar that builds no tree must not be told about VISITOR_DATA_TYPE: "
                        + withoutTree.collected());

        var withTree = diagnose(dir.resolve("noded"), "Plain.waggle",
                TreeDetectionTest.IGNORED_VISITOR_OPTION.formatted(" #Root"));
        assertTrue(withTree.collected().stream()
                        .anyMatch(d -> d.message().contains("VISITOR_DATA_TYPE")),
                "a grammar that builds a tree must be told: " + withTree.collected());
    }

    private static Diagnostics diagnose(Path dir, String name, String grammar) throws IOException {
        Files.createDirectories(dir);
        var source = dir.resolve(name);
        Files.writeString(source, grammar);

        var diagnostics = new Diagnostics(DiagnosticSink.SILENT);
        new WaggleCompiler(new GenerationRequest(source.toFile(), Language.JAVA,
                dir.resolve("out").toFile(), List.of()), diagnostics).parse();
        return diagnostics;
    }

    private static Path generate(Path dir, String name, String grammar) throws IOException {
        var source = dir.resolve(name);
        Files.writeString(source, grammar);

        var target = dir.resolve("out");
        new ParserBuilder()
                .setLanguage(Language.JAVA)
                .setParserFile(source.toFile())
                .setTargetDir(target.toFile())
                .build().parse();
        return target;
    }
}
