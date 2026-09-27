// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.hivevm.core.Environment;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Represents a template rendering system that interprets a set of commands embedded within input
 * data, allowing conditional logic and repetitive constructs. A template is processed with an
 * underlying environment, and the output is written to a specified {@link java.io.Writer}.
 * <p>
 * Commands such as "if", "elif", "else", "foreach", and their corresponding closing commands are
 * parsed and constructed into a tree structure, which is then rendered dynamically based on the
 * provided environment.
 */
public class Template {

    private enum Function {
        IF,
        ELIF,
        ELSE,
        FOREACH,
        FI,
        END,
        VAR,
        INVOKE
    }

    /**
     * Resolves a directive name. An unknown one (e.g. "//@endif", which this engine does not know —
     * it uses "//@fi") used to surface as a bare IllegalArgumentException from valueOf.
     */
    private static Function parse(String template, String name) {
        try {
            return Function.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new TemplateException(template + ": unknown directive '//@" + name.toLowerCase(Locale.ROOT)
                    + "' — known are: if, elif, else, fi, foreach, end, invoke");
        }
    }

    // "[ \\t]*" before the parameter list: "//@if (X)" used to leave the parameter unmatched, which
    // silently turned the condition into "no condition" and dropped the block. Not "\\s*", which
    // also crossed the line end and took a line starting with "(" after //@else as its parameter.
    //
    // The placeholder body is reluctant ("\\w*?"): the previous greedy form ran across two adjacent
    // placeholders — "__A__ __B__" was captured as the single name "A__ " — so neither was
    // substituted. The leading "[^_()]" stays: it is what lets a name be glued to a prefix that ends
    // in an underscore, as in "jjbitVec___TOKEN_MASKS_INDEX__".
    private static final Pattern STATEMENT = Pattern.compile(
            "(\\t*)//@(\\w+)(?:[ \\t]*\\(([^)]+)\\))?\\v?|__([^_()]\\w*?)__",
            Pattern.MULTILINE);

    /**
     * Returns the parameter of a directive that requires one.
     */
    private static String require(String template, String name, String param) {
        if (param == null) {
            throw new TemplateException(
                    template + ": //@" + name.toLowerCase(Locale.ROOT) + " requires a parameter");
        }
        return param;
    }

    /** The parsed template; it depends on the text alone, so it is built once. */
    private final Renderer renderer;

    /**
     * Parses the UTF-8 template {@code bytes}; {@code name}, usually its resource path, names it in
     * a {@link TemplateException}.
     */
    public Template(String name, byte[] bytes) {
        this(name, new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Parses the template {@code text}; {@code name}, usually its resource path, names it in a
     * {@link TemplateException}.
     */
    public Template(String name, String text) {
        this.renderer = Template.build(name, text.replace("\r", ""));
    }

    /**
     * Renders the template against {@code environment} and returns the source it produces.
     *
     * <p>It opens nothing: where the text goes is the caller's decision, expressed as an
     * {@link OutputSink} (ADR-0018). The checksum and the option list at the end are part of the
     * rendered text, not of writing it.
     */
    public final String render(String title, Environment environment) {
        var out = new java.io.ByteArrayOutputStream();
        try (var writer = TemplateWriter.create(title, out, environment)) {
            this.renderer.render(writer, writer);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * Builds the renderer tree. It used to be rebuilt on every render, so a template rendered once
     * per node type was re-parsed each time.
     */
    @SuppressWarnings("fallthrough")
    private static Renderer build(String name, String text) {
        var builder = new RendererBuilder(name);

        var offset = 0;
        var matcher = Template.STATEMENT.matcher(text);
        while (matcher.find()) {
            if (matcher.start() > offset) {
                builder.addText(text.substring(offset, matcher.start()));
            }
            offset = matcher.end();

            var isFunc = matcher.group(4) == null;
            var func = isFunc ? matcher.group(2).toUpperCase(Locale.ROOT) : "VAR";
            var param = matcher.group(isFunc ? 3 : 4);
            switch (Template.parse(name, func)) {
                case IF:
                    builder.addMatch(Template.require(name, func, param));
                    break;

                case ELIF:
                    builder.addCase(Template.require(name, func, param));
                    break;

                case ELSE:
                    builder.addCase(param);
                    break;

                // //@if pushes two renderers - the match and the list of its first case -
                // so closing one pops twice, which is //@end's single pop plus one.
                case FI:
                    builder.pop();
                    // fall through
                case END:
                    builder.pop();
                    break;

                case FOREACH:
                    builder.addForeach(param);
                    break;

                case VAR:
                    builder.addVar(param);
                    break;

                case INVOKE:
                    var intend = matcher.group(1).length();
                    builder.setIntend(intend);
                    builder.addVar(param);
                    builder.setIntend(-intend);
                    break;

                default:
            }
        }

        if (offset < text.length()) {
            builder.addText(text.substring(offset));
        }
        return builder.build();
    }
}
