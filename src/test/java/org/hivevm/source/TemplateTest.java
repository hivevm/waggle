// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.core.Environment;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.List;

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
        return new Template("Test", template).render("Test", new MapEnv(env));
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

    /**
     * A condition on a name the environment does not have is an error. It used to be false, so a
     * key that was never set dropped its block: VISITOR_RETURN_TYPE_VOID, set for the node classes
     * but not for Node, turned a void jjtAccept into one returning a value.
     */
    @Test
    void ifOnAnUnknownNameFails() {
        var e = assertThrows(TemplateException.class, () -> render("//@if(FLAG)\nyes\n//@fi\n", Map.of()));
        assertTrue(e.getMessage().contains("FLAG"), e.getMessage());
    }

    /** An //@else without an //@if is a template error, not an EmptyStackException. */
    @Test
    void elseOutsideAnIfIsATemplateError() {
        assertThrows(TemplateException.class, () -> render("a\n//@else\nb\n", Map.of()));
        assertThrows(TemplateException.class,
                () -> render("//@foreach(LIST)\n//@else\n//@end\n", Map.of("LIST", 1)));
    }

    /** The line after a directive is text, even when it starts with "(": it was taken as a parameter. */
    @Test
    void aLineStartingWithAParenthesisAfterElseIsKept() {
        var out = render("//@if(FLAG)\nyes\n//@else\n(void) x;\n//@fi\n", Map.of("FLAG", false));
        assertTrue(out.contains("(void) x;"), out);
    }

    /**
     * Under a Turkish default locale {@code "if".toUpperCase()} is {@code "İF"}, so every
     * {@code //@if} was an unknown directive and no template could be rendered.
     */
    @Test
    void directivesDoNotDependOnTheDefaultLocale() {
        var saved = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            var out = render("//@if(FLAG)\nyes\n//@fi\n", Map.of("FLAG", true));
            assertTrue(out.contains("yes"), out);
        } finally {
            Locale.setDefault(saved);
        }
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
    void negatedConditionOnAnUnknownNameFails() {
        assertThrows(TemplateException.class, () -> render("//@if(!FLAG)\nyes\n//@fi\n", Map.of()));
        assertThrows(TemplateException.class,
                () -> render("//@if(A)\na\n//@elif(FLAG)\nb\n//@fi\n", Map.of("A", false)));
    }

    /** The branches of an //@if are keyed by condition; a repeated one replaced the first. */
    @Test
    void aRepeatedBranchFails() {
        assertThrows(TemplateException.class,
                () -> render("//@if(A)\na\n//@elif(A)\nb\n//@fi\n", Map.of("A", true)));
        assertThrows(TemplateException.class,
                () -> render("//@if(A)\na\n//@else\nb\n//@else\nc\n//@fi\n", Map.of("A", true)));
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

    @Test
    void foreachOverAnUnknownNameFails() {
        assertThrows(TemplateException.class, () -> render("//@foreach(ITEMS)\nx\n//@end\n", Map.of()));
    }

    /** Neither a count nor an iterable: an array or a map rendered nothing. */
    @Test
    void foreachOverSomethingNotIterableFails() {
        assertThrows(TemplateException.class,
                () -> render("//@foreach(ITEMS)\nx\n//@end\n", Map.of("ITEMS", new int[] {1, 2})));
    }

    // ---------------------------------------------------------------- apply

    /** A leaf of the tree an applied template renders. */
    record Leaf(String text) {
    }

    /** A node of it; its children are rendered by the template each of them names (ADR-0031). */
    record Node(String name, List<Object> children) {
    }

    /** What the applied templates rendered, without the banner and the checksum around it. */
    private static String apply(Object value, Map<String, Object> env) {
        var values = new java.util.LinkedHashMap<String, Object>(env);
        values.put("ROOT", value);
        return body(new Template("/org/hivevm/source/Outer.java", "//@apply(ROOT)\n")
                .render("Test", new MapEnv(values)));
    }

    private static String body(String rendered) {
        var lines = new java.util.ArrayList<>(rendered.lines().skip(2)
                .takeWhile(l -> !l.startsWith("// Checksum=")).toList());
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }

    @Test
    void applyRendersTheTemplateNamedAfterTheRecordsType() {
        assertEquals("leaf a in u;\n", apply(new Leaf("a"), Map.of("UNIT", "u")));
    }

    /** A list under one attribute renders the template of each element in turn. */
    @Test
    void applyRendersEveryElementOfAList() {
        var out = apply(new Node("n", List.of(new Leaf("a"), new Leaf("b"))), Map.of("UNIT", "u"));
        assertEquals("node n {\n    leaf a in u;\n    leaf b in u;\n}\n", out);
    }

    /** The point of the directive: a template that applies itself follows a nested structure. */
    @Test
    void applyRecursesIntoNestedRecords() {
        var tree = new Node("outer", List.of(new Leaf("a"),
                new Node("inner", List.of(new Leaf("b"))), new Leaf("c")));
        assertEquals("""
                node outer {
                    leaf a in u;
                    node inner {
                        leaf b in u;
                    }
                    leaf c in u;
                }
                """, apply(tree, Map.of("UNIT", "u")));
    }

    /** An applied template reads the options of the generation it runs in, not only the record. */
    @Test
    void anAppliedTemplateStillSeesTheSurroundingEnvironment() {
        assertTrue(apply(new Leaf("a"), Map.of("UNIT", "the-unit")).contains("the-unit"));
    }

    /** Nothing bound means nothing rendered, as an absent optional part of a plan should be. */
    @Test
    void applyRendersNothingForNull() {
        var values = new java.util.HashMap<String, Object>();
        values.put("ROOT", null);
        assertEquals("", body(new Template("/org/hivevm/source/Outer.java", "//@apply(ROOT)\n")
                .render("Test", new MapEnv(values))));
    }

    /** A value that is not a record has no components; rendering it would say nothing. */
    @Test
    void applyToSomethingThatIsNotARecordFails() {
        assertThrows(TemplateException.class, () -> apply("plain text", Map.of("UNIT", "u")));
    }

    @Test
    void applyToAnUnknownAttributeFails() {
        assertThrows(TemplateException.class,
                () -> new Template("/org/hivevm/source/Outer.java", "//@apply(NOPE)\n")
                        .render("Test", new MapEnv(Map.of())));
    }

    // ---------------------------------------------------------------- errors

    /**
     * A broken template is named by its path. The render title — the "HiveVM Waggle v.X" banner —
     * used to stand in for the name, which said nothing about which template was broken.
     */
    @Test
    void aBrokenTemplateIsNamedByItsPath() {
        var path = "/org/hivevm/source/Unclosed.template";
        var e = assertThrows(TemplateException.class, () -> TemplateCache.get(path));
        assertTrue(e.getMessage().startsWith(path + ": "), e.getMessage());
    }
}
