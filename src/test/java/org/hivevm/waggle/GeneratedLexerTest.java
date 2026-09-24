// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.api.ParserBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        Files.createDirectories(dir);
        var source = dir.resolve(name);
        Files.writeString(source, grammar);

        var target = dir.resolve("generated");
        new ParserBuilder().setLanguage(Language.JAVA).setTargetDir(target.toFile())
                .setParserFile(source.toFile()).build().parse();

        List<File> sources;
        try (Stream<Path> paths = Files.walk(target)) {
            sources = paths.filter(p -> p.toString().endsWith(".java")).map(Path::toFile)
                    .collect(Collectors.toList());
        }

        var classes = Files.createDirectories(dir.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            var ok = compiler.getTask(null, files, diagnostics, List.of("-d", classes.toString()),
                    null, files.getJavaFileObjectsFromFiles(sources)).call();
            assertTrue(ok, "the generated code does not compile:\n" + diagnostics.getDiagnostics());
        }

        var loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()},
                GeneratedLexerTest.class.getClassLoader());
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
