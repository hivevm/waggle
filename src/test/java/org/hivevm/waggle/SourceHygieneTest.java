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
 * The import hygiene of the hand-written sources.
 *
 * <p>The build compiles {@code src/main/java} together with {@code src/main/generated}, so it cannot
 * be held to {@code -Werror}: the generated sources come from the published generator and a warning
 * there would gate a build nobody in this tree can fix. javac does not report unused imports at any
 * lint level either. This test is the guard for what this repository writes by hand — the same shape
 * as {@link PackageDagTest}, which states the dependency graph rather than discovering it one
 * crossed edge at a time (ADR-0019).
 *
 * <p>It found ten dead imports left in {@code LexerGenerator} by the split of the lexer back ends
 * (ADR-0017) and four imports of a class's own package.
 */
class SourceHygieneTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);");

    private static final Pattern IMPORT =
            Pattern.compile("^import\\s+(?:(static)\\s+)?([\\w.]+?)(?:\\.(\\*))?;");

    /**
     * Whether {@code name} occurs in {@code body} as an identifier. A plain {@code contains} would
     * count {@code List} inside {@code ArrayList} and let a dead import through.
     */
    private static boolean mentions(String body, String name) {
        return Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(body).find();
    }

    private record Source(Path file, String pkg, List<String> lines, String body) {}

    private static List<Source> sources() throws IOException {
        var sources = new ArrayList<Source>();
        try (Stream<Path> files = Files.walk(SourceHygieneTest.SOURCES)) {
            for (Path file : (Iterable<Path>) files.filter(
                    p -> p.toString().endsWith(".java"))::iterator) {
                var lines = Files.readAllLines(file);
                var pkg = "";
                var body = new StringBuilder();
                for (String line : lines) {
                    Matcher p = SourceHygieneTest.PACKAGE.matcher(line.strip());
                    if (p.matches()) {
                        pkg = p.group(1);
                    }
                    if (!SourceHygieneTest.IMPORT.matcher(line.strip()).lookingAt()) {
                        body.append(line).append('\n');
                    }
                }
                sources.add(new Source(file, pkg, lines, body.toString()));
            }
        }
        assertTrue(sources.size() > 100, "only " + sources.size() + " sources were inspected");
        return sources;
    }

    /**
     * An import whose name never appears again. They accumulate silently: nothing in the build
     * reports one, and a reader takes them for a real dependency of the file.
     */
    @Test
    void noSourceImportsWhatItDoesNotUse() throws IOException {
        var violations = new ArrayList<String>();
        for (Source source : SourceHygieneTest.sources()) {
            for (String line : source.lines()) {
                Matcher m = SourceHygieneTest.IMPORT.matcher(line.strip());
                if (!m.lookingAt() || m.group(3) != null) {
                    // An on-demand import names no single type, so there is nothing to look for.
                    continue;
                }
                var qualified = m.group(2);
                var simple = qualified.substring(qualified.lastIndexOf('.') + 1);
                if (!SourceHygieneTest.mentions(source.body(), simple)) {
                    violations.add(source.file() + " -> " + qualified);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "these imports are never used:\n" + String.join("\n", violations));
    }

    /**
     * An import of the file's own package. It compiles and means nothing, and it reads as though the
     * type came from somewhere else.
     */
    @Test
    void noSourceImportsItsOwnPackage() throws IOException {
        var violations = new ArrayList<String>();
        for (Source source : SourceHygieneTest.sources()) {
            for (String line : source.lines()) {
                Matcher m = SourceHygieneTest.IMPORT.matcher(line.strip());
                if (!m.lookingAt() || m.group(1) != null) {
                    // A static import of an own-package type imports a member, not the type.
                    continue;
                }
                var qualified = m.group(2);
                var owner = qualified.substring(0, Math.max(qualified.lastIndexOf('.'), 0));
                if (owner.equals(source.pkg())) {
                    violations.add(source.file() + " -> " + qualified);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "these imports name the file's own package:\n" + String.join("\n", violations));
    }
}
