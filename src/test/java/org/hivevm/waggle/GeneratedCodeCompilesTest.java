package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Regression tests: the generated parser must actually <em>compile</em>.
 *
 * <p>These cover three defects where the generator emitted references without the matching
 * declarations:
 * <ul>
 * <li>a grammar without syntactic {@code LOOKAHEAD} lost {@code getToken}, {@code getNextToken},
 * {@code jj_ntk_f} and {@code JJCalls} (they were trapped inside an {@code //@if(JJ2_INDEX)} block);
 * <li>{@code VISITOR} without {@code VISITOR_DATA_TYPE} produced an untyped {@code jjtAccept}
 * parameter;
 * <li>a missing {@code NODE_CLASS} produced {@code class ASTx extends  { }}.
 * </ul>
 */
class GeneratedCodeCompilesTest {

    /** A plain LL(1) grammar: no syntactic LOOKAHEAD anywhere. */
    private static final String NO_LOOKAHEAD = """
            grammar NoLookahead;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input =
              MatchedBraces() <EOF>
            ;

            MatchedBraces =
              < LBRACE > [ MatchedBraces() ] < RBRACE >
            ;

            SKIP = " " | "\\t" | "\\n" | "\\r" ;

            TOKEN =
              < LBRACE: "{" >
            | < RBRACE: "}" >
            ;
            """;

    /** Tree building with a visitor, but neither NODE_CLASS nor VISITOR_DATA_TYPE given. */
    private static final String TREE_WITHOUT_NODE_OPTIONS = """
            grammar TreeDefaults;

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
            ;

            SKIP = " " | "\\n" ;

            TOKEN =
              < PLUS: "+" >
            | < NUMBER: (["0"-"9"])+ >
            ;
            """;

    /**
     * A grammar whose tokens reach beyond ASCII, so the NFA builds composite states and the lexer
     * dispatches on {@code jjCanMove_0}. That path emitted {@code case 12: &#123;} — a case that opens a
     * block — and then wrote the remaining labels of the very same body <em>inside</em> it, which
     * javac rejects as an orphaned case. It went unnoticed because no test ever compiled a grammar
     * that reached it.
     */
    private static final String NON_ASCII_COMPOSITE_STATES = """
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
     * A choice whose middle alternative can match the empty sequence, after one that opens a switch.
     * Semanticize only warns about it, but generation then died with a NullPointerException: the
     * parser builder filed the switch's default case under the last alternative, while the generator
     * looked it up under the one the lookahead loop had stopped at.
     */
    static final String EMPTY_ALTERNATIVE = """
            grammar EmptyAlt;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input =
              ( <A> | [ <B> ] | <C> ) <EOF>
            ;

            SKIP = " " ;

            TOKEN =
              < A: "a" >
            | < B: "b" >
            | < C: "c" >
            ;
            """;

    /**
     * A TOKEN that is neither labelled nor a string literal. The back ends warned about it and then
     * wrote nothing into the token-image table — an initializer with an empty element, which does
     * not compile, and every later token reported under its neighbour's image.
     */
    static final String UNLABELLED_TOKEN = """
            grammar Unlabelled;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input =
              ( <ID> | "+" )* <EOF>
            ;

            SKIP = " " ;

            TOKEN =
              < ["0"-"9"] >
            | < ID: (["a"-"z"])+ >
            ;
            """;

    /**
     * Nodes without NODE_MULTI. The parser opened "new Node(this, NodeType.X)" on a tree it never
     * declared, and neither Node, NodeType nor NodeState was generated: only NODE_MULTI switched
     * the tree runtime on.
     */
    static final String NODES_WITHOUT_NODE_MULTI = """
            grammar SingleNode;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input() #Root =
              <NUMBER> #Number <EOF>
            ;

            SKIP = " " ;

            TOKEN =
              < NUMBER: (["0"-"9"])+ >
            ;
            """;

    @Test
    void choiceWithAnEmptyAlternativeCompiles(@TempDir Path dir) throws IOException {
        assertGeneratedSourceCompiles(dir, "EmptyAlt.waggle", GeneratedCodeCompilesTest.EMPTY_ALTERNATIVE);
    }

    @Test
    void unlabelledTokenKeepsItsEntryInTheTokenImageTable(@TempDir Path dir) throws IOException {
        assertGeneratedSourceCompiles(dir, "Unlabelled.waggle", GeneratedCodeCompilesTest.UNLABELLED_TOKEN);

        String constants;
        try (Stream<Path> paths = Files.walk(dir.resolve("generated"))) {
            var file = paths.filter(p -> p.getFileName().toString().equals("ParserConstants.java"))
                    .findFirst().orElseThrow();
            constants = Files.readString(file);
        }
        assertTrue(constants.contains("\"<token of kind "),
                "the unlabelled token has no entry in tokenImage:\n" + constants);
    }

    @Test
    void nodesWithoutNodeMultiCompile(@TempDir Path dir) throws IOException {
        assertGeneratedSourceCompiles(dir, "SingleNode.waggle",
                GeneratedCodeCompilesTest.NODES_WITHOUT_NODE_MULTI);
    }

    @Test
    void grammarWithoutSyntacticLookaheadCompiles(@TempDir Path dir) throws IOException {
        assertGeneratedSourceCompiles(dir, "NoLookahead.waggle", GeneratedCodeCompilesTest.NO_LOOKAHEAD);
    }

    @Test
    void treeGrammarWithoutNodeOptionsCompiles(@TempDir Path dir) throws IOException {
        assertGeneratedSourceCompiles(dir, "TreeDefaults.waggle",
                GeneratedCodeCompilesTest.TREE_WITHOUT_NODE_OPTIONS);
    }

    /**
     * The token-manager trace. It referenced a {@code debugStream} that was never declared, a
     * {@code tokenImage} the lexer had no access to, and two {@code jjKindsFor…} helpers that did not
     * exist — 41 missing symbols in all. Nothing that switched DEBUG_TOKEN_MANAGER on had ever
     * compiled.
     */
    private static final String TOKEN_MANAGER_TRACE =
            GeneratedCodeCompilesTest.NO_LOOKAHEAD.replace(
                    "  JAVA_PACKAGE: \"org.example\"",
                    "  JAVA_PACKAGE: \"org.example\",\n  DEBUG_TOKEN_MANAGER: true");

    @Test
    void grammarWithTokenManagerTraceCompiles(@TempDir Path dir) throws IOException {
        assertGeneratedSourceCompiles(dir, "Trace.waggle",
                GeneratedCodeCompilesTest.TOKEN_MANAGER_TRACE);
    }

    @Test
    void grammarWithCompositeNonAsciiStatesCompiles(@TempDir Path dir) throws IOException {
        assertGeneratedSourceCompiles(dir, "Str.waggle",
                GeneratedCodeCompilesTest.NON_ASCII_COMPOSITE_STATES);
    }

    /**
     * KEEP_LINE_COLUMN drives the line/column tracking in {@code jjFillToken()}. Nothing asserted
     * that, and a lexer stripped of it still compiles — it just hands out tokens whose position is
     * always zero. The condition guarding those blocks was spelled {@code KEEP_LINE_COOL} in the
     * templates <em>and</em> in the generator, so it worked by accident; this pins the behaviour to
     * the option rather than to the spelling.
     */
    @Test
    void keepLineColumnDrivesPositionTracking(@TempDir Path dir) throws IOException {
        var on = generate(dir.resolve("on"), "Pos.waggle", GeneratedCodeCompilesTest.NO_LOOKAHEAD);
        assertTrue(lexerOf(on).contains("t.beginLine = beginLine;"),
                "the lexer does not track line/column, although KEEP_LINE_COLUMN defaults to true");

        var off = generate(dir.resolve("off"), "Pos.waggle", GeneratedCodeCompilesTest.NO_LOOKAHEAD
                .replace("  JAVA_PACKAGE: \"org.example\"",
                        "  JAVA_PACKAGE: \"org.example\",\n  KEEP_LINE_COLUMN: false"));
        assertTrue(!lexerOf(off).contains("t.beginLine = beginLine;"),
                "the lexer still tracks line/column, although KEEP_LINE_COLUMN was switched off");
    }

    /** Reads the generated {@code Lexer.java}, wherever the package layout put it. */
    private static String lexerOf(Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(target)) {
            var lexer = paths.filter(p -> p.getFileName().toString().equals("Lexer.java")).findFirst()
                    .orElseThrow(() -> new AssertionError("no Lexer.java was generated in " + target));
            return Files.readString(lexer);
        }
    }

    private static Path generate(Path dir, String name, String grammar) throws IOException {
        Files.createDirectories(dir);
        var source = dir.resolve(name);
        Files.writeString(source, grammar);

        var target = dir.resolve("generated");
        var builder = new ParserBuilder();
        builder.setLanguage(Language.JAVA);
        builder.setTargetDir(target.toFile());
        builder.setParserFile(source.toFile());
        builder.build().parse();
        return target;
    }

    private static void assertGeneratedSourceCompiles(Path dir, String name, String grammar)
            throws IOException {
        var source = dir.resolve(name);
        Files.writeString(source, grammar);

        var target = dir.resolve("generated");
        var builder = new ParserBuilder();
        builder.setLanguage(Language.JAVA);
        builder.setTargetDir(target.toFile());
        builder.setParserFile(source.toFile());
        builder.build().parse();

        List<File> sources;
        try (Stream<Path> paths = Files.walk(target)) {
            sources = paths.filter(p -> p.toString().endsWith(".java")).map(Path::toFile)
                    .collect(Collectors.toList());
        }
        assertTrue(!sources.isEmpty(), "no sources were generated for " + name);

        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            var units = files.getJavaFileObjectsFromFiles(sources);
            var classes = Files.createDirectories(dir.resolve("classes"));
            var ok = compiler.getTask(null, files, diagnostics,
                    List.of("-d", classes.toString()), null, units).call();

            var errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                    .map(Object::toString).collect(Collectors.joining("\n"));
            assertTrue(ok, "generated code for " + name + " does not compile:\n" + errors);
        }
    }
}
