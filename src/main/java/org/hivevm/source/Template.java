// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.hivevm.core.Environment;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
            throw new TemplateException(template + ": unknown directive '//@" + name.toLowerCase()
                    + "' — known are: if, elif, else, fi, foreach, end, invoke");
        }
    }

    // "\\s*" before the parameter list: "//@if (X)" used to leave the parameter unmatched, which
    // silently turned the condition into "no condition" and dropped the block.
    //
    // The placeholder body is reluctant ("\\w*?"): the previous greedy form ran across two adjacent
    // placeholders — "__A__ __B__" was captured as the single name "A__ " — so neither was
    // substituted. The leading "[^_()]" stays: it is what lets a name be glued to a prefix that ends
    // in an underscore, as in "jjbitVec___TOKEN_MASKS_INDEX__".
    private static final Pattern STATEMENT = Pattern.compile(
            "(\\t*)//@(\\w+)(?:\\s*\\(([^)]+)\\))?\\v?|__([^_()]\\w*?)__",
            Pattern.MULTILINE);

    /**
     * Returns the parameter of a directive that requires one.
     */
    private static String require(String template, String name, String param) {
        if (param == null) {
            throw new TemplateException(
                    template + ": //@" + name.toLowerCase() + " requires a parameter");
        }
        return param;
    }


    private final String text;

    /**
     * Constructs a new instance of the Template class using the provided byte array.
     */
    public Template(byte[] bytes) {
        this(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Constructs a new instance of the Template class using the provided byte array.
     */
    public Template(String text) {
        this.text = text.replace("\r", "");
    }

    /**
     * Renders the template content using the provided environment and writes the result to the
     * specified output stream. The method processes template commands and uses a hierarchical
     * structure to construct and render the output.
     */
    public final void render(String title, OutputStream outputStream, Environment environment) {
        var builder = new RendererBuilder(title);

        var offset = 0;
        var matcher = Template.STATEMENT.matcher(text);
        while (matcher.find()) {
            if (matcher.start() > offset) {
                builder.addText(text.substring(offset, matcher.start()));
            }
            offset = matcher.end();

            var isFunc = matcher.group(4) == null;
            var func = isFunc ? matcher.group(2).toUpperCase() : "VAR";
            var param = matcher.group(isFunc ? 3 : 4);
            switch (Template.parse(title, func)) {
                case IF:
                    builder.addMatch(Template.require(title, func, param));
                    break;

                case ELIF:
                    builder.addCase(Template.require(title, func, param));
                    break;

                case ELSE:
                    builder.addCase(param);
                    break;

                case FI:
                    builder.pop();
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

        try (var writer = TemplateWriter.create(title, outputStream, environment)) {
            builder.build().render(writer, writer);
        }
    }

    /**
     * Creates a new instance of {@link Context} using the provided {@link Environment}. The
     * returned context is designed to manage key-value pairs and interact with the given
     * environment.
     */
    public static Context newContext(Environment environment) {
        return new TemplateContext(environment);
    }
}
