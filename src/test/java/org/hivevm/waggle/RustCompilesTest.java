// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Regression tests: the generated Rust must actually <em>compile</em>.
 *
 * <p>It never did. The lexer emitted Java — {@code switch}/{@code case} instead of {@code match},
 * {@code try}/{@code catch} around a {@code Result}, a {@code private final int} signature in the
 * middle of a {@code .rs} file — and where it did emit Rust it emitted broken Rust: braceless
 * {@code if}s, arms that were opened but never closed, {@code use crate::::charstream} because
 * RUST_MODULE defaulted to the empty string, a bit vector declared {@code [u64; 3]} and filled with
 * four values, and {@code -1} as a state index in a {@code usize}.
 *
 */
class RustCompilesTest {

    /** Keywords that are prefixes of an identifier: the string-literal DFA hands over to the NFA. */
    private static final String KEYWORDS = """
            grammar Kw;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input =
              ( Stmt() )* <EOF>
            ;

            Stmt =
              < IF > < ID >
            | < INT > < ID >
            | < ID >
            ;

            SKIP = " " | "\\t" | "\\n" | "\\r" ;

            TOKEN =
              < IF: "if" >
            | < INT: "int" >
            | < ID: ["a"-"z"] ( ["a"-"z","0"-"9","_"] )* >
            ;
            """;

    /** Tokens that reach past ASCII, so the NFA builds composite states and two-level bit vectors. */
    private static final String NON_ASCII = """
            grammar Str;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input =
              ( <STRING> | <FLOAT> | <COMMENT> )* <EOF>
            ;

            SKIP = " " | "\\t" | "\\n" | "\\r" ;

            TOKEN =
              < STRING: "\\"" ( ~["\\"","\\\\"] | "\\\\" ["n","t","r"] )* "\\"" >
            | < FLOAT: (["0"-"9"])+ "." (["0"-"9"])* ( ["e","E"] (["+","-"])? (["0"-"9"])+ )? >
            | < COMMENT: "/*" ( ~["*"] | "*" ~["/"] )* "*/" >
            ;
            """;

    /**
     * SKIP, MORE and TOKEN each with a lexical action. The Rust runtime had no image buffer at all —
     * the generator emitted {@code image.append(input_stream.GetSuffix(...))} against a struct with
     * no such field and a CharStream with no such method — so no grammar with a lexical action could
     * ever produce Rust that compiled. The action bodies themselves are the grammar's own code, so
     * they are Rust here.
     */
    private static final String LEXICAL_ACTIONS = """
            grammar Act;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input =
              ( <WORD> | <NUM> )* <EOF>
            ;

            SKIP =
              " " <? println!("ws"); ?>
            | "\\t"
            | "\\n"
            ;

            MORE = "/*" <? println!("comment starts"); ?> : IN_COMMENT ;

            TOKEN <IN_COMMENT> = < COMMENT: "*/" > <? println!("comment ends"); ?> : DEFAULT ;
            MORE <IN_COMMENT> = < ~[] > ;

            TOKEN =
              < WORD: (["a"-"z"])+ > <? println!("word"); ?>
            | < NUM: (["0"-"9"])+ >
            ;
            """;

    /**
     * Special tokens. Java hands each one a raw reference to its predecessor and lets the predecessor
     * point back through {@code next}; the generator emitted that verbatim, against a Rust
     * {@code Token} whose fields are {@code Option<Rc<RefCell<Token>>>} and against a
     * {@code specialToken} local that was declared a plain {@code Token} and never initialised.
     */
    private static final String SPECIAL_TOKENS = """
            grammar Spec;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input =
              ( <WORD> | <NUM> )* <EOF>
            ;

            SKIP = " " | "\\t" | "\\n" ;

            SPECIAL_TOKEN =
              < LINE_COMMENT: "//" (~["\\n"])* >
            ;

            MORE = "/*" : IN_COMMENT ;
            SPECIAL_TOKEN <IN_COMMENT> = < BLOCK_COMMENT: "*/" > : DEFAULT ;
            MORE <IN_COMMENT> = < ~[] > ;

            TOKEN =
              < WORD: (["a"-"z"])+ >
            | < NUM: (["0"-"9"])+ >
            ;
            """;

    /**
     * The token-manager trace. It was pure Java — {@code debugStream.println}, a
     * {@code const statesForState: [][][]} with no element type, calls to two helpers that did not
     * exist. Rust has no redirectable stream, so the trace goes to stderr.
     */
    private static final String TRACE = RustCompilesTest.KEYWORDS.replace(
            "  JAVA_PACKAGE: \"org.example\"",
            "  JAVA_PACKAGE: \"org.example\",\n  DEBUG_TOKEN_MANAGER: true");

    /**
     * A token that <em>ends</em> on a non-ASCII character. Its NFA state is final and has nowhere
     * left to go, which is the one branch of the non-ASCII dispatch that returns early — and it
     * returned without closing the match arm it had opened. Java and C++ never noticed, because
     * their case labels carry no braces.
     */
    private static final String NON_ASCII_FINAL_STATE = """
            grammar Unit;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input =
              ( <UNIT> | <WORD> )* <EOF>
            ;

            SKIP = " " | "\\t" | "\\n" ;

            TOKEN =
              < UNIT: (["0"-"9"])+ ["\u00b5","\u03a9","\u00b0"] >
            | < WORD: (["a"-"z"] | ["\u00c0"-"\u024f"])+ >
            ;
            """;

    @Test
    void generatedRustCompilesWithANonAsciiFinalState(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertCompiles(RustCompilesTest.NON_ASCII_FINAL_STATE, "unit", dir);
    }

    @Test
    void generatedRustCompilesWithTokenManagerTrace(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertCompiles(RustCompilesTest.TRACE, "kw", dir);
    }

    @Test
    void generatedRustCompilesWithSpecialTokens(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertCompiles(RustCompilesTest.SPECIAL_TOKENS, "spec", dir);
    }

    @Test
    void generatedRustCompilesWithLexicalActions(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertCompiles(RustCompilesTest.LEXICAL_ACTIONS, "act", dir);
    }

    @Test
    void generatedRustCompiles(@TempDir Path dir) throws IOException, InterruptedException {
        assertCompiles(RustCompilesTest.KEYWORDS, "kw", dir);
    }

    @Test
    void generatedRustCompilesWithCompositeNonAsciiStates(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertCompiles(RustCompilesTest.NON_ASCII, "str", dir);
    }

    /**
     * DEPTH_LIMIT is unimplemented for the Rust target: the template has no {@code jj_depth_error}
     * flag and declares {@code jj_depth} as a {@code u32} (so the {@code -1} sentinel cannot
     * compile), and the generator emitted Java {@code throw}/{@code try}/{@code ++} into the Rust
     * output. Rather than produce Rust that cannot compile, generation must fail honestly
     * (SPECIFICATION.md §3: target feature gaps are tracked, not silently produced).
     */
    @Test
    void rejectsDepthLimitForRust(@TempDir Path dir) throws IOException {
        var grammar = RustCompilesTest.KEYWORDS.replace(
                "  JAVA_PACKAGE: \"org.example\"",
                "  JAVA_PACKAGE: \"org.example\",\n  DEPTH_LIMIT: 5");
        var source = dir.resolve("Grammar.waggle");
        Files.writeString(source, grammar);

        var builder = new ParserBuilder()
                .setLanguage(Language.RUST)
                .setParserFile(source.toFile())
                .setTargetDir(dir.resolve("rust").toFile());

        var failure = org.junit.jupiter.api.Assertions.assertThrows(
                org.hivevm.waggle.api.GenerationException.class, () -> builder.build().parse());
        org.junit.jupiter.api.Assertions.assertTrue(
                failure.getMessage() != null && failure.getMessage().contains("DEPTH_LIMIT"),
                "expected a DEPTH_LIMIT-not-supported message, got: " + failure.getMessage());
    }

    /** The token-image table lost the entry of an unlabelled token: {@code [ "<EOF>", , … ]}. */
    @Test
    void unlabelledTokenCompiles(@TempDir Path dir) throws IOException, InterruptedException {
        assertCompiles(GeneratedCodeCompilesTest.UNLABELLED_TOKEN, "unlabelled", dir);
    }

    /**
     * A Rust project that uses "#Name" nodes supplies node.rs, treestate.rs and treeconstants.rs
     * itself. Generation must go on producing its parser — and leave those files alone. A broader
     * rejection of tree building once broke exactly this (the H3QL grammar).
     */
    @Test
    void generatesAGrammarWithNodesForRust(@TempDir Path dir) throws IOException {
        var source = dir.resolve("Grammar.waggle");
        Files.writeString(source, GeneratedCodeCompilesTest.NODES_WITHOUT_NODE_MULTI);
        var target = dir.resolve("rust");

        new ParserBuilder()
                .setLanguage(Language.RUST)
                .setParserFile(source.toFile())
                .setTargetDir(target.toFile())
                .build().parse();

        var module = target.resolve("singlenode");
        org.junit.jupiter.api.Assertions.assertTrue(Files.isRegularFile(module.resolve("parser.rs")),
                "no parser.rs was generated");
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(module.resolve("node.rs")),
                "node.rs belongs to the project and must not be written without NODE_SCOPE_HOOK");
    }

    /**
     * A definite node takes as many children as it says, a "greater than" node only when there are
     * more than that. Rust closed every node on all the children of its scope.
     */
    @Test
    void nodeAritiesAreKept(@TempDir Path dir) throws IOException, InterruptedException {
        assertCompiles("""
                grammar Arity;

                options {
                  JAVA_PACKAGE: "org.example",
                  USE_AST: true,
                  NODE_SCOPE_HOOK: true
                }

                Input() #Root =
                  expr() <EOF>
                ;

                expr =
                  term() ( < PLUS > term() #Add(2) )*
                ;

                term =
                  ( <NUMBER> #Number )+ #Many(>1)
                | < LPAREN > expr() < RPAREN >
                ;

                SKIP = " " ;

                TOKEN =
                  < PLUS: "+" >
                | < LPAREN: "(" >
                | < RPAREN: ")" >
                | < NUMBER: (["0"-"9"])+ >
                ;
                """, "arity", dir);

        var parser = Files.readString(dir.resolve("rust").resolve("arity").resolve("parser.rs"));
        assertTrue(parser.contains("self.jjtree.close_node_scope(&jjtn000, 2);"), parser);
        assertTrue(parser.contains("close_node_scope_bool(&jjtn001, self.jjtree.node_arity() > 1);"),
                parser);
    }

    /**
     * Rust has no templates for node classes or visitors: the ones it had were Java leftovers under
     * names that did not exist, so generation died with "Invalid template name". It must fail with
     * a message that says what is unsupported instead.
     */
    @Test
    void rejectsNodeClassesForRust(@TempDir Path dir) throws IOException {
        assertRejected(dir, "  NODE_MULTI: true", "NODE_MULTI");
    }

    @Test
    void rejectsVisitorForRust(@TempDir Path dir) throws IOException {
        assertRejected(dir, "  VISITOR: true,\n  NODE_SCOPE_HOOK: true", "VISITOR");
    }

    /**
     * Without NODE_SCOPE_HOOK and node classes Rust writes no tree runtime, and the check sat in the
     * code that writes it: VISITOR passed, and NODE_FACTORY and TRACK_TOKENS came out as Java.
     */
    @ParameterizedTest
    @ValueSource(strings = {"VISITOR: true", "NODE_FACTORY: \"Factory\"", "TRACK_TOKENS: true"})
    void rejectsWhatTheRustNodesCannotDo(String option, @TempDir Path dir) throws IOException {
        assertRejected(dir, "  " + option, option.substring(0, option.indexOf(':')));
    }

    private static void assertRejected(Path dir, String options, String expected) throws IOException {
        var grammar = GeneratedCodeCompilesTest.NODES_WITHOUT_NODE_MULTI.replace(
                "  JAVA_PACKAGE: \"org.example\"", "  JAVA_PACKAGE: \"org.example\",\n" + options);
        var source = dir.resolve("Grammar.waggle");
        Files.writeString(source, grammar);

        var builder = new ParserBuilder()
                .setLanguage(Language.RUST)
                .setParserFile(source.toFile())
                .setTargetDir(dir.resolve("rust").toFile());

        var failure = org.junit.jupiter.api.Assertions.assertThrows(
                org.hivevm.waggle.api.GenerationException.class, () -> builder.build().parse());
        org.junit.jupiter.api.Assertions.assertTrue(
                failure.getMessage() != null && failure.getMessage().contains(expected),
                "expected a " + expected + "-not-supported message, got: " + failure.getMessage());
    }

    /** The lexer, the parser and what they depend on go through rustc (ADR-0030). */
    private static void assertCompiles(String grammar, String module, Path dir)
            throws IOException, InterruptedException {
        assumeTrue(RustCompilesTest.hasCompiler(), "no Rust compiler on PATH");

        var source = dir.resolve("Grammar.waggle");
        Files.writeString(source, grammar);

        var target = dir.resolve("rust");
        new ParserBuilder()
                .setLanguage(Language.RUST)
                .setParserFile(source.toFile())
                .setTargetDir(target.toFile())
                .build().parse();

        var modules = new StringBuilder(RustCompilesTest.MODULES);
        for (var tree : List.of("node", "treestate", "treeconstants")) {
            if (Files.exists(target.resolve(module).resolve(tree + ".rs"))) {
                modules.append("pub mod ").append(tree).append(";\n");
            }
        }
        Files.writeString(target.resolve(module).resolve("mod.rs"), modules);
        var root = target.resolve("lib.rs");
        Files.writeString(root, "pub mod " + module + ";\n");

        var process = new ProcessBuilder("rustc", "--edition", "2024", "--crate-type", "lib",
                "--emit=metadata", "lib.rs")
                .directory(target.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes());

        assertEquals(0, process.waitFor(), "the generated Rust does not compile:\n" + output);
    }

    /** The modules of a generated grammar. */
    private static final String MODULES =
            "pub mod token;\npub mod charstream;\npub mod parserconstants;\npub mod lexer;\npub mod parser;\n";

    /** What a generated Rust program printed, and how it ended. */
    record Run(String out, String err, int status) {
    }

    /** Builds the lexer of {@code grammar} with a main() that prints every token and runs it on {@code input}. */
    static Run runLexer(String grammar, String module, Path dir, String input)
            throws IOException, InterruptedException {
        assumeTrue(RustCompilesTest.hasCompiler(), "no Rust compiler on PATH");

        var source = dir.resolve("Grammar.waggle");
        Files.writeString(source, grammar);
        var target = dir.resolve("rust");
        new ParserBuilder()
                .setLanguage(Language.RUST)
                .setParserFile(source.toFile())
                .setTargetDir(target.toFile())
                .build().parse();

        Files.writeString(target.resolve(module.replace("r#", "")).resolve("mod.rs"), RustCompilesTest.MODULES);
        var literal = new StringBuilder();
        input.codePoints().forEach(cp -> literal.append(String.format("\\u{%x}", cp)));
        Files.writeString(target.resolve("main.rs"), """
                #![allow(warnings)]
                mod %s;
                use %s::lexer::Lexer;

                fn main() {
                    let mut lexer = Lexer::new("%s");
                    loop {
                        match lexer.get_next_token() {
                            Ok(t) if t.kind == 0 => break,
                            Ok(t) => print!("{}:{};", t.kind, t.image),
                            Err(error) => {
                                eprintln!("{}", error);
                                std::process::exit(1);
                            }
                        }
                    }
                }
                """.formatted(module, module, literal));

        var build = new ProcessBuilder("rustc", "--edition", "2024", "-o", "program", "main.rs")
                .directory(target.toFile()).redirectErrorStream(true).start();
        var buildOutput = new String(build.getInputStream().readAllBytes());
        assertEquals(0, build.waitFor(), "the generated Rust does not build:\n" + buildOutput);

        // Into files, and bounded in time: a lexer that never reaches the end of its input prints
        // tokens until it is stopped.
        var outFile = target.resolve("out.txt");
        var errFile = target.resolve("err.txt");
        var program = new ProcessBuilder(target.resolve("program").toString())
                .directory(target.toFile())
                .redirectOutput(outFile.toFile()).redirectError(errFile.toFile()).start();
        if (!program.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
            program.destroyForcibly().waitFor();
            return new Run(head(outFile), "did not end within 20 seconds", -1);
        }
        return new Run(head(outFile), head(errFile), program.exitValue());
    }

    private static String head(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            return new String(in.readNBytes(1 << 20), StandardCharsets.UTF_8);
        }
    }

    /** Input the grammar does not match is a lexical error. The Rust lexer returned end of input. */
    @Test
    void aLexicalErrorIsNotTheEndOfInput(@TempDir Path dir) throws IOException, InterruptedException {
        var run = runLexer(WORDS, "words", dir, "ab $cd");
        assertEquals("2:ab;", run.out());
        assertTrue(run.status() != 0 && run.err().contains("Lexical error at line 1, column 4"),
                run.status() + ": " + run.err());
    }

    /** The character stream refills a 4096-character buffer; it used to start over from the top. */
    @Test
    void aLongInputIsReadToTheEnd(@TempDir Path dir) throws IOException, InterruptedException {
        var run = runLexer(WORDS, "words", dir, "ab ".repeat(2000) + "cd");
        assertEquals(0, run.status(), run.err());
        assertEquals("2:ab;".repeat(2000) + "2:cd;", run.out());
    }

    /**
     * A character beyond U+FFFF indexed the tables of the automaton, which know 16-bit characters,
     * past their end: a panic. It is read as U+FFFD, as in C++, and the image keeps it.
     */
    @Test
    void aCharacterBeyondTheBmpIsReadAsAReplacementCharacter(@TempDir Path dir)
            throws IOException, InterruptedException {
        var run = runLexer("""
                grammar Sup;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <WORD> )* <EOF> ;

                SKIP = " " ;

                TOKEN = < WORD: (["a"-"z", "\u0100"-"\uffff"])+ > ;
                """, "sup", dir, "a\uD83D\uDE00b c");
        assertEquals(0, run.status(), run.err());
        assertEquals("2:a\uD83D\uDE00b;2:c;", run.out());
    }

    /**
     * The shared NFA emitter writes Java's "break" to leave a case. In a Rust match arm it left the
     * loop over the whole state set, so a state that did not move dropped the others: "90" lexed as
     * "9" and "0", and "0x1f" not at all.
     */
    @Test
    void everyStateOfTheSetMoves(@TempDir Path dir) throws IOException, InterruptedException {
        var run = runLexer("""
                grammar Num;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <NUM> | <HEX> | <ID> )* <EOF> ;

                SKIP = " " ;

                TOKEN =
                  < NUM: (["0"-"9"])+ ("." (["0"-"9"])*)? >
                | < HEX: "0x" (["0"-"9", "a"-"f"])+ >
                | < ID: (["a"-"z"])+ >
                ;
                """, "num", dir, "90 1.5 0x1f ab");
        assertEquals(0, run.status(), run.err());
        assertEquals("2:90;2:1.5;3:0x1f;4:ab;", run.out());
    }

    /**
     * A lexical state without string literals goes straight to the NFA, through a call that was
     * spelled as in Java (jjMoveNfa_0) from a method taking &self: it did not compile.
     */
    @Test
    void aLexerWithoutLiteralsRuns(@TempDir Path dir) throws IOException, InterruptedException {
        var run = runLexer(WORDS.replace("SKIP = \" \" ;", "SKIP = < [\" \"] > ;"), "words", dir, "ab cd");
        assertEquals(0, run.status(), run.err());
        assertEquals("2:ab;2:cd;", run.out());
    }

    /** Beyond 128 literal kinds the DFA keeps three vectors, and wrote "| let" between them. */
    @Test
    void manyKeywordsRun(@TempDir Path dir) throws IOException, InterruptedException {
        var keywords = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            keywords.append(i == 0 ? "" : " | ").append("< K").append(i).append(": \"k").append(i).append("\" >");
        }
        var run = runLexer("""
                grammar Many;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <ID> )* <EOF> ;

                SKIP = " " ;

                TOKEN = %s | < ID: (["a"-"z", "0"-"9"])+ > ;
                """.formatted(keywords), "many", dir, "k0 k149 k1490 k7");
        assertEquals(0, run.status(), run.err());
        assertEquals("2:k0;151:k149;152:k1490;9:k7;", run.out());
    }

    /**
     * A lexical state that mixes case-sensitive and case-insensitive literals runs the NFA after
     * the literal DFA and reads again what the DFA read ahead. Rust re-read the wrong number of
     * characters and matched "aB", which the Java lexer rejects: B belongs to no token.
     */
    @Test
    void aMixedStateReadsAgainWhatTheDfaReadAhead(@TempDir Path dir)
            throws IOException, InterruptedException {
        var run = runLexer("""
                grammar Mixed;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <ABCD> | <AB> | <ID> | <NUM> )* <EOF> ;

                SKIP = " " ;

                TOKEN = < ABCD: "abcdef" > | < ID: (["a"-"z"])+ ("1")? > | < NUM: (["0"-"9"])+ > ;

                TOKEN [IGNORE_CASE] = < AB: "abc" > ;
                """, "mixed", dir, "aBabc");
        assertTrue(run.status() != 0 && run.err().contains("Lexical error"), run.out() + run.err());
    }

    /**
     * Without KEEP_LINE_COLUMN the token took its image as a {@code &'static str} into a String
     * field, and the lexical error asked the stream for a line it no longer kept.
     */
    @Test
    void aLexerWithoutLinesAndColumnsRuns(@TempDir Path dir) throws IOException, InterruptedException {
        var grammar = WORDS.replace("JAVA_PACKAGE: \"org.example\"",
                "JAVA_PACKAGE: \"org.example\",\n  KEEP_LINE_COLUMN: false");
        var run = runLexer(grammar, "words", dir, "ab cd $");
        assertEquals("2:ab;2:cd;", run.out());
        assertTrue(run.status() != 0 && run.err().contains("Lexical error"), run.status() + ": " + run.err());
    }

    /**
     * An empty match leaves the match position at -1, which is usize::MAX in Rust: adding one to it
     * overflowed, and a debug build panicked.
     */
    @Test
    void anEmptyMatchDoesNotOverflow(@TempDir Path dir) throws IOException, InterruptedException {
        var run = runLexer("""
                grammar Empty;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <WORD> )* <EOF> ;

                SKIP = " " ;

                TOKEN = < AT: "@" > : AFTER ;
                TOKEN <AFTER> = < BS: ("b")* > : DEFAULT ;
                TOKEN = < WORD: (["a"-"z"])+ > ;
                """, "empty", dir, "@ab @bb");
        assertEquals(0, run.status(), run.err());
        assertEquals("2:@;3:;4:ab;2:@;3:bb;", run.out());
    }

    /** Rust keywords as the grammar's and the tokens' names are written as raw identifiers. */
    @Test
    void aKeywordIsARawIdentifier(@TempDir Path dir) throws IOException, InterruptedException {
        var run = runLexer("""
                grammar Match;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <fn> | <type> )* <EOF> ;

                SKIP = " " ;

                TOKEN = < fn: "f" > | < type: (["a"-"z"])+ > ;
                """, "r#match", dir, "f ab");
        assertEquals(0, run.status(), run.err());
        assertEquals("2:f;3:ab;", run.out());
    }

    /**
     * A composite state that moves on a character beyond ASCII was written as Java's bare
     * {@code if (cond)} followed by one statement, which is not Rust: such grammars did not compile.
     */
    @Test
    void aCompositeStateBeyondAsciiCompiles(@TempDir Path dir) throws IOException, InterruptedException {
        var run = runLexer("""
                grammar Comp;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = <EOF> ;

                SKIP = " " ;

                TOKEN = < T0: "ba" >
                | < T1: "a" "-" >
                | < T2: ~[" "] >
                | < T3: "-" >
                | < T4: "->" >
                | < T5: ("\u00e9" "b")+ > ;
                """, "comp", dir, "ba \u00e9b a- x-> \u00e9\u00e9");
        assertEquals(0, run.status(), run.err());
        assertEquals("2:ba;7:\u00e9b;3:a-;4:x;6:->;4:\u00e9;4:\u00e9;", run.out());
    }

    private static final String WORDS = """
            grammar Words;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input = ( <WORD> )* <EOF> ;

            SKIP = " " ;

            TOKEN = < WORD: (["a"-"z"])+ > ;
            """;

    private static boolean hasCompiler() {
        try {
            return new ProcessBuilder("rustc", "--version").start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
