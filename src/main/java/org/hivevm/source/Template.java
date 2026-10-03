// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.hivevm.core.Environment;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
        APPLY
    }

    /**
     * Resolves a directive name. An unknown one (e.g. "//@endif", which this engine does not know —
     * it uses "//@fi") used to surface as a bare IllegalArgumentException from valueOf.
     */
    private static Function parse(String template, int line, String name) {
        try {
            return Function.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new TemplateException(template + ":" + line + ": unknown directive '//@"
                    + name.toLowerCase(Locale.ROOT)
                    + "' — known are: if, elif, else, fi, foreach, end, apply");
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
    // A backslash before a placeholder writes it out as it stands. C++ generated with DEPTH_LIMIT
    // defines __ERROR_RET__, which is a name of the target's own and not one of this engine's.
    //
    // Whether a directive takes its line with it is decided by where it stands, not by the
    // expression: see standalone() (ADR-0032).
    private static final Pattern STATEMENT = Pattern.compile(
            "(\\t*)//@(\\w+)(?:[ \\t]*\\(([^)]*)\\))?|\\\\(__[^_()]\\w*?__)|__([^_()]\\w*?)__",
            Pattern.MULTILINE);

    /**
     * Returns the parameter of a directive that requires one.
     */
    private static String require(String template, int line, String name, String param) {
        if ((param == null) || param.isEmpty()) {
            throw new TemplateException(template + ":" + line + ": //@"
                    + name.toLowerCase(Locale.ROOT) + " requires a parameter");
        }
        return param;
    }

    /**
     * Checks that a directive that takes no parameter has none. An empty list is allowed: it ends
     * the directive where text follows it directly, as in {@code //@fi()trace_call}, which would
     * otherwise be read as one name (ADR-0032). A parameter after {@code //@else} used to be
     * dropped without a word, and the text with it.
     */
    private static void none(String template, int line, String name, String param) {
        if ((param != null) && !param.isEmpty()) {
            throw new TemplateException(template + ":" + line + ": //@"
                    + name.toLowerCase(Locale.ROOT) + " takes no parameter, but has (" + param + ")");
        }
    }

    /**
     * A name a template refers to.
     *
     * @param directive {@code placeholder}, {@code if}, {@code elif},
     *                  {@code foreach} or {@code apply}; a condition is named without its negation
     * @param name      the name
     * @param line      the line it is written on
     * @param list      the list of the innermost {@code //@foreach} it sits in, whose element
     *                  answers it first, or {@code null}
     */
    public record Reference(String directive, String name, int line, String list) {
    }

    /** The parsed template; it depends on the text alone, so it is built once. */
    private final Renderer renderer;
    private final List<Reference> references;

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
        var builder = Template.build(name, text.replace("\r", ""));
        this.renderer = builder.build();
        this.references = builder.references();
    }

    /**
     * The names this template refers to, in the order it does. A test can check them against the
     * record the template renders, before any grammar is generated.
     */
    public final List<Reference> references() {
        return this.references;
    }

    /**
     * Where a template applied from this one is read from, and under which extension: the directory
     * {@code apply} next to it, and this template's own extension, so an applied template is a file
     * of the target language like every other (ADR-0005).
     *
     * <p>A resource path of {@code /templates/java/parser/Parser.java} resolves a record named
     * {@code ScanToken} to {@code /templates/java/parser/apply/ScanToken.java}. Applied templates
     * are siblings of each other, so one that applies a further template stays in that directory
     * rather than descending into another {@code apply}.
     */
    private static String applyBase(String name) {
        int slash = name.lastIndexOf('/');
        var directory = name.substring(0, slash + 1);
        int dot = name.indexOf('.', slash + 1);
        return (directory.endsWith("/apply/") ? directory : directory + "apply/") + "%s"
                + ((dot < 0) ? "" : name.substring(dot));
    }

    /** Renders into a printer a caller already owns, for a template applied from another. */
    final void renderInto(LinePrinter printer, Environment environment) {
        this.renderer.render(printer, environment);
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
    private static RendererBuilder build(String name, String text) {
        var builder = new RendererBuilder(name);

        var offset = 0;
        var line = 1;
        var matcher = Template.STATEMENT.matcher(text);
        while (matcher.find()) {
            if (matcher.start() > offset) {
                builder.at(line);
                builder.addText(text.substring(offset, matcher.start()));
                line += Template.lines(text, offset, matcher.start());
            }
            var at = line;
            builder.at(at);
            line += Template.lines(text, matcher.start(), matcher.end());
            offset = matcher.end();

            if (matcher.group(4) != null) {
                builder.addText(matcher.group(4)); // an escaped placeholder is text
                continue;
            }

            if (matcher.group(5) != null) {
                // A placeholder indents the lines of its value after the first as the first is
                // indented: by the tabs before it, when nothing else is (ADR-0032).
                var indent = Template.indent(text, matcher.start(), "");
                builder.addIndent(indent);
                builder.addVar(matcher.group(5));
                builder.addIndent(-indent);
                continue;
            }

            var func = matcher.group(2).toUpperCase(Locale.ROOT);
            var param = matcher.group(3);
            var tabs = matcher.group(1);
            int indent;
            if (Template.standalone(text, matcher.start(), matcher.end())) {
                // It owns its line: the tabs before it and the line break after it go with it.
                indent = tabs.length();
                if (offset < text.length()) {
                    offset++;
                    line++;
                }
            } else {
                // It owns nothing but itself: the tabs before it are text.
                builder.addText(tabs);
                indent = Template.indent(text, matcher.start(), tabs);
            }
            switch (Template.parse(name, at, func)) {
                case IF -> builder.addIf(Template.require(name, at, func, param));
                case ELIF -> builder.addBranch(Template.require(name, at, func, param));
                case ELSE -> {
                    Template.none(name, at, func, param);
                    builder.addBranch(null);
                }
                case FI -> {
                    Template.none(name, at, func, param);
                    builder.close("if");
                }
                case END -> {
                    Template.none(name, at, func, param);
                    builder.close("foreach");
                }
                case FOREACH -> builder.addForeach(Template.require(name, at, func, param));
                case APPLY -> {
                    builder.addIndent(indent);
                    builder.addApply(Template.require(name, at, func, param), Template.applyBase(name));
                    builder.addIndent(-indent);
                }
            }
        }

        if (offset < text.length()) {
            builder.at(line);
            builder.addText(text.substring(offset));
        }
        return builder;
    }

    /**
     * Whether the directive from {@code start} to {@code end} stands alone on its line: only tabs
     * before it, and the line break or the end of the template after it (ADR-0032).
     */
    private static boolean standalone(String text, int start, int end) {
        return ((start == 0) || (text.charAt(start - 1) == '\n'))
                && ((end == text.length()) || (text.charAt(end) == '\n'));
    }

    /**
     * How many levels the lines after the first of what is written at {@code start} are indented:
     * the number of tabs before it on its line, {@code tabs} being those the match itself holds, or
     * none when anything else stands before it.
     */
    private static int indent(String text, int start, String tabs) {
        var prefix = text.substring(text.lastIndexOf('\n', start - 1) + 1, start) + tabs;
        return prefix.chars().allMatch(c -> c == '\t') ? prefix.length() : 0;
    }

    /** How many line breaks {@code text} holds from {@code start} to {@code end}. */
    private static int lines(String text, int start, int end) {
        var count = 0;
        for (var i = text.indexOf('\n', start); (i >= 0) && (i < end); i = text.indexOf('\n', i + 1)) {
            count++;
        }
        return count;
    }
}
