// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.api.Waggle;

import org.hivevm.waggle.api.ParserBuilder;

import org.hivevm.waggle.api.Language;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The tool must be able to generate its own parser from its own grammar (self-hosting).
 *
 * <p>These tests used to assert nothing and to write into the source tree ({@code src/main/generated2},
 * a directory in no source set, so the result was never even compiled). They now assert that the
 * expected sources appear, and they write to a temporary directory.
 *
 * <p>The self-hosted parser must emit its own {@code Token} (ADR-0013): the published plugin that
 * regenerates it in the build always does, so the hand-written code has to agree with that.
 */
class WaggleParserTest {

    private static final File PARSER_SOURCE =
            new File(new File(".").getAbsoluteFile(), "src/main/resources");

    @Test
    void generatesItsOwnParser(@TempDir Path target) {
        new ParserBuilder()
                .setLanguage(Language.JAVA)
                .setTargetDir(target.toFile())
                .setParserFile(WaggleParserTest.PARSER_SOURCE, "Waggle.waggle")
                .build().parse();

        assertGenerated(target, "org/hivevm/waggle/grammar", "Parser.java", "Lexer.java",
                "ParserConstants.java", "Token.java");
    }

    private static void assertGenerated(Path target, String pkg, String... names) {
        for (var name : names) {
            var file = target.resolve(pkg).resolve(name);
            assertTrue(Files.isRegularFile(file), "not generated: " + file);
        }
    }
}
