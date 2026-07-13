package org.hivevm.cc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * The tool must be able to generate its own parser from its own grammar (self-hosting).
 *
 * <p>These tests used to assert nothing and to write into the source tree ({@code src/main/generated2},
 * a directory in no source set, so the result was never even compiled). They now assert that the
 * expected sources appear, and they write to a temporary directory.
 */
class CCParserTest {

    private static final File PARSER_SOURCE =
            new File(new File(".").getAbsoluteFile(), "src/main/resources");

    private static final List<String> NODES = Arrays.asList("BNF", "BNFAction", "BNFDeclaration",
            "BNFNodeScope", "ExpansionNodeScope", "NodeDescriptor", "OptionBinding");

    @Test
    void generatesItsOwnParser(@TempDir Path target) {
        new ParserBuilder()
                .setLanguage(Language.JAVA)
                .setTargetDir(target.toFile())
                .setParserFile(CCParserTest.PARSER_SOURCE, "JavaCC.jj")
                .build().parse();

        assertGenerated(target, "org/hivevm/cc/parser", "Parser.java", "Lexer.java",
                "ParserConstants.java");
    }

    @Test
    void generatesTheTreeParser(@TempDir Path target) {
        new ParserBuilder()
                .setLanguage(Language.JAVA)
                .setTargetDir(target.toFile())
                .setParserFile(CCParserTest.PARSER_SOURCE, "JJTree.jjt")
                .setCustomNodes(CCParserTest.NODES)
                .build().parse();

        assertGenerated(target, "org/hivevm/cc/parser/jjtree", "Parser.java", "Lexer.java",
                "ParserConstants.java");
    }

    private static void assertGenerated(Path target, String pkg, String... names) {
        for (var name : names) {
            var file = target.resolve(pkg).resolve(name);
            assertTrue(Files.isRegularFile(file), "not generated: " + file);
        }
    }
}
