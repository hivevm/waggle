package org.hivevm.cc;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static void assertCompiles(String grammar, Path dir)
            throws IOException, InterruptedException {
        assumeTrue(CppCompilesTest.hasCompiler(), "no C++ compiler on PATH");

        var source = dir.resolve("Grammar.jj");
        Files.writeString(source, grammar);

        var target = dir.resolve("cpp");
        new ParserBuilder()
                .setLanguage(Language.CPP)
                .setParserFile(source.toFile())
                .setTargetDir(target.toFile())
                .build().parse();

        List<Path> sources;
        try (Stream<Path> paths = Files.walk(target)) {
            sources = paths.filter(p -> p.toString().endsWith(".cc")).sorted().toList();
        }
        assertEquals(false, sources.isEmpty(), "no C++ was generated");

        var failures = new ArrayList<String>();
        for (var file : sources) {
            var command = List.of("g++", "-fsyntax-only", "-std=c++17", file.getFileName().toString());
            var process = new ProcessBuilder(command)
                    .directory(target.toFile())
                    .redirectErrorStream(true)
                    .start();
            var output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0) {
                failures.add(file.getFileName() + ":\n" + output);
            }
        }

        assertEquals(List.of(), failures, "the generated C++ does not compile");
    }

    private static boolean hasCompiler() {
        try {
            return new ProcessBuilder("g++", "--version").start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
