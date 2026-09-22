// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.jjtree;

import org.hivevm.waggle.api.Waggle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.codegen.java.tree.JavaTreeEmitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JJTree's runtime path, exercised.
 *
 * <p>Generation and compilation were the only things tested here: {@code JJTreeParserDefault},
 * {@code JJTreeVisitor} and {@code TreeGenerator} were constructed nowhere, so the package was
 * regenerated on every build and never once run (ADR-0016). These tests parse the two self-hosted
 * grammars and write them back out through the public entry point.
 */
class JJTreeAstTest {

    private static final Path GRAMMARS = Path.of("src", "main", "resources");

    private static String read(String grammar) throws IOException {
        return Files.readString(JJTreeAstTest.GRAMMARS.resolve(grammar), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace('\r', '\n');
    }

    /** Both self-hosted grammars must parse into an AST whose root is the grammar itself. */
    @ParameterizedTest
    @ValueSource(strings = { "Waggle.waggle", "JJTree.waggle" })
    void theSelfHostedGrammarsParse(String grammar) throws Exception {
        var ast = JJTree.parse(JJTreeAstTest.read(grammar));

        assertTrue(ast.jjtGetNumChildren() > 0, grammar + " parsed into an empty tree");
        assertTrue(JJTreeAstTest.nodeTypes(ast).containsKey("ASTCompilationUnit"),
                "every grammar starts with a compilation unit: " + JJTreeAstTest.nodeTypes(ast));
    }

    /**
     * The grammar of the grammar language is the richer of the two: it is the only one that
     * exercises node descriptors, expansion scopes and actions together.
     */
    @Test
    void theTreeGrammarYieldsTheExpectedNodes() throws Exception {
        var types = JJTreeAstTest.nodeTypes(JJTree.parse(JJTreeAstTest.read("JJTree.waggle")));

        for (var expected : List.of("ASTGrammar", "ASTCompilationUnit", "ASTOptionBinding",
                "ASTBNF", "ASTBNFAction", "ASTBNFNodeScope", "ASTNodeDescriptor")) {
            assertTrue(types.containsKey(expected),
                    "no " + expected + " in the tree: " + types.keySet());
        }
        assertEquals(types.get("ASTGrammar"), 1, "there is exactly one grammar node");
        assertTrue(types.get("ASTBNF") > 20,
                "JJTree.waggle has many productions, found " + types.get("ASTBNF"));
    }

    /**
     * {@code NODE_SCOPE_HOOK} is on for this grammar, so the parser calls the open hook when it
     * opens a scope and the close hook when it closes one. The hooks record the first and the last
     * token of the node, so a node with one but not the other is a scope that was opened and never
     * closed, or closed without being opened.
     */
    @ParameterizedTest
    @ValueSource(strings = { "Waggle.waggle", "JJTree.waggle" })
    void theScopeHooksFireInBalancedPairs(String grammar) throws Exception {
        var unbalanced = new ArrayList<String>();
        JJTreeAstTest.walk(JJTree.parse(JJTreeAstTest.read(grammar)), node -> {
            var opened = node.getFirstToken() != null;
            var closed = node.getLastToken() != null;
            if (opened != closed) {
                unbalanced.add(node.getClass().getSimpleName()
                        + (opened ? " was opened but never closed" : " was closed but never opened"));
            }
        });

        assertEquals(List.of(), unbalanced, "unbalanced node scopes in " + grammar);
    }

    /**
     * The rewrite is the whole point of the package: the grammar goes in, and the same grammar with
     * the tree code woven into its node scopes comes out. The golden file is for the Java target;
     * the other two would add two more goldens for the same walk.
     */
    @Test
    void theRewriteMatchesTheGoldenFile() throws Exception {
        var golden = Path.of("src", "test", "resources", "jjtree", "JJTree.waggle.java.txt");
        var written = new StringWriter();
        var tree = JJTree.write(JJTree.parse(JJTreeAstTest.read("JJTree.waggle")),
                new JavaTreeEmitter(), Language.JAVA, written);

        assertFalse(tree.getNodesToGenerate().isEmpty(),
                "the walk found no node classes to write");
        assertEquals(Files.readString(golden, StandardCharsets.UTF_8), written.toString(),
                "the rewritten grammar changed; inspect the diff before updating " + golden);
    }

    /** How often each node class occurs, in the order the walk meets them. */
    private static Map<String, Integer> nodeTypes(ASTNode root) {
        var counts = new LinkedHashMap<String, Integer>();
        JJTreeAstTest.walk(root, node ->
                counts.merge(node.getClass().getSimpleName(), 1, Integer::sum));
        return counts;
    }

    private static void walk(ASTNode node, java.util.function.Consumer<ASTNode> visitor) {
        visitor.accept(node);
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            JJTreeAstTest.walk((ASTNode) node.jjtGetChild(i), visitor);
        }
    }
}
