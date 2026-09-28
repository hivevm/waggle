// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.api.Language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a generated Java lexer on input, where the other tests only compile it or read its source.
 * {@code LexerInterpreter} cannot stand in for this: it builds the NFA with NO_DFA, so it never
 * reaches the string-literal DFA and the hand-over from it to the NFA.
 */
class GeneratedLexerTest {

    /**
     * "a" is a prefix of the literal "ab" and starts both D and E, so after "a" the string-literal
     * DFA gives up and the NFA must resume in the composite state set {D, E}. That set was never
     * registered in stage 4: generation failed with an ArrayIndexOutOfBoundsException.
     */
    private static final String STOP_STATE_SET = """
            grammar Stop;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input = ( <A> | <D> | <E> )* <EOF> ;

            SKIP = " " ;

            TOKEN =
              < A: "ab" >
            | < D: ["a"-"z"] (["a"-"z"])* >
            | < E: "a" (["0"-"9"])+ >
            ;
            """;

    @Test
    void theNfaResumesInEveryStateTheLiteralDfaLeftOpen(@TempDir Path dir) throws Exception {
        var lexer = compile(dir, "Stop.waggle", STOP_STATE_SET);
        assertEquals(List.of("\"ab\":ab", "<E>:a5", "<D>:ax", "<E>:a12", "<D>:a"),
                lexer.tokens("ab a5 ax a12 a"));
    }

    /**
     * With one more token the same grammar used to generate, but {@code jjStopStringLiteralDfa}
     * returned a single member of the set instead of the set, and "a5" lexed as D and an error.
     */
    @Test
    void theNfaKeepsAllBranchesOfTheSetItResumesIn(@TempDir Path dir) throws Exception {
        var lexer = compile(dir, "Stop.waggle", STOP_STATE_SET
                .replace("( <A> | <D> | <E> )*", "( <A> | <D> | <E> | <F> )*")
                .replace("| < E: \"a\" ([\"0\"-\"9\"])+ >",
                        "| < E: \"a\" ([\"0\"-\"9\"])+ >\n| < F: \"a\" \"-\" >"));
        assertEquals(List.of("\"ab\":ab", "<E>:a5", "<D>:ax", "<F>:a-", "<E>:a12"),
                lexer.tokens("ab a5 ax a- a12"));
    }

    /** The same hand-over for operators: "-" is a prefix of the literal "->". */
    @Test
    void anOperatorSharingAPrefixWithALiteralIsMatchedWhole(@TempDir Path dir) throws Exception {
        var lexer = compile(dir, "Ops.waggle", """
                grammar Ops;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <ARROW> | <OP> )* <EOF> ;

                SKIP = " " ;

                TOKEN =
                  < ARROW: "->" >
                | < OP: "-" | "--" | "-=" | "=" | "==" >
                ;
                """);
        assertEquals(List.of("\"->\":->", "<OP>:-=", "<OP>:--", "<OP>:-", "<OP>:=="),
                lexer.tokens("-> -= -- - =="));
    }

    /**
     * After "-" the literal DFA hands over to the NFA in the set {T1, T3}, and that set was named
     * after the T3 state that the start state also reaches on its own. The move code of the set
     * then ran for that state too: "c--" lexed as T3, which cannot start with "c".
     */
    @Test
    void aStopSetIsNotNamedAfterAStateReachedOnItsOwn(@TempDir Path dir) throws Exception {
        var lexer = compile(dir, "Named.waggle", """
                grammar Named;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <T1> | <T2> | <T3> )* <EOF> ;

                SKIP = " " ;

                TOKEN = < T1: ~[" "] "-" "->" > | < T2: "->" > | < T3: ("-")+ > ;
                """);
        assertEquals(List.of("error"), lexer.tokens("c--"));
        assertEquals(List.of("<T3>:--", "\"->\":->", "<T1>:c-->"), lexer.tokens("-- -> c-->"));
    }

    /**
     * With case ignored, a range that starts inside a block of letters, not at its first letter,
     * got no other case: ["B"-"Z"] did not match "c".
     */
    @Test
    void anIgnoredCaseFoldsARangeStartingInsideABlock(@TempDir Path dir) throws Exception {
        var lexer = compile(dir, "Range.waggle", """
                grammar Range;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <UP> | <LOW> )* <EOF> ;

                SKIP = " " ;

                TOKEN [IGNORE_CASE] = < UP: "#" ["B"-"Z"] > | < LOW: "@" ["b"-"y"] > ;
                """);
        assertEquals(List.of("<UP>:#c", "<UP>:#Z", "<LOW>:@C", "<LOW>:@y"), lexer.tokens("#c #Z @C @y"));
        assertEquals(List.of("error"), lexer.tokens("#a"));
        assertEquals(List.of("error"), lexer.tokens("@Z"));
    }

    /**
     * A negated list in a choice, with case ignored: the lists of a choice are merged into one,
     * and the negation used to be removed before the case was folded, so folding the complement
     * put the excluded "a" back in as the other case of "A".
     */
    @Test
    void anIgnoredCaseDoesNotUndoANegation(@TempDir Path dir) throws Exception {
        var lexer = compile(dir, "Neg.waggle", """
                grammar Neg;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <X> )* <EOF> ;

                TOKEN [IGNORE_CASE] = < X: ( "xy" | ~["a"] ) > ;
                """);
        assertEquals(List.of("<X>:b", "<X>:XY", "error"), lexer.tokens("bXYa"));
        assertEquals(List.of("error"), lexer.tokens("A"));
    }

    /**
     * A private list used by a case-sensitive and a case-insensitive token. Building the automaton
     * rewrote the list itself, so whichever token came first decided the case for both.
     */
    @Test
    void aSharedListKeepsTheCaseOfEachToken(@TempDir Path dir) throws Exception {
        for (var order : List.of(List.of("SENSITIVE", "IGNORED"), List.of("IGNORED", "SENSITIVE"))) {
            var productions = order.stream().map(p -> p.equals("SENSITIVE")
                    ? "TOKEN = < X: <L> \"1\" > ;"
                    : "TOKEN [IGNORE_CASE] = < Y: <L> \"2\" > ;").toList();
            var lexer = compile(dir.resolve(String.join("-", order)), "Shared.waggle", """
                    grammar Shared;

                    options {
                      JAVA_PACKAGE: "org.example"
                    }

                    Input = ( <X> | <Y> )* <EOF> ;

                    TOKEN = < #L: ["a"-"c"] > ;
                    %s
                    %s
                    """.formatted(productions.get(0), productions.get(1)));

            assertEquals(List.of("<X>:a1", "<Y>:A2", "<Y>:b2"), lexer.tokens("a1A2b2"), order.toString());
            assertEquals(List.of("error"), lexer.tokens("A1"), order.toString());
        }
    }

    /**
     * A token many times longer than the char stream's buffer: a comment accumulated with MORE keeps
     * the start of the token fixed, so the buffer has to grow several times, each time carrying the
     * token so far over. It grew by a fixed step, which made this quadratic.
     */
    @Test
    void aTokenLongerThanTheBufferIsReadWhole(@TempDir Path dir) throws Exception {
        var lexer = compile(dir, "Long.waggle", GeneratedLexerTest.LONG_COMMENT);
        var body = "x".repeat(100_000);
        assertEquals(List.of("<WORD>:ab", "\"*/\":/*" + body + "*/", "<WORD>:cd"),
                lexer.tokens("ab /*" + body + "*/ cd"));
    }

    /** A comment read with MORE, between words. */
    static final String LONG_COMMENT = """
            grammar Long;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input = ( <WORD> | <COMMENT> )* <EOF> ;

            SKIP = " " ;

            MORE = "/*" : IN_COMMENT ;
            TOKEN <IN_COMMENT> = < COMMENT: "*/" > : DEFAULT ;
            MORE <IN_COMMENT> = < ~[] > ;

            TOKEN = < WORD: (["a"-"z"])+ > ;
            """;

    /** A compiled lexer, loaded in its own class loader. */
    private record GeneratedLexer(Constructor<?> lexer, Constructor<?> stream,
                                  Constructor<?> provider, Method next, String[] images) {

        /**
         * The tokens up to end of input, each as {@code tokenImage:image}, and {@code error} for a
         * lexical error, which ends the list.
         */
        List<String> tokens(String input) throws Exception {
            var tokens = new ArrayList<String>();
            var manager = this.lexer.newInstance(this.stream.newInstance(this.provider.newInstance(input)));
            while (true) {
                Object token;
                try {
                    token = this.next.invoke(manager);
                } catch (InvocationTargetException e) {
                    if (!e.getCause().getClass().getSimpleName().equals("TokenException")) {
                        throw e;
                    }
                    tokens.add("error");
                    return tokens;
                }
                int kind = token.getClass().getField("kind").getInt(token);
                if (kind == 0) {
                    return tokens;
                }
                tokens.add(this.images[kind] + ":" + token.getClass().getField("image").get(token));
            }
        }
    }

    private static GeneratedLexer compile(Path dir, String name, String grammar) throws Exception {
        var target = GeneratedSources.generate(dir.resolve(name), grammar, Language.JAVA,
                dir.resolve("generated"));
        var loader = GeneratedSources.javac(target, dir.resolve("classes"));
        var lexer = loader.loadClass("org.example.Lexer");
        var stream = loader.loadClass("org.example.JavaCharStream");
        var provider = loader.loadClass("org.example.StringProvider");
        var lexerCtor = lexer.getConstructor(stream);
        var streamCtor = stream.getConstructor(loader.loadClass("org.example.Provider"));
        var providerCtor = provider.getConstructor(String.class);
        lexerCtor.setAccessible(true);
        streamCtor.setAccessible(true);
        var next = lexer.getMethod("getNextToken");
        next.setAccessible(true);
        var images = (String[]) loader.loadClass("org.example.ParserConstants")
                .getField("tokenImage").get(null);
        return new GeneratedLexer(lexerCtor, streamCtor, providerCtor, next, images);
    }
}
