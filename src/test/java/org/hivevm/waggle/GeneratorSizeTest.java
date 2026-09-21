// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

/**
 * No class in the generator packages exceeds 1000 lines (ADR-0017).
 *
 * <p>{@code LexerGenerator} was 3064 lines and the Rust back end 1537, because the way to change
 * what a target emits was to extend the class that emitted it. The limit is the measurable form of
 * splitting that into emitters over a target syntax; it is a limit, not a target to sit under by
 * moving code sideways.
 */
class GeneratorSizeTest {

    private static final int LIMIT = 1000;

    private static final Path GENERATOR =
            Path.of("src", "main", "java", "org", "hivevm", "waggle", "generator");

    @Test
    void noGeneratorClassExceedsTheLimit() throws IOException {
        assertTrue(Files.isDirectory(GENERATOR),
                "generator sources not found at " + GENERATOR.toAbsolutePath());

        var oversized = new ArrayList<String>();
        var inspected = 0;
        try (Stream<Path> files = Files.walk(GENERATOR)) {
            for (Path file : (Iterable<Path>) files.filter(
                    p -> p.toString().endsWith(".java"))::iterator) {
                inspected++;
                var count = Files.readAllLines(file).size();
                if (count > GeneratorSizeTest.LIMIT) {
                    oversized.add(GENERATOR.relativize(file) + ": " + count + " lines");
                }
            }
        }

        assertTrue(inspected > 20, "only " + inspected + " generator sources were inspected");
        assertTrue(oversized.isEmpty(),
                "no generator class may exceed " + GeneratorSizeTest.LIMIT + " lines (ADR-0017):\n"
                        + String.join("\n", oversized));
    }
}
