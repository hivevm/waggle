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
                () -> render("//@foreach(LIST)\n//@else\n//@end\n", Map.of("LIST", List.of())));
    }

    /** An //@elif after the //@else could never be taken; the branches used to sit in a map. */
    @Test
    void anElifAfterTheElseFails() {
        var e = assertThrows(TemplateException.class,
                () -> render("//@if(A)\na\n//@else\nb\n//@elif(B)\nc\n//@fi\n", Map.of()));
        assertTrue(e.getMessage().contains("Test:5:"), e.getMessage());
        assertTrue(e.getMessage().contains("line 1"), e.getMessage());
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

    /** A repeated condition can never be taken, and a second //@else neither. */
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

    // ---------------------------------------------------------------- whitespace (ADR-0032)

    /** What the template rendered, without the banner and the checksum around it. */
    private static String text(String template, Map<String, Object> env) {
        return body(render(template, env));
    }

    /** A directive alone on its line takes the line with it, tabs and line break included. */
    @Test
    void aStandaloneDirectiveOwnsItsLine() {
        assertEquals("a\nb\nc\n", text("a\n\t//@if(A)\nb\n\t//@fi\nc\n", Map.of("A", true)));
    }

    /**
     * A directive next to text keeps the line break after it. It used to eat it, so a template
     * could splice three of its lines into one line of output.
     */
    @Test
    void anInlineDirectiveOwnsNothingButItself() {
        assertEquals("x = a;\nnext\n",
                text("x = //@if(A)a//@else()b//@fi;\nnext\n", Map.of("A", true)));
        // The break after an inline //@if is text of its branch; it used to be dropped.
        assertEquals("a\nb\nc\n", text("a//@if(A)\nb\n//@fi\nc\n", Map.of("A", true)));
    }

    /** The tabs before an inline directive are text, as they are before anything else. */
    @Test
    void theTabsBeforeAnInlineDirectiveAreText() {
        assertEquals("{\n    a }\n", text("{\n\t//@if(A)a//@fi }\n", Map.of("A", true)));
    }

    /** An empty parameter list ends a directive where text follows it directly. */
    @Test
    void anEmptyParameterListEndsADirective() {
        assertEquals("if (x) trace();\n",
                text("//@if(A)if (x) //@fi()trace();\n", Map.of("A", true)));
        assertEquals("trace();\n", text("//@if(A)if (x) //@fi()trace();\n", Map.of("A", false)));
    }

    /** A parameter on a directive that takes none was dropped, and the text with it. */
    @Test
    void aParameterOnElseFiOrEndFails() {
        var e = assertThrows(TemplateException.class,
                () -> render("//@if(A)a//@else(x == 1) ? b : c//@fi\n", Map.of("A", true)));
        assertTrue(e.getMessage().startsWith("Test:1: //@else takes no parameter"), e.getMessage());
        assertThrows(TemplateException.class, () -> render("//@if(A)\n//@fi(A)\n", Map.of()));
    }

    /** {@code //@invoke} is gone: a placeholder indents what it writes (ADR-0032). */
    @Test
    void invokeIsNoDirective() {
        assertThrows(TemplateException.class, () -> render("//@invoke(A)\n", Map.of("A", "a")));
    }

    /** Every line of a value is indented by the tabs before its placeholder, not only the first. */
    @Test
    void aPlaceholderIndentsWhatItWrites() {
        assertEquals("{\n    a\n    b\n}\n", text("{\n\t__A__\n}\n", Map.of("A", "a\nb")));
    }

    /** After other text the lines of a value stay where the value puts them. */
    @Test
    void aPlaceholderAfterTextDoesNotIndent() {
        assertEquals("x = a\nb;\n", text("x = __A__;\n", Map.of("A", "a\nb")));
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

    /** An element of a list a //@foreach walks. */
    record Item(String name, int value) {
    }

    @Test
    void foreachRepeatsItsBody() {
        var out = render("//@foreach(ITEMS)\nx\n//@end\n",
                Map.of("ITEMS", List.of(new Item("a", 1), new Item("b", 2), new Item("c", 3))));
        assertEquals(3, out.lines().filter(l -> l.equals("x")).count(), out);
    }

    /** A pass reads its element's components by name, and every other name around it. */
    @Test
    void foreachReadsTheComponentsOfItsElement() {
        var out = render("//@foreach(ITEMS)\nint __name__ = __value__; // __UNIT__\n//@end\n",
                Map.of("ITEMS", List.of(new Item("a", 1), new Item("b", 2)), "UNIT", "u"));
        assertTrue(out.contains("int a = 1; // u\nint b = 2; // u\n"), out);
    }

    /**
     * A count is not a list. It used to be one, with the element read through functions bound
     * under names of their own, such as {@code TOKENS_LABEL}.
     */
    @Test
    void foreachOverACountFails() {
        assertThrows(TemplateException.class,
                () -> render("//@foreach(ITEMS)\nx\n//@end\n", Map.of("ITEMS", 3)));
    }

    /** An element that is not a record has no components to read. */
    @Test
    void foreachOverSomethingNotARecordFails() {
        var e = assertThrows(TemplateException.class,
                () -> render("a\n//@foreach(ITEMS)\nx\n//@end\n", Map.of("ITEMS", List.of("x"))));
        assertTrue(e.getMessage().startsWith("Test:2: //@foreach(ITEMS) needs a record"), e.getMessage());
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
     * A broken template is named by its path, and the block left open by the line that opens it.
     * The render title — the "HiveVM Waggle v.X" banner — used to stand in for the name, which said
     * nothing about which template was broken.
     */
    @Test
    void aBrokenTemplateIsNamedByItsPath() {
        var path = "/org/hivevm/source/Unclosed.template";
        var e = assertThrows(TemplateException.class, () -> TemplateCache.get(path));
        assertTrue(e.getMessage().startsWith(path + ":1: //@if is never closed"), e.getMessage());
    }

    /**
     * A name that is missing at render time is reported with the template and the line that uses
     * it. It used to be reported by name alone, which left 140 small templates per target to search.
     */
    @Test
    void anUnknownNameIsReportedWithItsLine() {
        var e = assertThrows(TemplateException.class,
                () -> render("a\nb\n\tvalue=__MISSING__\n", Map.of()));
        assertTrue(e.getMessage().startsWith("Test:3: "), e.getMessage());

        e = assertThrows(TemplateException.class,
                () -> render("a\n//@if(A)\nb\n//@elif(MISSING)\n//@fi\n", Map.of("A", false)));
        assertTrue(e.getMessage().startsWith("Test:4: "), e.getMessage());
    }

    /** A line counts as one however many directives and placeholders precede it. */
    @Test
    void linesAreCountedAcrossDirectives() {
        var e = assertThrows(TemplateException.class, () -> render(
                "//@if(A)\n__A__ __A__\n//@else\nx\n//@fi\n\n__MISSING__\n", Map.of("A", true)));
        assertTrue(e.getMessage().startsWith("Test:7: "), e.getMessage());
    }

    /** A block closed by the other block's directive names the line that opened it. */
    @Test
    void aBlockClosedWithTheWrongDirectiveFails() {
        var e = assertThrows(TemplateException.class,
                () -> render("//@foreach(ITEMS)\nx\n//@fi\n", Map.of()));
        assertTrue(e.getMessage().startsWith("Test:3: //@fi closes the //@foreach on line 1"),
                e.getMessage());
        assertThrows(TemplateException.class, () -> render("//@if(A)\nx\n//@end\n", Map.of()));
    }

    /** A record whose type has no template is named, with the line that applies it. */
    @Test
    void applyWithoutATemplateNamesTheRecord() {
        var e = assertThrows(TemplateException.class, () -> apply(new Item("a", 1), Map.of()));
        assertTrue(e.getMessage().contains("/org/hivevm/source/Outer.java:1: //@apply(ROOT)"),
                e.getMessage());
        assertTrue(e.getMessage().contains("apply/Item.java"), e.getMessage());
    }

    // ---------------------------------------------------------------- references

    /** What a template refers to is known without rendering it, for a check against its record. */
    @Test
    void aTemplateListsTheNamesItRefersTo() {
        var template = new Template("Test", """
                __A__
                //@if(!B)
                //@foreach(LIST)
                	__name__
                	//@apply(child)
                //@end
                //@elif(C)
                	__D__
                //@fi
                """);
        assertEquals(List.of(
                        new Template.Reference("placeholder", "A", 1, null),
                        new Template.Reference("if", "B", 2, null),
                        new Template.Reference("foreach", "LIST", 3, null),
                        new Template.Reference("placeholder", "name", 4, "LIST"),
                        new Template.Reference("apply", "child", 5, "LIST"),
                        new Template.Reference("elif", "C", 7, null),
                        new Template.Reference("placeholder", "D", 8, null)),
                template.references());
    }
}
