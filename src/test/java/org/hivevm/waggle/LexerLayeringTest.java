// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The lexer stage owns NFA/DFA construction; a back end only renders what it produced (ADR-0012).
 *
 * <p>The generators used to keep computing while they emitted:
 * {@code NfaState.GetStateSetIndicesForUse} grew the {@code jjnextStates} table from inside the code
 * that was rendering it, and {@code reArrange} re-derived an ordering the finished DFA already had.
 *
 * <p>The rule is enforced where it cannot be talked around: no class in {@code lexer} offers a
 * {@code public static} method, and everything that shapes the automaton is reachable only from
 * inside the package.
 *
 * <p>Since ADR-0029 a back end reads a finished plan, not the automaton: code generation may name
 * {@code LexerBuilder}, {@code LexerData} and the {@code LexerPlan} records, never {@code NfaState}, {@code NfaStateData}
 * or {@code DfaBuilder}, which are no longer public either.
 */
class LexerLayeringTest {

    private static final Path CODEGEN =
            Path.of("src", "main", "java", "org", "hivevm", "waggle", "codegen");

    private static final Pattern LEXER_IMPORT =
            Pattern.compile("^import\\s+(org\\.hivevm\\.waggle\\.lexer\\.[\\w.]+);");

    /**
     * What code generation may import from the lexer stage: the builder that runs it, what it
     * returns, and the plan.
     */
    private static final List<String> READABLE = List.of("org.hivevm.waggle.lexer.LexerBuilder",
            "org.hivevm.waggle.lexer.LexerData", "org.hivevm.waggle.lexer.LexerPlan");

    @Test
    void codeGenerationReadsOnlyThePlan() throws IOException {
        var violations = new ArrayList<String>();
        var inspected = 0;
        try (Stream<Path> files = Files.walk(LexerLayeringTest.CODEGEN)) {
            for (Path file : (Iterable<Path>) files.filter(
                    p -> p.toString().endsWith(".java"))::iterator) {
                inspected++;
                for (String line : Files.readAllLines(file)) {
                    Matcher m = LexerLayeringTest.LEXER_IMPORT.matcher(line.strip());
                    if (!m.matches()) {
                        continue;
                    }
                    var imported = m.group(1);
                    if (LexerLayeringTest.READABLE.stream().noneMatch(
                            r -> imported.equals(r) || imported.startsWith(r + "."))) {
                        violations.add(file.getFileName() + " -> " + imported);
                    }
                }
            }
        }

        assertTrue(inspected > 20, "only " + inspected + " generator sources were inspected");
        assertTrue(violations.isEmpty(),
                "code generation reads the lexer plan, not the automaton (ADR-0029):\n"
                        + String.join("\n", violations));
    }

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
                var type = LexerLayeringTest.classOf(name);
                for (var method : type.getDeclaredMethods()) {
                    var modifiers = method.getModifiers();
                    if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)
                            && !method.isSynthetic() && !LexerLayeringTest.isEnumMember(type, method)) {
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

    /**
     * The {@code values()} and {@code valueOf()} every enum has. The compiler declares them, and
     * they reach nothing but the constants of the plan (ADR-0029).
     */
    private static boolean isEnumMember(Class<?> type, Method method) {
        return type.isEnum() && (method.getName().equals("values")
                || method.getName().equals("valueOf"));
    }

    private static Class<?> classOf(String name) {
        try {
            return Class.forName(name, false, LexerLayeringTest.class.getClassLoader());
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new IllegalStateException("cannot inspect " + name, e);
        }
    }
}
