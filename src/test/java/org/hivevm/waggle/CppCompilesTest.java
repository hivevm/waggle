// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright (c) 2007-2009, Paul Cager. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: src/main/jjtree/JJTree.jjt, src/main/javacc/JavaCC.jj

package org.hivevm.waggle;

import org.hivevm.waggle.api.ParserBuilder;

import org.hivevm.waggle.api.Language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Regression tests: the generated C++ must actually <em>compile</em>.
 *
 * <p>It never did. The back end emitted a hard-coded {@code OQLTokenManager} left over from another
 * grammar, declared production return types as the production's own name, used {@code Token::next}
 * and {@code Token::specialToken} as fields where C++ exposes them as accessors, called a
 * {@code Reader::GetSuffix} that does not exist, and defined {@code jjCheckNAdd} and friends as bare
 * brace blocks, so that {@code if (c) jjCheckNAdd(s); else …} did not parse. Two brace bugs — a stray
 * {@code &#123;} in the trace and one {@code &#125;} too many in the template — happened to cancel each other
 * out in exactly the configuration that was looked at by hand.
 *
 * <p>None of this was noticed because nothing ever ran a C++ compiler over the output.
 */
class CppCompilesTest {

    /**
     * Exercises MORE, SKIP, SPECIAL_TOKEN, two lexical states, a token that matches the empty string
     * and a catch-all token — the combination that drives every branch of the token dispatch.
     */
    private static final String GRAMMAR = """
            grammar Any;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input =
              ( <WORD> | <ANY> )* <EOF>
            ;

            SKIP = " " | "\\t" | "\\n" | "\\r" ;

            MORE = "/*" : IN_COMMENT ;

            SPECIAL_TOKEN <IN_COMMENT> = < COMMENT: "*/" > : DEFAULT ;
            MORE <IN_COMMENT> = < ~[] > ;

            TOKEN =
              < WORD: (["a"-"z"])* >
            | < ANY: ~[] >
            ;
            """;

    /**
     * A grammar with an AST. Every node used to land in the same {@code MultiNode.cc}, the per-node
     * headers were never written at all, {@code NODE_CLASS} was left unset so the nodes extended
     * nothing, and the tree header emitted {@code #include ASTAdd.h"} — with the opening quote
     * missing.
     */
    private static final String GRAMMAR_AST = """
            grammar Ast;

            options {
              USE_AST: true,
              JAVA_PACKAGE: "org.example",
              NODE_MULTI: true,
              NODE_DEFAULT_VOID: true,
              VISITOR: true
            }

            Input() #Root =
              expr() <EOF>
            ;

            expr =
              term() ( < PLUS > term() #Add(2) )*
            ;

            term =
              <NUMBER> #Number
            | < LPAREN > expr() < RPAREN >
            ;

            SKIP = " " | "\\n" ;

            TOKEN =
              < PLUS: "+" >
            | < LPAREN: "(" >
            | < RPAREN: ")" >
            | < NUMBER: (["0"-"9"])+ >
            ;
            """;

    /** The same grammar with the token-manager trace on: it emits a different code path. */
    private static final String GRAMMAR_DEBUG = CppCompilesTest.GRAMMAR.replace(
            "  JAVA_PACKAGE: \"org.example\"",
            "  JAVA_PACKAGE: \"org.example\",\n  DEBUG_TOKEN_MANAGER: true");

    @Test
    void generatedCppCompiles(@TempDir Path dir) throws IOException, InterruptedException {
        assertCompiles(CppCompilesTest.GRAMMAR, dir);
    }

    @Test
    void generatedCppCompilesWithTokenManagerTrace(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertCompiles(CppCompilesTest.GRAMMAR_DEBUG, dir);
    }

    @Test
    void generatedCppCompilesWithAnAst(@TempDir Path dir) throws IOException, InterruptedException {
        assertCompiles(CppCompilesTest.GRAMMAR_AST, dir);
    }

    @Test
    void choiceWithAnEmptyAlternativeCompiles(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertCompiles(GeneratedCodeCompilesTest.EMPTY_ALTERNATIVE, dir);
    }

    /** C++ compiled the missing entry as an empty image, {@code tokenImage_N[] = {0}}. */
    @Test
    void unlabelledTokenHasATokenImage(@TempDir Path dir) throws IOException, InterruptedException {
        assertCompiles(GeneratedCodeCompilesTest.UNLABELLED_TOKEN, dir);

        var constants = Files.readString(dir.resolve("cpp").resolve("UnlabelledConstants.h"));
        assertEquals(false, constants.matches("(?s).*tokenImage_\\d+\\[\\] = \\{0\\};.*"),
                "a token has an empty image:\n" + constants);
    }

    @Test
    void nodesWithoutNodeMultiCompile(@TempDir Path dir) throws IOException, InterruptedException {
        assertCompiles(GeneratedCodeCompilesTest.NODES_WITHOUT_NODE_MULTI, dir);
    }

    /**
     * Skipped single characters on both sides of 64 are tested with two bit masks, and the lower one
     * was printed without its {@code 0x}: C++ read it as a decimal number and skipped other
     * characters than the grammar said.
     */
    @Test
    void theSkipMasksAreHexadecimal(@TempDir Path dir) throws IOException, InterruptedException {
        assertCompiles("""
                grammar Skip;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <WORD> )* <EOF> ;

                SKIP = " " | "~" ;

                TOKEN = < WORD: (["a"-"z"])+ > ;
                """, dir);

        String lexer;
        try (Stream<Path> paths = Files.walk(dir.resolve("cpp"))) {
            lexer = paths.filter(p -> p.toString().endsWith(".cc")).map(p -> {
                try {
                    return Files.readString(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).filter(text -> text.contains("while ((curChar < 64 && (")).findFirst()
                    .orElseThrow(() -> new AssertionError("no two-mask skip loop was generated"));
        }
        assertTrue(lexer.contains("while ((curChar < 64 && (0x100000000ULL & (1L << curChar))"),
                lexer.lines().filter(l -> l.contains("curChar < 64")).toList().toString());
    }

    /**
     * Compiles every generated {@code .cc} and links them into one shared library that may not
     * leave a symbol undefined. {@code -fsyntax-only} alone passed code that declared a member,
     * called it and never defined it.
     */
    static void assertCompiles(String grammar, Path dir)
            throws IOException, InterruptedException {
        assumeTrue(CppCompilesTest.hasCompiler(), "no C++ compiler on PATH");

        var source = dir.resolve("Grammar.waggle");
        Files.writeString(source, grammar);

        var target = dir.resolve("cpp");
        new ParserBuilder()
                .setLanguage(Language.CPP)
                .setParserFile(source.toFile())
                .setTargetDir(target.toFile())
                .build().parse();

        List<String> sources;
        try (Stream<Path> paths = Files.walk(target)) {
            sources = paths.filter(p -> p.toString().endsWith(".cc")).sorted()
                    .map(p -> target.relativize(p).toString()).toList();
        }
        assertEquals(false, sources.isEmpty(), "no C++ was generated");

        var command = new ArrayList<>(List.of("g++", "-std=c++17", "-shared", "-fPIC",
                "-Wl,--no-undefined", "-o", "libgrammar.so"));
        command.addAll(sources);
        var process = new ProcessBuilder(command)
                .directory(target.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), "the generated C++ does not compile or link:\n" + output);
    }

    private static boolean hasCompiler() {
        try {
            return new ProcessBuilder("g++", "--version").start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
