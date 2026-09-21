// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * JJTree consumes the tree API; it does not reach into a back end (ADR-0016).
 *
 * <p>Its {@code TreeGenerator} used to call {@code ParserGenerator.insert*NodeCode} directly, and
 * its writer used {@code CodeGenerator.can_replace}/{@code replace}. So the one thing that would
 * show the tree API works could only run through a back end — which meant there was no tree API.
 */
class JJTreeLayeringTest {

    private static final Path JJTREE_DIR =
            Path.of("src", "main", "java", "org", "hivevm", "waggle", "jjtree");

    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+);");

    /**
     * What JJTree may depend on. The option types ({@code WaggleOptions}, {@code parser.Options})
     * are still on the list: they are the single string-keyed option set, which a later change
     * replaces with records — until then, narrowing this would mean inventing a second option type
     * for JJTree alone.
     */
    private static final List<String> ALLOWED = List.of(
            "java.",
            "org.jspecify.",
            "org.hivevm.core.",
            "org.hivevm.source.",
            "org.hivevm.waggle.diag.",
            "org.hivevm.waggle.model.",
            "org.hivevm.waggle.tree.",
            "org.hivevm.waggle.jjtree.",
            "org.hivevm.waggle.api.Encoding",
            "org.hivevm.waggle.api.Language",
            "org.hivevm.waggle.api.Options",
            "org.hivevm.waggle.api.WaggleOptions",
            "org.hivevm.waggle.api.Options");

    @Test
    void jjtreeDependsOnTheTreeApiAndNotOnABackEnd() throws IOException {
        assertTrue(Files.isDirectory(JJTREE_DIR),
                "jjtree source directory not found at " + JJTREE_DIR.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(JJTREE_DIR)) {
            for (Path file : (Iterable<Path>) files.filter(
                    p -> p.toString().endsWith(".java"))::iterator) {
                for (String line : Files.readAllLines(file)) {
                    Matcher matcher = JJTreeLayeringTest.IMPORT.matcher(line.strip());
                    if (matcher.matches() && JJTreeLayeringTest.ALLOWED.stream()
                            .noneMatch(a -> matcher.group(1).startsWith(a))) {
                        violations.add(JJTREE_DIR.relativize(file) + " -> " + matcher.group(1));
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "jjtree must consume the tree API, not a back end (ADR-0016), but found:\n"
                        + String.join("\n", violations));
    }
}
