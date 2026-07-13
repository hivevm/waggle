package org.hivevm.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.core.Environment;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Tests for the template engine.
 *
 * <p>The engine is what turns the templates under {@code src/main/resources/templates} into source.
 * When it drops a block or a placeholder silently, the result is code that does not compile — which
 * is exactly how the missing {@code jj_ntk_f}/{@code JJCalls} declarations came about. So the
 * behaviour under test here is mostly: <em>fail loudly instead of emitting something broken</em>.
 */
class TemplateTest {

    /** A plain map-backed environment. */
    private record MapEnv(Map<String, Object> values) implements Environment {

        @Override
        public boolean has(String name) {
            return this.values.containsKey(name);
        }

        @Override
        public Object get(String name) {
            return this.values.get(name);
        }
    }

    private static String render(String template, Map<String, Object> env) {
        var out = new ByteArrayOutputStream();
        new Template(template).render("Test", out, new MapEnv(env));
        return out.toString(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- conditions

    @Test
    void ifRendersWhenTrue() {
        var out = render("//@if(FLAG)\nyes\n//@fi\n", Map.of("FLAG", true));
        assertTrue(out.contains("yes"), out);
    }

    @Test
    void ifIsSkippedWhenFalse() {
        var out = render("//@if(FLAG)\nyes\n//@fi\n", Map.of("FLAG", false));
        assertFalse(out.contains("yes"), out);
    }

    @Test
    void ifIsSkippedWhenAbsent() {
        var out = render("//@if(FLAG)\nyes\n//@fi\n", Map.of());
        assertFalse(out.contains("yes"), out);
    }

    /**
     * A negated condition must render when the flag is false. This is what the C++ and Rust
     * templates rely on (e.g. {@code //@if(!CACHE_TOKENS)} guarding the {@code jj_ntk} declaration).
     */
    @Test
    void negatedConditionRendersWhenFlagIsFalse() {
        var out = render("//@if(!FLAG)\nyes\n//@fi\n", Map.of("FLAG", false));
        assertTrue(out.contains("yes"), "negated condition did not render: " + out);
    }

    @Test
    void negatedConditionRendersWhenFlagIsAbsent() {
        var out = render("//@if(!FLAG)\nyes\n//@fi\n", Map.of());
        assertTrue(out.contains("yes"), "negated condition did not render: " + out);
    }

    @Test
    void negatedConditionIsSkippedWhenFlagIsTrue() {
        var out = render("//@if(!FLAG)\nyes\n//@fi\n", Map.of("FLAG", true));
        assertFalse(out.contains("yes"), out);
    }

    @Test
    void elseRendersWhenConditionIsFalse() {
        var out = render("//@if(FLAG)\nyes\n//@else\nno\n//@fi\n", Map.of("FLAG", false));
        assertTrue(out.contains("no"), out);
        assertFalse(out.contains("yes"), out);
    }

    /** With two satisfied branches the <em>first one in the source</em> must win, not a hash order. */
    @Test
    void elifPicksTheFirstMatchingBranchInSourceOrder() {
        var template = """
                //@if(A)
                first
                //@elif(B)
                second
                //@fi
                """;
        var out = render(template, Map.of("A", true, "B", true));
        assertTrue(out.contains("first"), out);
        assertFalse(out.contains("second"), out);
    }

    // ---------------------------------------------------------------- placeholders

    @Test
    void placeholderIsSubstituted() {
        var out = render("value=__NAME__\n", Map.of("NAME", "abc"));
        assertTrue(out.contains("value=abc"), out);
    }

    /**
     * An unknown placeholder must not silently render as the empty string — that is how
     * {@code class ASTx extends  { }} and the untyped {@code jjtAccept} parameter were produced.
     */
    @Test
    void unknownPlaceholderFails() {
        assertThrows(RuntimeException.class,
                () -> render("value=__MISSING__\n", Map.of()),
                "an unknown placeholder must be reported, not rendered as \"\"");
    }

    // ---------------------------------------------------------------- balance

    /**
     * A missing {@code //@fi} must be reported. Today the builder returns the innermost renderer,
     * so everything after the unclosed block is dropped — silently, with a valid checksum footer.
     */
    @Test
    void missingFiFails() {
        assertThrows(RuntimeException.class,
                () -> render("//@if(FLAG)\nyes\ntail\n", Map.of("FLAG", true)),
                "an unbalanced //@if must be reported");
    }

    @Test
    void surplusFiFails() {
        assertThrows(RuntimeException.class,
                () -> render("//@if(FLAG)\nyes\n//@fi\n//@fi\n", Map.of("FLAG", true)),
                "a surplus //@fi must be reported");
    }

    /** Content after a properly closed block must survive. */
    @Test
    void contentAfterBlockSurvives() {
        var out = render("//@if(FLAG)\nyes\n//@fi\ntail\n", Map.of("FLAG", true));
        assertTrue(out.contains("yes"), out);
        assertTrue(out.contains("tail"), "content after the block was dropped: " + out);
    }

    // ---------------------------------------------------------------- foreach

    @Test
    void foreachRepeatsItsBody() {
        var out = render("//@foreach(ITEMS)\nx\n//@end\n", Map.of("ITEMS", 3));
        assertEquals(3, out.lines().filter(l -> l.equals("x")).count(), out);
    }
}
