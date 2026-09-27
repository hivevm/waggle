// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.hivevm.source.Renderer.ForEachRenderer;
import org.hivevm.source.Renderer.IndentRenderer;
import org.hivevm.source.Renderer.ListRenderer;
import org.hivevm.source.Renderer.MatchRenderer;
import org.hivevm.source.Renderer.TextRenderer;
import org.hivevm.source.Renderer.VarRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * A builder class for constructing a tree of renderers that can dynamically generate output based
 * on various directives such as text blocks, variables, conditions, and iteration.
 * <p>
 * This class provides a fluent API that supports chaining calls to add different types of renderers
 * and control structures. Renderers are organized in a hierarchical structure, allowing for the
 * creation of complex rendering logic.
 */
class RendererBuilder {

    private final String template;
    /** The open blocks, innermost last; the root list renderer is always the first. */
    private final List<Renderer> stack;

    /**
     * Constructs a new instance of the RendererBuilder for the named template.
     */
    public RendererBuilder(String template) {
        this.template = template;
        this.stack = new ArrayList<>();
        this.stack.add(new ListRenderer());
    }

    /**
     * Adds renderer to current list or for-each renderer
     */
    protected final <R extends Renderer> R addRenderer(R renderer) {
        var peek = stack.getLast();
        if (peek instanceof ListRenderer(List<Renderer> nodes)) {
            nodes.add(renderer);
        } else if (peek instanceof ForEachRenderer forech) {
            forech.renderer().nodes().add(renderer);
        } else {
            // Reached when a block was closed with the wrong directive (e.g. //@end for an //@if),
            // which leaves the MatchRenderer on the stack. Dropping the content here is how a
            // template loses its tail without a word.
            throw new TemplateException(template
                    + ": content in a block that is not open — a //@if closed with //@end, or a "
                    + "//@foreach closed with //@fi?");
        }
        return renderer;
    }

    /**
     * Changes the indentation of the lines that follow by {@code intend} levels; a negative value
     * outdents.
     */
    public final RendererBuilder setIntend(int intend) {
        addRenderer(new IndentRenderer(intend));
        return this;
    }

    /**
     * Adds a block of text to the renderer. The provided text will be handled as raw content and
     * included in the rendered output as-is, without any additional processing or interpretation.
     */
    public final RendererBuilder addText(String text) {
        addRenderer(new TextRenderer(text));
        return this;
    }

    /**
     * Adds a variable to the renderer's context. The variable is specified as an expression, which
     * will be dynamically evaluated in the rendering environment. This allows the rendered output
     * to include values derived from the environment's state.
     */
    public final RendererBuilder addVar(String expression) {
        addRenderer(new VarRenderer(expression));
        return this;
    }

    /**
     * Adds a new match case renderer to the current renderer. The match renderer allows conditional
     * rendering based on an environment's variables or flags. The matched case is dynamically
     * selected at render time depending on whether conditions associated with environment states
     * are satisfied.
     */
    public final RendererBuilder addMatch(String expression) {
        var renderer = addRenderer(new MatchRenderer());
        stack.add(renderer);

        var list = new ListRenderer();
        renderer.nodes().put(expression, list);
        stack.add(list);

        return this;
    }

    /**
     * Adds a conditional case to the renderer. This method associates a specific case with its
     * conditional expression. The case will be evaluated and rendered dynamically based on whether
     * the condition defined by the expression evaluates to true during runtime.
     */
    public final RendererBuilder addCase(String expression) {
        // Only the branch of an //@if can be followed by another: at the top level this was an
        // EmptyStackException, inside a //@foreach a ClassCastException.
        if ((stack.size() < 2) || !(stack.get(stack.size() - 2) instanceof MatchRenderer)) {
            throw new TemplateException("//@elif or //@else outside an //@if");
        }
        stack.removeLast();
        var peek = (MatchRenderer) stack.getLast();
        var renderer = new ListRenderer();
        // The branches sit in a map: a second //@else, or an //@elif repeating a condition, used to
        // replace the earlier branch instead of being reported.
        if (peek.nodes().putIfAbsent(expression != null ? expression : MatchRenderer.DEFAULT, renderer) != null) {
            throw new TemplateException(expression != null
                    ? "//@elif(" + expression + ") repeats a condition of its //@if"
                    : "a second //@else in one //@if");
        }
        stack.add(renderer);
        return this;
    }

    /**
     * Adds a "foreach" directive to the renderer. This directive processes a collection by
     * iterating over its elements, associating each element with a specified variable that can be
     * referenced during rendering. The variable will be substituted with each element of the list
     * sequentially as the iteration proceeds.
     */
    public final RendererBuilder addForeach(String param) {
        var renderer = addRenderer(new ForEachRenderer(param));
        stack.add(renderer);
        return this;
    }

    /**
     * Closes the innermost block. The root list renderer must never be popped — a //@fi or //@end
     * without a matching opener would otherwise unbalance the stack.
     */
    public final RendererBuilder pop() {
        if (stack.size() <= 1) {
            throw new TemplateException(template
                    + ": //@fi or //@end without a matching //@if or //@foreach");
        }
        stack.removeLast();
        return this;
    }

    /**
     * Returns the root renderer.
     *
     * <p>Fails when blocks are left open. Previously this popped whatever was on top, so a missing
     * //@fi returned the innermost block instead of the root — the rest of the template was dropped
     * silently, leaving a truncated file with a valid checksum footer.
     */
    public final Renderer build() {
        if (stack.size() != 1) {
            throw new TemplateException(template + ": " + (stack.size() - 1)
                    + " block(s) left open — a //@fi or //@end is missing");
        }
        return stack.removeLast();
    }
}
