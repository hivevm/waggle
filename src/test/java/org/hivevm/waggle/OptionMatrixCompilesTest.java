// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.api.GenerationException;
import org.hivevm.waggle.api.Language;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Every option that changes what is emitted, switched on one at a time, must still give code that
 * compiles. Each of these was once shipped broken because only the defaults were ever compiled:
 * NODE_FACTORY and DEPTH_LIMIT referred to members nothing declared, KEEP_LINE_COLUMN off called a
 * method it had just left out, and the DEBUG_PARSER trace was JavaCC's generator code rather than
 * generated code.
 */
class OptionMatrixCompilesTest {

    /**
     * Tree building, a syntactic lookahead, a production with a parameter and a result, states and
     * an action on end of input.
     */
    static final String GRAMMAR = """
            grammar Matrix;

            options {
              USE_AST: true,
              NODE_MULTI: true,
              NODE_DEFAULT_VOID: true,
              VISITOR: true,
              JAVA_PACKAGE: "org.example"
            }

            Input() #Root =
              ( LOOKAHEAD(2) pair() | value(0) )* <EOF>
            ;

            pair #Pair =
              <NAME> <COLON> value(1)
            ;

            value(int depth) : int =
              <? int result = depth; ?>
              ( <NUMBER> #Number | <NAME> #Name <? result = depth + 1; ?> )
              <? return result; ?>
            ;

            SKIP = " " | "\\t" | "\\n" | "\\r" ;

            MORE = "/*" : IN_COMMENT ;

            SPECIAL_TOKEN <IN_COMMENT> = < COMMENT: "*/" > : DEFAULT ;
            MORE <IN_COMMENT> = < ~[] > ;

            TOKEN <*> = < EOF > <? { } ?> ;

            TOKEN =
              < COLON: ":" >
            | < NUMBER: (["0"-"9"])+ >
            | < NAME: ["a"-"z"] (["a"-"z","0"-"9"])* >
            ;
            """;

    /** The options each variant adds; the empty one is the grammar as written. */
    static Stream<String> variants() {
        return Stream.of("", "CACHE_TOKENS: true", "ERROR_REPORTING: false",
                "KEEP_LINE_COLUMN: false", "DEBUG_PARSER: true", "DEBUG_LOOKAHEAD: true",
                "DEBUG_TOKEN_MANAGER: true", "DEPTH_LIMIT: 10", "NODE_FACTORY: true",
                "TRACK_TOKENS: true", "FORCE_LA_CHECK: true", "NODE_MULTI: false",
                "USE_AST: false");
    }

    static String withOption(String option) {
        if (option.isEmpty()) {
            return GRAMMAR;
        }
        var name = option.substring(0, option.indexOf(':'));
        var grammar = GRAMMAR.lines().filter(l -> !l.trim().startsWith(name + ":"))
                .reduce("", (a, b) -> a + b + "\n");
        return grammar.replace("  JAVA_PACKAGE: \"org.example\"",
                "  JAVA_PACKAGE: \"org.example\",\n  " + option);
    }

    @ParameterizedTest(name = "Java: {0}")
    @MethodSource("variants")
    void javaCompiles(String option, @TempDir Path dir) throws Exception {
        GeneratedCodeCompilesTest.assertGeneratedSourceCompiles(dir, "Matrix.waggle",
                withOption(option));
    }

    @ParameterizedTest(name = "C++: {0}")
    @MethodSource("variants")
    void cppCompilesAndLinks(String option, @TempDir Path dir) throws Exception {
        if (option.startsWith("NODE_FACTORY")) {
            var error = assertThrows(GenerationException.class,
                    () -> CppCompilesTest.assertCompiles(withOption(option), dir));
            assertTrue(error.getMessage().contains("NODE_FACTORY is not supported"),
                    error.getMessage());
            return;
        }
        CppCompilesTest.assertCompiles(withOption(option), dir);
    }

    /** A grammar that leaves JAVA_PACKAGE out goes to the unnamed package. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("languages")
    void aGrammarWithoutAPackageCompiles(Language language, @TempDir Path dir) throws Exception {
        var grammar = GRAMMAR.replace(",\n  JAVA_PACKAGE: \"org.example\"", "");
        if (language == Language.JAVA) {
            GeneratedCodeCompilesTest.assertGeneratedSourceCompiles(dir, "Matrix.waggle", grammar);
        } else {
            CppCompilesTest.assertCompiles(grammar, dir);
        }
    }

    /**
     * A generic return type. Only its last token was kept, so {@code List<String>} came out as
     * {@code public final > name()}.
     */
    @org.junit.jupiter.api.Test
    void aGenericReturnTypeIsKeptWhole(@TempDir Path dir) throws Exception {
        var grammar = GRAMMAR.replace("  JAVA_PACKAGE: \"org.example\"",
                        "  JAVA_PACKAGE: \"org.example\",\n  JAVA_IMPORTS: \"java.util.*\"")
                .replace("pair #Pair =\n  <NAME> <COLON> value(1)\n;",
                        "pair : List<String> #Pair =\n  <NAME> <COLON> value(1) <? return new ArrayList<>(); ?>\n;");
        assertTrue(grammar.contains("List<String>"), grammar);
        GeneratedCodeCompilesTest.assertGeneratedSourceCompiles(dir, "Matrix.waggle", grammar);
    }

    static Stream<Language> languages() {
        return Stream.of(Language.JAVA, Language.CPP);
    }
}
