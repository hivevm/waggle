package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tree building follows from the grammar, not from a marker in its text.
 *
 * <p>The driver used to scan the grammar for an {@code @generated(JJTree)} comment — the stamp
 * JavaCC's JJTree pre-processor left on the intermediate grammar it wrote. There is no such
 * pre-processor and no intermediate grammar (ADR-0010), so the marker could only ever arrive by
 * accident, and when it did it switched the parser template's tree code on for a grammar that
 * declares no node at all.
 */
class TreeDetectionTest {

    /** Carries the marker in a comment, but declares no {@code #Node}. */
    private static final String MARKED = """
            grammar Marked;

            /* @generated(JJTree) */

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input = < WORD > <EOF> ;

            TOKEN = < WORD: (["a"-"z"])+ > ;
            """;

    /** The same grammar with a node descriptor, so tree building is genuinely requested. */
    private static final String WITH_NODE = """
            grammar Noded;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input #Root = < WORD > <EOF> ;

            TOKEN = < WORD: (["a"-"z"])+ > ;
            """;

    @Test
    void theMarkerDoesNotSwitchOnTreeBuilding(@TempDir Path dir) throws IOException {
        var target = generate(dir, "Marked.waggle", TreeDetectionTest.MARKED);

        assertFalse(Files.readString(target.resolve("org/example/Parser.java")).contains("jjtree"),
                "a grammar without #Node must not build a tree, whatever its comments say");
        assertFalse(Files.isRegularFile(target.resolve("org/example/Node.java")),
                "no node runtime may be written for a grammar without #Node");
    }

    @Test
    void aNodeDescriptorSwitchesOnTreeBuilding(@TempDir Path dir) throws IOException {
        var target = generate(dir, "Noded.waggle", TreeDetectionTest.WITH_NODE);

        assertTrue(Files.readString(target.resolve("org/example/Parser.java")).contains("jjtree"),
                "a grammar with #Node must build a tree");
        assertTrue(Files.isRegularFile(target.resolve("org/example/Node.java")),
                "the node runtime must be written for a grammar with #Node");
    }

    private static Path generate(Path dir, String name, String grammar) throws IOException {
        var source = dir.resolve(name);
        Files.writeString(source, grammar);

        var target = dir.resolve("out");
        new ParserBuilder()
                .setLanguage(Language.JAVA)
                .setParserFile(source.toFile())
                .setTargetDir(target.toFile())
                .build().parse();
        return target;
    }
}
