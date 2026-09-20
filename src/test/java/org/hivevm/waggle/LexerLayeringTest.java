// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

/**
 * The lexer stage owns NFA/DFA construction; a back end only renders what it produced (ADR-0012).
 *
 * <p>The generators used to keep computing while they emitted:
 * {@code NfaState.GetStateSetIndicesForUse} grew the {@code jjnextStates} table from inside the code
 * that was rendering it, and {@code reArrange} re-derived an ordering the finished DFA already had.
 *
 * <p>The rule is enforced where it cannot be talked around: no class in {@code lexer} offers a
 * {@code public static} method. Everything a back end needs is an instance accessor on
 * {@code LexerData}, {@code NfaStateData} or {@code NfaState}, and everything that shapes the
 * automaton is reachable only from inside the package.
 */
class LexerLayeringTest {

    @Test
    void theLexerExposesNoStaticEntryPoints() throws IOException {
        var classes = Path.of("build", "classes", "java", "main");
        var root = classes.resolve(Path.of("org", "hivevm", "waggle", "lexer"));
        assertTrue(Files.isDirectory(root),
                "compiled classes not found at " + root.toAbsolutePath() + " (run ./gradlew test)");

        var violations = new ArrayList<String>();
        var inspected = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(
                    p -> p.toString().endsWith(".class"))::iterator) {
                var name = classes.relativize(file).toString()
                        .replace(java.io.File.separatorChar, '.').replaceAll("\\.class$", "");
                inspected++;
                for (var method : LexerLayeringTest.classOf(name).getDeclaredMethods()) {
                    var modifiers = method.getModifiers();
                    if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)
                            && !method.isSynthetic()) {
                        violations.add(name + "." + method.getName() + "()");
                    }
                }
            }
        }

        assertTrue(inspected > 5, "only " + inspected + " lexer classes were inspected");
        assertTrue(violations.isEmpty(),
                "the lexer stage must expose no static entry point to a back end (ADR-0012):\n"
                        + String.join("\n", violations));
    }

    private static Class<?> classOf(String name) {
        try {
            return Class.forName(name, false, LexerLayeringTest.class.getClassLoader());
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new IllegalStateException("cannot inspect " + name, e);
        }
    }
}
