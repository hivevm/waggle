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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

    /**
     * VISITOR_DATA_TYPE "Object", Java's word for "anything". The node classes took it as
     * {@code void *}, but the visitor was given {@code Object}, which C++ does not know.
     */
    @Test
    void anObjectVisitorArgumentCompiles(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertCompiles(CppCompilesTest.GRAMMAR_AST.replace("  VISITOR: true",
                "  VISITOR: true,\n  VISITOR_DATA_TYPE: \"Object\""), dir);
    }

    /**
     * An exception thrown inside a production reaches the caller with DEBUG_PARSER on too. The
     * trace wrapped every production in a {@code catch (...)} that swallowed it.
     */
    @Test
    void theParserTraceLetsExceptionsThrough(@TempDir Path dir)
            throws IOException, InterruptedException {
        var output = run("""
                grammar Thrower;

                options {
                  JAVA_PACKAGE: "org.example",
                  DEBUG_PARSER: true
                }

                Input = <A> <? throw std::runtime_error("from the action"); ?> <EOF> ;

                TOKEN = < A: "a" > ;
                """, dir, """
                #include <iostream>
                #include <stdexcept>
                #include "Thrower.h"
                #include "ThrowerTokenManager.h"
                #include "StringReader.h"

                int main() {
                    StringReader reader(JJString("a"));
                    ThrowerTokenManager lexer(&reader);
                    Thrower parser(&lexer);
                    parser.disable_tracing();
                    try {
                        parser.Input();
                        std::cout << "no error";
                    } catch (std::runtime_error& e) {
                        std::cout << e.what();
                    }
                }
                """);
        assertEquals("from the action", output);
    }

    /** BUILD_NODE_FILES off: the author writes the node classes, as the Java back end honours. */
    @Test
    void nodeFilesAreLeftOutWhenNotToBeBuilt(@TempDir Path dir) throws IOException {
        var source = dir.resolve("Grammar.waggle");
        Files.writeString(source, CppCompilesTest.GRAMMAR_AST.replace("  VISITOR: true",
                "  VISITOR: true,\n  BUILD_NODE_FILES: false"));
        var target = dir.resolve("cpp");
        new ParserBuilder().setLanguage(Language.CPP).setParserFile(source.toFile())
                .setTargetDir(target.toFile()).build().parse();

        assertTrue(Files.isRegularFile(target.resolve("Ast.cc")), "no parser was generated");
        assertTrue(!Files.exists(target.resolve("ASTAdd.h")), "a node class was generated");
        assertTrue(!Files.exists(target.resolve("ASTAdd.cc")), "a node class was generated");
    }

    /**
     * Parses into a tree. Without NODE_SCOPE_HOOK the parser declared the scope hooks pure virtual
     * all the same, so it could not be instantiated; only compiling its sources did not show that.
     */
    @Test
    void aParserWithAnAstBuildsTheTree(@TempDir Path dir) throws IOException, InterruptedException {
        var output = run(CppCompilesTest.GRAMMAR_AST, dir, """
                #include <iostream>
                #include "Ast.h"
                #include "AstTokenManager.h"
                #include "StringReader.h"

                struct Probe : Ast {
                    using Ast::Ast;
                    Node* root() { return jjtree.rootNode(); }
                };

                void print(const Node* node) {
                    std::cout << node->getId() << "(";
                    for (int i = 0; i < node->jjtGetNumChildren(); i++)
                        print(node->jjtGetChild(i));
                    std::cout << ")";
                }

                int main() {
                    StringReader reader(JJString("1 + (2 + 3)"));
                    AstTokenManager lexer(&reader);
                    Probe parser(&lexer);
                    parser.Input();
                    print(parser.root());
                }
                """);
        assertEquals("0(1(2()1(2()2())))", output);
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
        assertTrue(lexer.contains("while ((curChar < 64 && (0x100000000ULL & (1ULL << curChar))"),
                lexer.lines().filter(l -> l.contains("curChar < 64")).toList().toString());
    }

    /**
     * SwitchTo with a state that does not exist. It threw a pointer, which no
     * {@code catch (TokenManagerError&)} catches, appended the state as one character with that
     * code instead of its number, and the error did not keep the message it was given.
     */
    @Test
    void anInvalidLexicalStateIsReportedWithItsNumber(@TempDir Path dir)
            throws IOException, InterruptedException {
        var output = run(RUN, dir, """
                #include <iostream>
                #include "RunTokenManager.h"
                #include "StringReader.h"
                #include "TokenManagerError.h"

                // SwitchTo is protected: lexical actions call it.
                struct Probe : RunTokenManager {
                    using RunTokenManager::RunTokenManager;
                    void switchTo(int state) { SwitchTo(state); }
                };

                int main() {
                    StringReader reader(JJString("ab"));
                    Probe lexer(&reader);
                    try {
                        lexer.switchTo(42);
                        std::cout << "no error";
                    } catch (TokenManagerError& e) {
                        std::cout << e.getMessage();
                    }
                }
                """);
        assertEquals("Error: Ignoring invalid lexical state : 42. State unchanged.", output);
    }

    /**
     * Two lexical states that switch to each other on an empty match. The check that catches this
     * printed a string literal that did not compile, and would only have logged and gone on looping.
     */
    @Test
    void aLoopOfEmptyMatchesThrows(@TempDir Path dir) throws IOException, InterruptedException {
        var output = run("""
                grammar Loop;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <A> )* <EOF> ;

                TOKEN = < A: "a" > ;

                TOKEN = < E1: ("x")? > : S2 ;

                TOKEN <S2> = < E2: ("y")? > : DEFAULT ;
                """, dir, """
                #include <iostream>
                #include "LoopTokenManager.h"
                #include "StringReader.h"
                #include "TokenManagerError.h"

                int main() {
                    StringReader reader(JJString("az"));
                    LoopTokenManager lexer(&reader);
                    try {
                        for (Token* t = lexer.getNextToken(); t->kind() != 0; t = lexer.getNextToken()) {
                        }
                        std::cout << "no error";
                    } catch (TokenManagerError& e) {
                        std::cout << e.errorCode << ": " << e.getMessage();
                    }
                }
                """);
        assertTrue(output.startsWith("3: Error: Bailing out of infinite loop"), output);
    }

    /**
     * UTF-8 input whose tokens start with a character beyond ASCII. The NFA reads decoded code
     * points, but the reader handed it the first character of a token as a raw, sign-extended byte.
     */
    @Test
    void aTokenMayStartBeyondAscii(@TempDir Path dir) throws IOException, InterruptedException {
        var output = run(RUN, dir, """
                #include <iostream>
                #include "RunTokenManager.h"
                #include "StringReader.h"

                int main() {
                    StringReader reader(JJString("\\xc3\\xa4" "b a\\xc3\\xa4 ab"));
                    RunTokenManager lexer(&reader);
                    for (Token* t = lexer.getNextToken(); t->kind() != 0; t = lexer.getNextToken()) {
                        std::cout << t->kind() << ":" << t->image() << ";";
                    }
                }
                """);
        assertEquals("2:\u00e4b;2:a\u00e4;2:ab;", output);
    }

    /**
     * More than 64 token kinds, where the first 64 are all one character long and only later kinds
     * are longer: {@code jjMoveStringLiteralDfa1} then takes no {@code active0}. The header
     * separated its parameters by index rather than by whether one had been printed yet, and
     * declared it with a leading comma.
     */
    @Test
    void literalsBeyondTheFirst64KindsAreDeclaredInTheHeader(@TempDir Path dir)
            throws IOException, InterruptedException {
        var tokens = new StringBuilder();
        for (int i = 0; i < 63; i++) {
            char c = (i < 26) ? (char) ('A' + i) : (i < 52) ? (char) ('a' + i - 26) : (char) ('0' + i - 52);
            tokens.append("< T").append(i).append(": \"").append(c).append("\" > | ");
        }
        var output = run("""
                grammar Kinds;

                Input = ( <T0> | <BEGIN> | <END> )* <EOF> ;

                SKIP = " " ;

                TOKEN = %s< BEGIN: "begin" > | < END: "end" > ;
                """.formatted(tokens), dir, """
                #include <iostream>
                #include "KindsTokenManager.h"
                #include "StringReader.h"

                int main() {
                    StringReader reader(JJString("A begin 9 end"));
                    KindsTokenManager lexer(&reader);
                    for (Token* t = lexer.getNextToken(); t->kind() != 0; t = lexer.getNextToken()) {
                        std::cout << t->kind() << ":" << t->image() << ";";
                    }
                }
                """);
        assertEquals("2:A;65:begin;63:9;66:end;", output);
    }

    /**
     * A token many times longer than the reader's buffer, which has to grow several times while the
     * start of the token stays fixed. See {@link GeneratedLexerTest#aTokenLongerThanTheBufferIsReadWhole}.
     */
    @Test
    void aTokenLongerThanTheBufferIsReadWhole(@TempDir Path dir)
            throws IOException, InterruptedException {
        var output = run(GeneratedLexerTest.LONG_COMMENT, dir, """
                #include <iostream>
                #include "LongTokenManager.h"
                #include "StringReader.h"

                int main() {
                    StringReader reader(JJString("ab /*") + JJString(100000, 'x') + JJString("*/ cd"));
                    LongTokenManager lexer(&reader);
                    for (Token* t = lexer.getNextToken(); t->kind() != 0; t = lexer.getNextToken()) {
                        std::cout << t->kind() << ":" << t->image().size() << ":"
                                  << t->image().substr(0, 3) << t->image().substr(t->image().size() - 2) << ";";
                    }
                }
                """);
        assertEquals("5:2:abab;3:100004:/*x*/;5:2:cdcd;", output);
    }

    /**
     * Backing up over characters beyond ASCII: "a\u00e4c" starts "a\u00e4b", which fails at
     * "c", so the lexer takes "a" and backs up over "\u00e4c". The reader backed up by bytes while
     * the lexer counts characters, and so stopped inside the "\u00e4". Written once with literals,
     * which the string-literal DFA matches, and once with lists, which only the NFA sees. The
     * literals' images did not even compile: they were UTF-16 code units in a char array.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "< LITERAL: \"a\u00e4b\" > | < UMLAUT: \"\u00e4\" >",
            "< LITERAL: \"a\" [\"\u00e4\"] \"b\" > | < UMLAUT: [\"\u00e4\"] >"})
    void theLexerBacksUpOverCharactersBeyondAscii(String tokens, @TempDir Path dir)
            throws IOException, InterruptedException {
        var output = run("""
                grammar Run;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <WORD> | <UMLAUT> | <LITERAL> )* <EOF> ;

                SKIP = " " ;

                TOKEN = %s | < WORD: (["a"-"z"])+ > ;
                """.formatted(tokens), dir, """
                #include <iostream>
                #include "RunTokenManager.h"
                #include "StringReader.h"

                int main() {
                    StringReader reader(JJString("a\\xc3\\xa4" "b a\\xc3\\xa4" "c"));
                    RunTokenManager lexer(&reader);
                    for (Token* t = lexer.getNextToken(); t->kind() != 0; t = lexer.getNextToken()) {
                        std::cout << t->kind() << ":" << t->image() << ";";
                    }
                }
                """);
        assertEquals("2:a\u00e4b;4:a;3:\u00e4;4:c;", output);
    }

    /**
     * A lexical error in a grammar with MORE. The error is reported after the loop that
     * accumulates MORE, and nothing left that loop when nothing matched: the lexer went round again
     * on the same character for ever, and at the end of the input inside a MORE as well. The
     * handler did not count the error either, so a caller could not tell that there was one.
     */
    @ParameterizedTest
    @ValueSource(strings = {"a1 b", "a /* b"})
    void aLexicalErrorEndsWithMore(String input, @TempDir Path dir)
            throws IOException, InterruptedException {
        var output = run("""
                grammar Run;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <WORD> )* <EOF> ;

                SKIP = " " ;
                MORE = "/*" : IN_COMMENT ;
                SKIP <IN_COMMENT> = "*/" : DEFAULT ;
                MORE <IN_COMMENT> = < ~[] > ;

                TOKEN = < WORD: (["a"-"z"])+ > ;
                """, dir, """
                #include <iostream>
                #include "RunTokenManager.h"
                #include "StringReader.h"

                int main() {
                    StringReader reader(JJString("%s"));
                    RunTokenManager lexer(&reader);
                    std::cout.setstate(std::ios::failbit); // the handler prints the error
                    std::string tokens;
                    for (Token* t = lexer.getNextToken(); t->kind() != 0; t = lexer.getNextToken()) {
                        tokens += t->image() + ";";
                    }
                    std::cout.clear();
                    std::cout << tokens << "errors=" << lexer.getErrorHandler()->getErrorCount();
                }
                """.formatted(input));
        assertEquals(input.equals("a1 b") ? "a;b;errors=1" : "a;errors=1", output);
    }

    /**
     * The image of &lt;EOF&gt; is empty, as in Java. The lexer took an empty literal image for
     * "no literal" and read it from the input: the text of the token before, or with empty input
     * the whole uninitialised buffer.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "ab"})
    void theEndOfInputHasAnEmptyImage(String input, @TempDir Path dir)
            throws IOException, InterruptedException {
        var output = run(RUN, dir, """
                #include <iostream>
                #include "RunTokenManager.h"
                #include "StringReader.h"

                int main() {
                    StringReader reader(JJString("%s"));
                    RunTokenManager lexer(&reader);
                    Token* t = lexer.getNextToken();
                    while (t->kind() != 0) {
                        t = lexer.getNextToken();
                    }
                    std::cout << "[" << t->image() << "]";
                }
                """.formatted(input));
        assertEquals("[]", output);
    }

    /**
     * Input the automaton cannot represent: a code point beyond U+FFFF indexed its tables past
     * their end, and a sequence cut off by the end of the input read stale bytes. Both are read
     * as U+FFFD, which the list matches; the image keeps the bytes.
     */
    @ParameterizedTest
    @ValueSource(strings = {"a\\xf0\\x9f\\x98\\x80", "a\\xc3", "a\\xc3\" \"b", "\\x80\" \"a"})
    void inputBeyondTheAutomatonIsReadAsAReplacementCharacter(String input, @TempDir Path dir)
            throws IOException, InterruptedException {
        var output = run("""
                grammar Run;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <WORD> )* <EOF> ;

                SKIP = " " ;

                TOKEN = < WORD: (["a"-"z", "\u0100"-"\uffff"])+ > ;
                """, dir, """
                #include <iostream>
                #include "RunTokenManager.h"
                #include "StringReader.h"

                int main() {
                    const std::string input("%s");
                    StringReader reader(input);
                    RunTokenManager lexer(&reader);
                    int count = 0;
                    for (Token* t = lexer.getNextToken(); t->kind() != 0; t = lexer.getNextToken()) {
                        count++;
                        std::cout << (t->image() == input ? "whole" : "part") << ";";
                    }
                    std::cout << count;
                }
                """.formatted(input));
        assertEquals("whole;1", output);
    }

    /**
     * A lexical state and a node whose names reach beyond ASCII. Their names were written as UTF-16
     * code units into a char array, which does not compile beyond ASCII; the literal images had
     * already been moved to UTF-8.
     */
    @Test
    void namesBeyondAsciiCompile(@TempDir Path dir) throws Exception {
        assertCompiles("""
                grammar Names;

                options {
                  USE_AST: true,
                  JAVA_PACKAGE: "org.example"
                }

                Input #Größe = ( <WORD> )* <EOF> ;

                SKIP = " " ;

                MORE = "/*" : IN_ÄRGER ;
                SKIP <IN_ÄRGER> = "*/" : DEFAULT ;
                MORE <IN_ÄRGER> = < ~[] > ;

                TOKEN = < WORD: (["a"-"z"])+ > ;
                """, dir);
    }

    @ParameterizedTest
    @ValueSource(strings = {GeneratedCodeCompilesTest.SKIP_UPPER, GeneratedCodeCompilesTest.SKIP_BOTH})
    void skippingAboveAsciiHalfCompiles(String skip, @TempDir Path dir) throws Exception {
        for (var trace : List.of(false, true)) {
            var sub = dir.resolve(String.valueOf(trace));
            Files.createDirectories(sub);
            assertCompiles(GeneratedCodeCompilesTest.skipping(skip, trace), sub);
        }
    }

    /** A grammar to run: its tokens reach beyond ASCII. */
    private static final String RUN = """
            grammar Run;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input = ( <WORD> )* <EOF> ;

            SKIP = " " ;

            TOKEN = < WORD: (["a"-"z", "ä"])+ > ;
            """;

    /**
     * Generates {@code grammar} as C++, links it with {@code main} into a program, runs it and
     * returns what it printed.
     */
    static String run(String grammar, Path dir, String main)
            throws IOException, InterruptedException {
        assumeTrue(CppCompilesTest.hasCompiler(), "no C++ compiler on PATH");
        assertCompiles(grammar, dir);

        var target = dir.resolve("cpp");
        Files.writeString(target.resolve("main.cpp"), main);
        List<String> sources;
        try (Stream<Path> paths = Files.walk(target)) {
            sources = paths.filter(p -> p.toString().endsWith(".cc")).sorted()
                    .map(p -> target.relativize(p).toString()).toList();
        }
        var command = new ArrayList<>(List.of("g++", "-std=c++17", "-g", "-o", "program", "main.cpp"));
        command.addAll(CppCompilesTest.sanitizers());
        command.addAll(sources);
        var build = new ProcessBuilder(command).directory(target.toFile())
                .redirectErrorStream(true).start();
        var buildOutput = new String(build.getInputStream().readAllBytes());
        assertEquals(0, build.waitFor(), "the program does not build:\n" + buildOutput);

        // Into a file, not a pipe read to its end: a lexer that loops would block the read forever.
        var log = target.resolve("program.out");
        var builder = new ProcessBuilder(target.resolve("program").toString())
                .directory(target.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        // The tokens a TokenManager returns belong to the caller, and these mains do not free them.
        builder.environment().put("ASAN_OPTIONS", "detect_leaks=0");
        var program = builder.start();
        var finished = program.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            program.destroyForcibly().waitFor();
        }
        var output = Files.readString(log, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(finished, "the program did not end:\n" + output);
        assertEquals(0, program.exitValue(), "the program failed:\n" + output);
        return output;
    }

    /**
     * AddressSanitizer and UndefinedBehaviorSanitizer, when the compiler can link them: reading past
     * a table or a buffer is what the lexer did with input beyond ASCII, and that printed plausible
     * output before. Every finding ends the program with a non-zero status.
     */
    private static List<String> sanitizers() throws IOException, InterruptedException {
        if (CppCompilesTest.sanitizers == null) {
            var probe = Files.createTempFile("sanitizers", ".cpp");
            try {
                Files.writeString(probe, "int main() { return 0; }\n");
                var flags = List.of("-fsanitize=address,undefined", "-fno-sanitize-recover=all");
                var command = new ArrayList<>(List.of("g++", "-o", "/dev/null", probe.toString()));
                command.addAll(flags);
                var process = new ProcessBuilder(command).redirectErrorStream(true).start();
                process.getInputStream().readAllBytes();
                CppCompilesTest.sanitizers = (process.waitFor() == 0) ? flags : List.of();
            } finally {
                Files.delete(probe);
            }
        }
        return CppCompilesTest.sanitizers;
    }

    private static List<String> sanitizers;

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
