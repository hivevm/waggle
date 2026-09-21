// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the front-end boundary from ADR-0013: {@code org.hivevm.waggle.model} is the stable,
 * language-independent artifact of the front end and must depend only on the JDK, on
 * {@code org.hivevm.core}, and on itself — never on the parser or any later stage. The one exception
 * is the generated {@code org.hivevm.waggle.parser.Token}, which the model holds for positions and
 * verbatim token chains (ADR-0013). A dependency-free source scan keeps the rest of the
 * {@code model -> parser} cycle from silently returning.
 */
class ModelLayeringTest {

    private static final Path MODEL_DIR =
            Path.of("src", "main", "java", "org", "hivevm", "waggle", "model");

    /** The generated token class, the only parser type the model may use (ADR-0013). */
    private static final String PARSER_TOKEN = "org.hivevm.waggle.parser.Token";

    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+);");

    /** Fragments of generated tree code: the runtime field and the scope variables. */
    private static final List<String> TREE_CODE = List.of("jjtree.", "jjtn", "jjtc", "jjte");

    private static boolean isAllowed(String imported) {
        return imported.startsWith("java.")
                || imported.startsWith("org.hivevm.core.")
                || imported.startsWith("org.hivevm.waggle.model.")
                || imported.equals(ModelLayeringTest.PARSER_TOKEN);
    }

    @Test
    void modelDependsOnlyOnCoreAndJdk() throws IOException {
        assertTrue(Files.isDirectory(MODEL_DIR),
                "model source directory not found at " + MODEL_DIR.toAbsolutePath()
                        + " (run the test from the project root)");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MODEL_DIR)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                for (String line : Files.readAllLines(file)) {
                    Matcher matcher = ModelLayeringTest.IMPORT.matcher(line.strip());
                    if (matcher.matches() && !ModelLayeringTest.isAllowed(matcher.group(1))) {
                        violations.add(MODEL_DIR.relativize(file) + " -> " + matcher.group(1));
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "model must depend only on the JDK, org.hivevm.core, itself and " + ModelLayeringTest.PARSER_TOKEN
                        + " (ADR-0013), but found:\n"
                        + String.join("\n", violations));
    }

    /**
     * Imports were never the only way in. {@code NodeDescriptor.openNode()} returned the string
     * {@code "jjtree.openNodeScope(…);"} and {@code NodeScope} built the identifier {@code jjtn000},
     * so the language-independent model emitted target source text without importing anything — and
     * the text was Java and C++ only, since the Rust back end wrote its own (ADR-0016).
     */
    @Test
    void modelEmitsNoTreeCode() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MODEL_DIR)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                int number = 0;
                for (String line : Files.readAllLines(file)) {
                    number++;
                    for (String forbidden : ModelLayeringTest.TREE_CODE) {
                        if (line.contains(forbidden)) {
                            violations.add(MODEL_DIR.relativize(file) + ":" + number + " -> " + forbidden);
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "model must not spell out generated tree code (ADR-0016), but found:\n"
                        + String.join("\n", violations));
    }
}
