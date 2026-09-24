// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.api.ParserBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tree building, end to end, on the grammar of the grammar language.
 *
 * <p>Waggle.waggle sets USE_AST with NODE_MULTI, NODE_DEFAULT_VOID, NODE_SCOPE_HOOK and VISITOR.
 * This test generates it, unchanged, through Waggle itself: the node classes, the visitor and the
 * parser with the tree code woven into its node scopes.
 */
class AstTest {

    private static final Path GRAMMAR = Path.of("src", "main", "resources", "Waggle.waggle");
    private static final Path GOLDEN = Path.of("src", "test", "resources", "ast", "Parser.java.txt");

    /** Where the generated parser lands, relative to the target directory. */
    private static final Path PACKAGE = Path.of("org", "hivevm", "waggle", "grammar");

    @TempDir
    static Path dir;

    private static Path generated;

    @BeforeAll
    static void generate() throws IOException {
        AstTest.generated = AstTest.dir.resolve("generated");
        new ParserBuilder()
                .setLanguage(Language.JAVA)
                .setParserFile(AstTest.GRAMMAR.toFile())
                .setTargetDir(AstTest.generated.toFile())
                .build().parse();
    }

    /** Every node descriptor of the grammar becomes a node class, and the visitor is written. */
    @Test
    void theNodeClassesAreWritten() throws IOException {
        List<String> files;
        try (Stream<Path> paths = Files.list(AstTest.generated.resolve(AstTest.PACKAGE))) {
            files = paths.map(p -> p.getFileName().toString()).toList();
        }

        for (var expected : List.of("Node.java", "NodeType.java", "NodeState.java",
                "NodeVisitor.java", "NodeDefaultVisitor.java", "ASTGrammar.java",
                "ASTCompilationUnit.java", "ASTOptionBinding.java", "ASTBNF.java",
                "ASTBNFAction.java", "ASTBNFNodeScope.java", "ASTNodeDescriptor.java")) {
            assertTrue(files.contains(expected), "no " + expected + " was generated: " + files);
        }
        assertTrue(files.stream().filter(f -> f.startsWith("AST")).count() > 20,
                "Waggle.waggle declares many nodes, found " + files);
    }

    /**
     * The parser with the tree code woven into its node scopes. The golden file is for the Java
     * target; the other two would add two more goldens for the same walk.
     */
    @Test
    void theParserMatchesTheGoldenFile() throws IOException {
        var parser = Files.readString(AstTest.generated.resolve(AstTest.PACKAGE).resolve("Parser.java"),
                StandardCharsets.UTF_8);

        assertEquals(Files.readString(AstTest.GOLDEN, StandardCharsets.UTF_8), parser,
                "the generated parser changed; inspect the diff before updating " + AstTest.GOLDEN);
    }

    /** The parser, the nodes and the visitor compile against the hand-written base parser. */
    @Test
    void theGeneratedTreeCompiles() throws IOException {
        List<File> sources;
        try (Stream<Path> paths = Files.walk(AstTest.generated)) {
            sources = paths.filter(p -> p.toString().endsWith(".java")).map(Path::toFile).toList();
        }

        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            var classes = Files.createDirectories(AstTest.dir.resolve("classes"));
            var ok = compiler.getTask(null, files, diagnostics, List.of("-d", classes.toString()),
                    null, files.getJavaFileObjectsFromFiles(sources)).call();

            var errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(Object::toString).collect(Collectors.joining("\n"));
            assertTrue(ok, "the generated tree does not compile:\n" + errors);
        }
    }
}
