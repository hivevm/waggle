// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.api.GenerationException;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.api.ParserBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The generated Rust parser, run against the Java parser of the same grammar (ADR-0030): for every
 * input both accept, or both reject at the same line and column with the same message.
 *
 * <p>Actions are target code, so a grammar with actions comes in two spellings, one per target.
 */
class RustParserTest {

    /** Plain LL(1): every choice is decided by the next token. */
    private static final String LL1 = """
            grammar Ll;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input = ( Stmt() )* <EOF> ;

            Stmt =
              < IF > < ID >
            | < INT > < ID > [ "=" < NUM > ] ";"
            | < ID >
            ;

            SKIP = " " | "\\n" ;

            TOKEN =
              < IF: "if" >
            | < INT: "int" >
            | < NUM: (["0"-"9"])+ >
            | < ID: (["a"-"z"])+ >
            ;
            """;

    /**
     * Syntactic lookahead of two and three tokens, a semantic lookahead, and productions with a
     * parameter and a result. Java needs the JavaCC idiom "if (true) return" inside a choice.
     */
    private static final String LOOKAHEAD_JAVA = """
            grammar La;

            options {
              JAVA_PACKAGE: "org.example",
              BASE_PARSER: "Base"
            }

            Input =
            <? int total = 0; int n; ?>
              ( n=Item(1) <? total = total + n; ?> )* <EOF>
            ;

            Item(int depth) : int =
            <? Token t; int n; ?>
            (
              LOOKAHEAD(2) t=<ID> "=" n=Sum(depth) ";" <? if (true) return n; ?>
            | LOOKAHEAD(3) <ID> "(" ")" ";" <? if (true) return 0; ?>
            | LOOKAHEAD({ nextIs("call") }) <ID> "(" Sum(depth) ( "," Sum(depth) )* ")" ";" <? if (true) return 1; ?>
            | <ID> "(" <ID> ")" ";" <? if (true) return 2; ?>
            | t=<NUM> ";" <? if (true) return 3; ?>
            ) <? throw new IllegalStateException(); ?>
            ;

            Sum(int depth) : int =
            <? int n = 0; ?>
              ( <NUM> | <ID> | "(" Sum(depth) ")" ) ( LOOKAHEAD(2) "+" ( <NUM> | <ID> ) )* [ "+" "+" ]
              <? return n; ?>
            ;

            SKIP = " " | "\\n" ;

            TOKEN =
              < NUM: (["0"-"9"])+ >
            | < ID: (["a"-"z"])+ >
            ;
            """;

    private static final String LOOKAHEAD_RUST = RustParserTest.LOOKAHEAD_JAVA
            .replace(",\n  BASE_PARSER: \"Base\"", "")
            .replace("<? int total = 0; int n; ?>", "<? let mut total: i32 = 0; let mut n: i32; ?>")
            .replace("Item(int depth) : int", "Item(i32 depth) : i32")
            .replace("Sum(int depth) : int", "Sum(i32 depth) : i32")
            .replace("<? Token t; int n; ?>", "<? let mut t: Token; let mut n: i32; ?>")
            .replace("<? int n = 0; ?>", "<? let mut n: i32 = 0; ?>")
            .replaceAll("<\\? if \\(true\\) return (\\w+); \\?>", "<? return Ok($1); ?>")
            .replace("<? return n; ?>", "<? return Ok(n); ?>")
            .replace("<? throw new IllegalStateException(); ?>", "<? unreachable!() ?>")
            .replace("nextIs(\"call\")", "self.next_is(\"call\")");

    /** What the semantic lookahead calls: Java through the base parser, Rust in an impl block. */
    private static final String BASE_JAVA = """
            package org.example;

            public abstract class Base {
                public abstract Token getToken(int index);

                protected boolean nextIs(String image) {
                    return getToken(1).image.equals(image);
                }
            }
            """;

    private static final String HELPER_RUST = """
            impl<'a> la::parser::Parser<'a> {
                pub fn next_is(&mut self, image: &str) -> bool {
                    self.get_token(1).image == image
                }
            }
            """;

    private static final List<String> LL1_INPUTS = List.of("", "if x", "int y = 3;", "int y;",
            "a b c", "int = 3;", "if", "int y = ;", "if 3", "$", "int y\n= 3\n;", "if x $");

    private static final List<String> LOOKAHEAD_INPUTS = List.of("", "a = 1;", "a = 1 + b + 2;",
            "a = (1 + 2) + c;", "f();", "call(1, 2);", "call(x);", "g(x);", "g(1);", "5;", "a = ;",
            "a = 1 +;", "a = 1 + + ;", "f(;", "call(1,);", "a b", "a = 1", "(", "a = (1;", "$",
            "a = 1; $", "f ( ) ;\nx = 2;", "call(1 + 2, 3 + (4));", "x(", "a = 1 + + b;");

    @Test
    void anLl1ParserAgreesWithJava(@TempDir Path dir) throws Exception {
        assertAgrees(dir, RustParserTest.LL1, RustParserTest.LL1, null, null, "Input",
                RustParserTest.LL1_INPUTS);
    }

    @Test
    void lookaheadParametersAndResultsAgreeWithJava(@TempDir Path dir) throws Exception {
        assertAgrees(dir, RustParserTest.LOOKAHEAD_JAVA, RustParserTest.LOOKAHEAD_RUST,
                RustParserTest.BASE_JAVA, RustParserTest.HELPER_RUST, "Input",
                RustParserTest.LOOKAHEAD_INPUTS);
    }

    /** The token cached ahead and the message without ERROR_REPORTING change nothing observable. */
    @Test
    void cachedTokensAndPlainErrorsAgreeWithJava(@TempDir Path dir) throws Exception {
        var options = "  JAVA_PACKAGE: \"org.example\",\n  CACHE_TOKENS: true,\n  ERROR_REPORTING: false";
        assertAgrees(dir, RustParserTest.LOOKAHEAD_JAVA.replace("  JAVA_PACKAGE: \"org.example\"", options),
                RustParserTest.LOOKAHEAD_RUST.replace("  JAVA_PACKAGE: \"org.example\"", options),
                RustParserTest.BASE_JAVA, RustParserTest.HELPER_RUST, "Input",
                RustParserTest.LOOKAHEAD_INPUTS);
    }

    /** More than 32 tokens need a second word in the masks the expected tokens are read from. */
    @Test
    void manyTokensAgreeWithJava(@TempDir Path dir) throws Exception {
        var alternatives = new ArrayList<String>();
        for (int i = 0; i < 40; i++) {
            alternatives.add("\"k" + i + "\" [ <ID> ] \";\"");
        }
        alternatives.add("LOOKAHEAD(2) <ID> \"=\" <NUM> \";\"");
        alternatives.add("<ID> \"(\" \")\" \";\"");
        var grammar = """
                grammar Many;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( Stmt() )* <EOF> ;

                Stmt =
                  %s
                ;

                SKIP = " " | "\\n" ;

                SPECIAL_TOKEN = < COMMENT: "#" (["a"-"z", " "])* "\\n" > ;

                TOKEN = < NUM: (["0"-"9"])+ > | < ID: ["a"-"j", "l"-"z"] (["a"-"z"])* > ;
                """.formatted(String.join("\n| ", alternatives));
        assertAgrees(dir, grammar, grammar, null, null, "Input", List.of("k0 x; k33; k39 y;",
                "k0 k1", "x = 5; # c d\nk7;", "x (", "k40;", "x = y;", "k33 x", "# c\n", ""));
    }

    /**
     * A token owns the special tokens before it; Java chains them. The image of &lt;EOF&gt; is
     * empty, as in Java: it was the text before it.
     */
    @Test
    void aTokenOwnsTheSpecialTokensBeforeIt(@TempDir Path dir) throws Exception {
        var grammar = """
                grammar Spec;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( <ID> )* <EOF> ;

                SKIP = " " ;

                SPECIAL_TOKEN = < COMMENT: "#" (["a"-"z"])* > ;

                TOKEN = < ID: (["a"-"z"])+ > ;
                """;
        var output = runRust(dir, grammar, "spec", """
                let mut lexer = spec::lexer::Lexer::new("#one #two word #three");
                loop {
                    let token = lexer.get_next_token().unwrap();
                    let special: Vec<String> = token.special.iter().map(|t| t.image.clone()).collect();
                    print!("{}{:?};", token.image, special);
                    if token.kind == 0 {
                        break;
                    }
                }
                """);
        assertEquals("word[\"#one\", \"#two\"];[\"#three\"];", output);
    }

    /** A lexical error is a value the caller can tell apart, not a panic. */
    @Test
    void aLexicalErrorIsAValue(@TempDir Path dir) throws Exception {
        var output = runRust(dir, RustParserTest.LL1, "ll", """
                let mut parser = ll::parser::Parser::new("int x;\\n  $ y");
                match parser.input() {
                    Err(ll::parser::ParseError::Lexical(error)) => print!("{}:{}", error.line, error.column),
                    other => print!("{:?}", other.is_ok()),
                }
                """);
        assertEquals("2:3", output);
    }

    /**
     * Rust keywords among the names are raw identifiers; a production that would shadow a method of
     * the parser is refused.
     */
    @Test
    void keywordsAreRawIdentifiers(@TempDir Path dir) throws Exception {
        var grammar = """
                grammar Kw;

                options {
                  JAVA_PACKAGE: "org.example"
                }

                Input = ( Type() )* <EOF> ;

                Type = < fn > < ID > ;

                SKIP = " " ;

                TOKEN = < fn: "fn" > | < ID: (["a"-"z"])+ > ;
                """;
        var output = runRust(dir, grammar, "kw", """
                let mut parser = kw::parser::Parser::new("fn a fn b");
                print!("{}", parser.input().is_ok());
                """);
        assertEquals("true", output);

        var clash = grammar.replace("Type", "New");
        var source = dir.resolve("clash").resolve("Clash.waggle");
        Files.createDirectories(source.getParent());
        Files.writeString(source, clash);
        var failure = assertThrows(GenerationException.class, () -> new ParserBuilder()
                .setLanguage(Language.RUST).setParserFile(source.toFile())
                .setTargetDir(dir.resolve("clash").resolve("rust").toFile()).build().parse());
        assertTrue(failure.getMessage().contains("'new'"), failure.getMessage());
    }

    /**
     * Generates both parsers and runs every input through both. The Java outcome is "OK" or
     * "ERR line:column message", and the Rust main prints the same.
     */
    private static void assertAgrees(Path dir, String javaGrammar, String rustGrammar,
            String javaBase, String rustHelper, String start, List<String> inputs) throws Exception {
        assumeTrue(RustParserTest.hasCompiler(), "no Rust compiler on PATH");

        var java = RustParserTest.javaParser(dir.resolve("java"), javaGrammar, javaBase, start);
        var module = RustParserTest.module(rustGrammar);
        var program = RustParserTest.buildRust(dir.resolve("rust"), rustGrammar, module, """
                use std::io::Read;

                fn main() {
                    let mut input = String::new();
                    std::io::stdin().read_to_string(&mut input).unwrap();
                    let mut parser = %s::parser::Parser::new(&input);
                    match parser.%s() {
                        Ok(_) => print!("OK"),
                        Err(error) => print!("ERR {}:{} {}", error.line(), error.column(), error),
                    }
                }
                """.formatted(module, start.toLowerCase()) + (rustHelper == null ? "" : rustHelper));

        for (var input : inputs) {
            var process = new ProcessBuilder(program.toString()).redirectErrorStream(true).start();
            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            var rust = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "the Rust parser did not end");
            assertEquals(java.run(input), rust, "input [" + input + "]");
        }
    }

    /** A compiled Java parser: runs {@code start} on an input and reports as the Rust main does. */
    private record JavaParser(java.lang.reflect.Constructor<?> constructor, java.lang.reflect.Method start) {

        private static final Pattern POSITION = Pattern.compile("line (-?\\d+), column (-?\\d+)");

        String run(String input) throws Exception {
            try {
                this.start.invoke(this.constructor.newInstance(input));
                return "OK";
            } catch (InvocationTargetException e) {
                var message = e.getCause().getMessage();
                var position = JavaParser.POSITION.matcher(message);
                return "ERR " + (position.find() ? position.group(1) + ":" + position.group(2) : "0:0")
                        + " " + message;
            }
        }
    }

    private static JavaParser javaParser(Path dir, String grammar, String base, String start)
            throws Exception {
        var source = dir.resolve("Grammar.waggle");
        Files.createDirectories(dir);
        Files.writeString(source, grammar);
        var target = dir.resolve("generated");
        new ParserBuilder().setLanguage(Language.JAVA).setParserFile(source.toFile())
                .setTargetDir(target.toFile()).build().parse();
        if (base != null) {
            Files.writeString(target.resolve("org").resolve("example").resolve("Base.java"), base);
        }

        List<File> sources;
        try (Stream<Path> paths = Files.walk(target)) {
            sources = paths.filter(p -> p.toString().endsWith(".java")).map(Path::toFile).toList();
        }
        var classes = Files.createDirectories(dir.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            var ok = compiler.getTask(null, files, diagnostics, List.of("-d", classes.toString()),
                    null, files.getJavaFileObjectsFromFiles(sources)).call();
            assertTrue(ok, "the Java parser does not compile:\n" + diagnostics.getDiagnostics());
        }
        var loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()},
                RustParserTest.class.getClassLoader());
        var parser = loader.loadClass("org.example.Parser");
        return new JavaParser(parser.getConstructor(String.class), parser.getMethod(start));
    }

    /** The module a grammar is generated into: its name in lower case. */
    private static String module(String grammar) {
        var matcher = Pattern.compile("grammar (\\w+);").matcher(grammar);
        assertTrue(matcher.find());
        return matcher.group(1).toLowerCase();
    }

    /** Builds a program from the generated modules and {@code main}, and returns its path. */
    private static Path buildRust(Path dir, String grammar, String module, String main)
            throws IOException, InterruptedException {
        var source = dir.resolve("Grammar.waggle");
        Files.createDirectories(dir);
        Files.writeString(source, grammar);
        var target = dir.resolve("generated");
        new ParserBuilder().setLanguage(Language.RUST).setParserFile(source.toFile())
                .setTargetDir(target.toFile()).build().parse();

        Files.writeString(target.resolve(module).resolve("mod.rs"),
                "pub mod token;\npub mod charstream;\npub mod parserconstants;\npub mod lexer;\npub mod parser;\n");
        Files.writeString(target.resolve("main.rs"), "mod " + module + ";\n\n" + main);
        // Warnings count as failures: generated code has to build cleanly in a user's crate.
        var build = new ProcessBuilder("rustc", "--edition", "2024", "-D", "warnings", "-o", "program",
                "main.rs").directory(target.toFile()).redirectErrorStream(true).start();
        var output = new String(build.getInputStream().readAllBytes());
        assertEquals(0, build.waitFor(), "the generated Rust does not build:\n" + output);
        return target.resolve("program");
    }

    /** Runs {@code body} as the main of a program built from {@code grammar}. */
    private static String runRust(Path dir, String grammar, String module, String body)
            throws IOException, InterruptedException {
        assumeTrue(RustParserTest.hasCompiler(), "no Rust compiler on PATH");
        var program = RustParserTest.buildRust(dir, grammar, module, "fn main() {\n" + body + "}\n");
        var process = new ProcessBuilder(program.toString()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(20, TimeUnit.SECONDS), "the Rust program did not end");
        assertEquals(0, process.exitValue(), output);
        return output;
    }

    private static boolean hasCompiler() {
        try {
            return new ProcessBuilder("rustc", "--version").start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
