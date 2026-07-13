package org.hivevm.cc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Regression tests for the C++ and Rust back ends.
 *
 * <p>They were broken in a way that produced no error at all: the template engine dropped every
 * {@code //@if(!X)} block, so the C++ header declared no {@code jj_ntk_f()} while the body called it,
 * and the Rust struct had no {@code jj_ntk} field while the code assigned to it. A C++/Rust compiler
 * cannot be assumed to exist here, so instead of compiling we assert that the declarations a
 * generated parser refers to are actually emitted.
 */
class MultiTargetGenerationTest {

    /** A grammar with a syntactic LOOKAHEAD, so the lookahead machinery is emitted too. */
    private static final String GRAMMAR = """
            grammar Example;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input =
              MatchedBraces() <EOF>
            ;

            MatchedBraces =
              < LBRACE > [ LOOKAHEAD(2) MatchedBraces() ] < RBRACE >
            ;

            SKIP = " " | "\\t" | "\\n" | "\\r" ;

            TOKEN =
              < LBRACE: "{" >
            | < RBRACE: "}" >
            ;
            """;

    /** Every back end must at least produce output without throwing. */
    @ParameterizedTest
    @EnumSource(Language.class)
    void everyTargetGenerates(Language language, @TempDir Path dir) throws IOException {
        var target = generate(language, dir);
        try (Stream<Path> paths = Files.walk(target)) {
            assertTrue(paths.anyMatch(Files::isRegularFile),
                    "no output was generated for " + language);
        }
    }

    /**
     * The C++ header must declare {@code jj_ntk_f()} — the body calls it unconditionally.
     * Guarded by {@code //@if(!CACHE_TOKENS)}, which used to render never.
     */
    @Test
    void cppDeclaresWhatItCalls(@TempDir Path dir) throws IOException {
        var target = generate(Language.CPP, dir);
        var header = Files.readString(target.resolve("Example.h"));
        assertTrue(header.contains("jj_ntk_f"),
                "the C++ header does not declare jj_ntk_f(), but Example.cc calls it");
    }

    /**
     * The Rust parser struct must declare the {@code jj_ntk} field it assigns to.
     */
    @Test
    void rustDeclaresWhatItAssigns(@TempDir Path dir) throws IOException {
        var target = generate(Language.RUST, dir);
        var parser = Files.readString(target.resolve("example").resolve("parser.rs"));
        assertTrue(parser.contains("jj_ntk:"),
                "the Rust parser struct has no jj_ntk field, but the code assigns to it");
    }

    /** The same grammar, but with the token-manager trace switched on. */
    private static final String GRAMMAR_DEBUG = MultiTargetGenerationTest.GRAMMAR.replace(
            "  JAVA_PACKAGE: \"org.example\"",
            "  JAVA_PACKAGE: \"org.example\",\n  DEBUG_TOKEN_MANAGER: true");

    /**
     * The DEBUG_TOKEN_MANAGER trace must reference things that exist.
     *
     * <p>It did not: the C++ back end emitted {@code fprintf(debugStreoptions.set, …)} — the remains
     * of a botched search-and-replace — and the Rust back end {@code vjjmatchedKind}, a name that is
     * declared nowhere. Neither compiles, and neither was noticed, because the trace is off by
     * default.
     */
    @ParameterizedTest
    @EnumSource(Language.class)
    void debugTraceReferencesOnlyExistingNames(Language language, @TempDir Path dir)
            throws IOException {
        var target = generate(language, dir, MultiTargetGenerationTest.GRAMMAR_DEBUG);

        List<String> sources;
        try (Stream<Path> paths = Files.walk(target)) {
            sources = paths.filter(Files::isRegularFile).map(Path::toString).toList();
        }
        assertTrue(!sources.isEmpty(), "no output for " + language);

        for (var file : sources) {
            var text = Files.readString(Path.of(file));
            assertTrue(!text.contains("debugStreoptions"),
                    "the trace writes to a stream that does not exist, in " + file);
            assertTrue(!text.contains("vjjmatchedKind"),
                    "the trace reads a variable that is never declared, in " + file);
        }
    }

    private static Path generate(Language language, Path dir) throws IOException {
        return generate(language, dir, MultiTargetGenerationTest.GRAMMAR);
    }

    private static Path generate(Language language, Path dir, String grammar) throws IOException {
        var source = dir.resolve("Example.jj");
        Files.writeString(source, grammar);

        var target = dir.resolve("out-" + language);
        new ParserBuilder()
                .setLanguage(language)
                .setParserFile(source.toFile())
                .setTargetDir(target.toFile())
                .build().parse();
        return target;
    }
}
