package org.hivevm.cc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        assertLexerCompiles(RustCompilesTest.NON_ASCII_FINAL_STATE, "unit", dir);
    }

    @Test
    void generatedRustCompilesWithTokenManagerTrace(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertLexerCompiles(RustCompilesTest.TRACE, "kw", dir);
    }

    @Test
    void generatedRustCompilesWithSpecialTokens(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertLexerCompiles(RustCompilesTest.SPECIAL_TOKENS, "spec", dir);
    }

    @Test
    void generatedRustCompilesWithLexicalActions(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertLexerCompiles(RustCompilesTest.LEXICAL_ACTIONS, "act", dir);
    }

    @Test
    void generatedRustCompiles(@TempDir Path dir) throws IOException, InterruptedException {
        assertLexerCompiles(RustCompilesTest.KEYWORDS, "kw", dir);
    }

    @Test
    void generatedRustCompilesWithCompositeNonAsciiStates(@TempDir Path dir)
            throws IOException, InterruptedException {
        assertLexerCompiles(RustCompilesTest.NON_ASCII, "str", dir);
    }

    /**
     * The parser is not covered yet: it still emits Java. Only the lexer and what it depends on go
     * through rustc.
     */
    private static void assertLexerCompiles(String grammar, String module, Path dir)
            throws IOException, InterruptedException {
        assumeTrue(RustCompilesTest.hasCompiler(), "no Rust compiler on PATH");

        var source = dir.resolve("Grammar.jj");
        Files.writeString(source, grammar);

        var target = dir.resolve("rust");
        new ParserBuilder()
                .setLanguage(Language.RUST)
                .setParserFile(source.toFile())
                .setTargetDir(target.toFile())
                .build().parse();

        Files.writeString(target.resolve(module).resolve("mod.rs"),
                "pub mod token;\npub mod charstream;\npub mod parserconstants;\npub mod lexer;\n");
        var root = target.resolve("lib.rs");
        Files.writeString(root, "pub mod " + module + ";\n");

        var process = new ProcessBuilder("rustc", "--edition", "2021", "--crate-type", "lib",
                "--emit=metadata", "lib.rs")
                .directory(target.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes());

        assertEquals(0, process.waitFor(), "the generated Rust does not compile:\n" + output);
    }

    private static boolean hasCompiler() {
        try {
            return new ProcessBuilder("rustc", "--version").start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
