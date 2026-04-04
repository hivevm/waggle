// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.hivevm.core.Environment;

/**
 * Represents a template rendering system that interprets a set of commands embedded within input
 * data, allowing conditional logic and repetitive constructs. A template is processed with an
 * underlying environment, and the output is written to a specified {@link PrintWriter}.
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

    private static final Pattern STATEMENT = Pattern.compile(
        "(\\h*)//@(\\w+)(?:\\(([^)]+)\\))?\\v?|__([^_()][\\w+]+[^_()])__", Pattern.MULTILINE);


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
        var builder = new RendererBuilder();

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
            switch (Function.valueOf(func)) {
                case IF:
                    builder.addMatch(param);
                    break;

                case ELIF:
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
                    var intend = matcher.group(1).length() / 4;
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

        var renderer = builder.build();
        try (var writer = TemplateWriter.create(title, outputStream, environment)) {
            renderer.render(writer, writer);
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
