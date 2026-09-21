package org.hivevm.waggle;

import org.hivevm.waggle.api.WaggleCompiler;

import org.hivevm.waggle.api.GenerationException;

import org.hivevm.waggle.api.GenerationRequest;

import org.hivevm.waggle.api.Language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.waggle.diag.DiagnosticSink;
import org.hivevm.waggle.diag.Diagnostics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

/**
 * A generation's errors belong to that generation (ADR-0015).
 *
 * <p>They used to belong to the JVM: three static counters in {@code JavaCCErrors}, reset by whoever
 * started a generation next. A second generation therefore wiped the first one's verdict, and two at
 * once corrupted both — which is what the build cache and parallel task execution were about to make
 * routine.
 */
class GenerationIsolationTest {

    /** Refers to a token no {@code TOKEN} block defines: one semantic error, reliably. */
    private static final String BROKEN = """
            grammar Broken;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input = < UNDEFINED_TOKEN > <EOF> ;
            """;

    private static final String CLEAN = """
            grammar Clean;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input = < WORD > <EOF> ;

            TOKEN = < WORD: (["a"-"z"])+ > ;
            """;

    @Test
    void concurrentGenerationsKeepTheirOwnErrors(@TempDir Path dir) throws Exception {
        var broken = write(dir, "Broken.waggle", GenerationIsolationTest.BROKEN);
        var clean = write(dir, "Clean.waggle", GenerationIsolationTest.CLEAN);

        var brokenDiagnostics = new Diagnostics(DiagnosticSink.SILENT);
        var cleanDiagnostics = new Diagnostics(DiagnosticSink.SILENT);

        // Both threads start together, so the two generations really do overlap.
        var start = new CountDownLatch(1);
        var one = generateOn(start, broken, dir.resolve("out-broken"), brokenDiagnostics);
        var two = generateOn(start, clean, dir.resolve("out-clean"), cleanDiagnostics);

        one.start();
        two.start();
        start.countDown();
        one.join();
        two.join();

        assertTrue(brokenDiagnostics.hasError(),
                "the broken grammar must report its own error");
        assertEquals(0, cleanDiagnostics.errorCount(),
                "the clean grammar must not inherit the other generation's error: "
                        + cleanDiagnostics.collected());
        assertTrue(Files.isRegularFile(dir.resolve("out-clean/org/example/Parser.java")),
                "the clean grammar must still have been generated");
    }

    @Test
    void aSecondGenerationDoesNotInheritTheFirstOne(@TempDir Path dir) throws IOException {
        var broken = write(dir, "Broken.waggle", GenerationIsolationTest.BROKEN);
        var clean = write(dir, "Clean.waggle", GenerationIsolationTest.CLEAN);

        var first = new Diagnostics(DiagnosticSink.SILENT);
        try {
            compiler(broken, dir.resolve("out-1"), first).parse();
        } catch (GenerationException expected) {
            // The broken grammar fails, which is the point of it (ADR-0011).
        }
        assertTrue(first.hasError());

        var second = new Diagnostics(DiagnosticSink.SILENT);
        compiler(clean, dir.resolve("out-2"), second).parse();

        assertFalse(second.hasError(),
                "the second generation started from a clean slate: " + second.collected());
    }

    /**
     * The counters are gone, and nothing else may take their place: a mutable static under
     * {@code org.hivevm.waggle} is shared by every generation in the JVM, and Gradle shares
     * classloaders — and therefore statics — between concurrently executing tasks. The compiled
     * classes are asked rather than the sources, so a field is judged by what it actually is.
     *
     * <p>Only the hand-written classes are in scope. The generated lexer's lookup tables
     * ({@code jjbitVec0}, {@code lexStateNames}) and {@code ParseException.EOL} are static without
     * being final, but they are written once at class initialisation and never again — and they come
     * out of the templates, so declaring them final is a change to emitted code and needs two
     * releases (ADR-0009, ADR-0013).
     */
    @Test
    void noMutableStaticStateRemains() throws IOException {
        var classes = Path.of("build", "classes", "java", "main");
        var sources = Path.of("src", "main", "java");
        var root = classes.resolve(Path.of("org", "hivevm", "waggle"));
        assertTrue(Files.isDirectory(root),
                "compiled classes not found at " + root.toAbsolutePath() + " (run ./gradlew test)");

        var violations = new ArrayList<String>();
        var inspected = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(
                    p -> p.toString().endsWith(".class"))::iterator) {
                var relative = classes.relativize(file).toString();
                var name = relative.replace(java.io.File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                if (!Files.isRegularFile(sources.resolve(
                        relative.replaceAll("(\\$.*)?\\.class$", "") + ".java"))) {
                    continue;
                }
                inspected++;
                for (var field : classOf(name).getDeclaredFields()) {
                    var modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)
                            && !field.isSynthetic()) {
                        violations.add(name + "." + field.getName());
                    }
                }
            }
        }

        // Without this the test would pass just as happily if the filter matched nothing.
        assertTrue(inspected > 50, "only " + inspected + " hand-written classes were inspected");
        assertTrue(violations.isEmpty(),
                "no mutable static state may live under org.hivevm.waggle (ADR-0015):\n"
                        + String.join("\n", violations));
    }

    private static Class<?> classOf(String name) {
        try {
            return Class.forName(name, false, GenerationIsolationTest.class.getClassLoader());
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new IllegalStateException("cannot inspect " + name, e);
        }
    }

    private static Thread generateOn(CountDownLatch start, Path grammar, Path target,
            Diagnostics diagnostics) {
        return new Thread(() -> {
            try {
                start.await();
                compiler(grammar, target, diagnostics).parse();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (GenerationException expected) {
                // A broken grammar fails; the test asks the diagnostics, not the exception.
            }
        });
    }

    private static WaggleCompiler compiler(Path grammar, Path target, Diagnostics diagnostics) {
        return new WaggleCompiler(
                new GenerationRequest(grammar.toFile(), Language.JAVA, target.toFile(), List.of()),
                diagnostics);
    }

    private static Path write(Path dir, String name, String grammar) throws IOException {
        var source = dir.resolve(name);
        Files.writeString(source, grammar);
        return source;
    }
}
