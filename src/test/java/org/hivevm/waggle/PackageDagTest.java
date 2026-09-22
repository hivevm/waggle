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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The whole dependency graph of the pipeline, in one place (ADR-0019).
 *
 * <p>It used to be three tests — one for the model (ADR-0013), one for JJTree (ADR-0016), one for
 * the lexer (ADR-0012) — each written after that one edge had been crossed. Stating the graph is
 * what keeps the next edge from being discovered the same way.
 */
class PackageDagTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    private static final String W = "org.hivevm.waggle.";

    /** Everything may use these. */
    private static final List<String> COMMON =
            List.of("java.", "javax.", "org.jspecify.", "org.hivevm.core.");

    /** The root every inspected package sits under. */
    private static final String ROOT = "org.hivevm.";

    /**
     * What each package may depend on, beyond {@link #COMMON} and itself.
     *
     * <p>{@code model} keeps the one exception ADR-0013 carved out: the generated {@code Token},
     * which it holds for positions and verbatim token chains. {@code grammar} is where that token
     * now lives, so the model names it and nothing else there.
     */
    private static final Map<String, List<String>> ALLOWED = Map.ofEntries(
            Map.entry(W + "diag", List.of(W + "model.", W + "grammar.Token")),
            Map.entry(W + "model", List.of(W + "grammar.Token")),
            Map.entry(W + "lexer", List.of(W + "model.", W + "diag.", W + "api.")),
            Map.entry(W + "tree",
                    List.of(W + "model.", W + "diag.", W + "api.", "org.hivevm.source.")),
            Map.entry(W + "analysis", List.of(W + "model.", W + "diag.", W + "api.", W + "tree.",
                    "org.hivevm.source.")),
            Map.entry(W + "jjtree", List.of(W + "model.", W + "diag.", W + "tree.", W + "api.",
                    W + "grammar.", "org.hivevm.source.")),
            Map.entry(W + "codegen", List.of(W + "model.", W + "diag.", W + "api.", W + "tree.",
                    W + "lexer.", W + "analysis.", W + "grammar.", "org.hivevm.source.")),
            Map.entry(W + "grammar",
                    List.of(W + "model.", W + "diag.", W + "api.", "org.hivevm.source.")),
            Map.entry(W + "api", List.of(W + "model.", W + "diag.", W + "lexer.", W + "tree.",
                    W + "analysis.", W + "codegen.", W + "grammar.", "org.hivevm.source.")),
            Map.entry(W + "doc", List.of(W + "model.", W + "diag.", W + "api.", W + "grammar.",
                    "org.hivevm.source.")),

            // The three packages outside org.hivevm.waggle. The template engine is the leaf
            // ADR-0005 described: it may not name Waggle at all. It used to import
            // org.hivevm.waggle.api.Options, closing a cycle that no entry below could catch,
            // because the map was keyed by Waggle package and the walk followed its keys
            // (ADR-0023).
            Map.entry("org.hivevm.source", List.of()),
            Map.entry("org.hivevm.core", List.of()));

    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+);");

    /** Fragments of generated tree code: the runtime field and the scope variables. */
    private static final List<String> TREE_CODE = List.of("jjtree.", "jjtn", "jjtc", "jjte");

    /**
     * Imports were never the only way in. {@code NodeDescriptor.openNode()} returned the string
     * {@code "jjtree.openNodeScope(…);"} and {@code NodeScope} built the identifier {@code jjtn000},
     * so the language-independent model emitted target source text without importing anything — and
     * the text was Java and C++ only, since the Rust back end wrote its own (ADR-0016).
     */
    @Test
    void theModelEmitsNoTreeCode() throws IOException {
        var dir = PackageDagTest.SOURCES.resolve(Path.of("org", "hivevm", "waggle", "model"));
        var violations = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : (Iterable<Path>) files.filter(
                    p -> p.toString().endsWith(".java"))::iterator) {
                var number = 0;
                for (String line : Files.readAllLines(file)) {
                    number++;
                    for (String forbidden : PackageDagTest.TREE_CODE) {
                        if (line.contains(forbidden)) {
                            violations.add(dir.relativize(file) + ":" + number + " -> " + forbidden);
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "model must not spell out generated tree code (ADR-0016), but found:\n"
                        + String.join("\n", violations));
    }

    @Test
    void everyPackageStaysInsideTheGraph() throws IOException {
        var violations = new ArrayList<String>();
        var inspected = 0;

        for (var entry : PackageDagTest.ALLOWED.entrySet()) {
            var dir = PackageDagTest.SOURCES.resolve(
                    Path.of("", entry.getKey().split("\\.")));
            assertTrue(Files.isDirectory(dir), "no such package: " + dir);

            var own = entry.getKey() + ".";
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : (Iterable<Path>) files.filter(
                        p -> p.toString().endsWith(".java"))::iterator) {
                    inspected++;
                    for (String line : Files.readAllLines(file)) {
                        Matcher m = PackageDagTest.IMPORT.matcher(line.strip());
                        if (!m.matches()) {
                            continue;
                        }
                        var imported = m.group(1);
                        var ok = imported.startsWith(own)
                                || PackageDagTest.COMMON.stream().anyMatch(imported::startsWith)
                                || entry.getValue().stream().anyMatch(imported::startsWith);
                        if (!ok) {
                            violations.add(entry.getKey() + "/" + file.getFileName() + " -> "
                                    + imported);
                        }
                    }
                }
            }
        }

        assertTrue(inspected > 80, "only " + inspected + " sources were inspected");
        assertTrue(violations.isEmpty(),
                "the pipeline's dependency graph is a DAG (ADR-0019), but found:\n"
                        + String.join("\n", violations));
    }

    /**
     * Every package under {@code src/main/java} is in the graph.
     *
     * <p>Without this, a package joins the tree unconstrained: {@code org.hivevm.source} was never
     * inspected, so the back edge that made the graph cyclic was invisible to the test that states
     * it (ADR-0023).
     */
    @Test
    void everyPackageIsInTheGraph() throws IOException {
        var missing = new ArrayList<String>();
        try (Stream<Path> dirs = Files.walk(PackageDagTest.SOURCES)) {
            for (Path dir : (Iterable<Path>) dirs.filter(Files::isDirectory)::iterator) {
                try (Stream<Path> files = Files.list(dir)) {
                    if (files.noneMatch(f -> f.toString().endsWith(".java"))) {
                        continue;
                    }
                }
                var pkg = PackageDagTest.SOURCES.relativize(dir).toString()
                        .replace(java.io.File.separatorChar, '.');
                // A key covers its sub-packages: codegen states the rule for its back ends.
                if (PackageDagTest.ALLOWED.keySet().stream()
                        .noneMatch(k -> pkg.equals(k) || pkg.startsWith(k + "."))) {
                    missing.add(pkg);
                }
            }
        }
        assertTrue(missing.isEmpty(),
                "these packages are not in the dependency graph (ADR-0023):\n"
                        + String.join("\n", missing));
    }
}
