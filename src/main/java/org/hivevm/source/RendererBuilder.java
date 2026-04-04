// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.util.Stack;

import org.hivevm.source.Renderer.ForEachRenderer;
import org.hivevm.source.Renderer.IndentRenderer;
import org.hivevm.source.Renderer.ListRenderer;
import org.hivevm.source.Renderer.MatchRenderer;
import org.hivevm.source.Renderer.TextRenderer;
import org.hivevm.source.Renderer.VarRenderer;

/**
 * A builder class for constructing a tree of renderers that can dynamically generate output based
 * on various directives such as text blocks, variables, conditions, and iteration.
 * <p>
 * This class provides a fluent API that supports chaining calls to add different types of renderers
 * and control structures. Renderers are organized in a hierarchical structure, allowing for the
 * creation of complex rendering logic.
 */
class RendererBuilder {

    private static final String DEFAULT = "_";

    private final Stack<Renderer> stack;

    /**
     * Constructs a new instance of the RendererBuilder.
     */
    public RendererBuilder() {
        this.stack = new Stack<Renderer>();
        this.stack.push(new ListRenderer());
    }

    /**
     * Adds renderer to current list or for-each renderer
     */
    protected final <R extends Renderer> R addRenderer(R renderer) {
        var peek = stack.peek();
        if (peek instanceof ListRenderer(java.util.List<Renderer> nodes)) {
            nodes.add(renderer);
        }
        else if (peek instanceof ForEachRenderer forech) {
            forech.renderer().nodes().add(renderer);
        }
        return renderer;
    }

    /**
     * Adds a block of text to the renderer. The provided text will be handled as raw content and
     * included in the rendered output as-is, without any additional processing or interpretation.
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
        stack.push(renderer);

        var list = new ListRenderer();
        renderer.nodes().put(expression, list);
        stack.push(list);

        return this;
    }

    /**
     * Adds a conditional case to the renderer. This method associates a specific case with its
     * conditional expression. The case will be evaluated and rendered dynamically based on whether
     * the condition defined by the expression evaluates to true during runtime.
     */
    public final RendererBuilder addCase(String expression) {
        stack.pop();
        var peek = (MatchRenderer) stack.peek();
        var renderer = new ListRenderer();
        peek.nodes().put(expression != null ? expression : DEFAULT, renderer);
        stack.push(renderer);
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
        stack.push(renderer);
        return this;
    }

    public final RendererBuilder pop() {
        stack.pop();
        return this;
    }

    public final Renderer build() {
        return stack.pop();
    }
}
