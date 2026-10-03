// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.hivevm.source.Renderer.ApplyRenderer;
import org.hivevm.source.Renderer.Branch;
import org.hivevm.source.Renderer.ForEachRenderer;
import org.hivevm.source.Renderer.IndentRenderer;
import org.hivevm.source.Renderer.ListRenderer;
import org.hivevm.source.Renderer.MatchRenderer;
import org.hivevm.source.Renderer.TextRenderer;
import org.hivevm.source.Renderer.VarRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the renderer tree of one template, directive by directive, and records every name the
 * template refers to.
 *
 * <p>Every directive that can fail names the template and the line it is written on. A block
 * remembers the line of the directive that opened it, so a block closed with the wrong directive or
 * not at all is reported where it starts, not where the template ends.
 */
class RendererBuilder {

    /**
     * An open block: the {@code //@if} or {@code //@foreach} that opened it, and the list that
     * content goes into — for an {@code //@if}, the body of its current branch.
     */
    private record Block(String directive, int line, ListRenderer body, MatchRenderer match,
                         String list) {
    }

    private final String template;
    /** The open blocks, innermost last; the template's own body is always the first. */
    private final List<Block> stack = new ArrayList<>();
    private final List<Template.Reference> references = new ArrayList<>();

    /** The line of the directive being added. */
    private int line;

    /**
     * Constructs a new instance of the RendererBuilder for the named template.
     */
    RendererBuilder(String template) {
        this.template = template;
        this.stack.add(new Block("", 0, new ListRenderer(), null, null));
    }

    /** The line the next directive or text is written on. */
    final void at(int line) {
        this.line = line;
    }

    private String where() {
        return this.template + ":" + this.line;
    }

    private TemplateException error(String message) {
        return new TemplateException(where() + ": " + message);
    }

    /** Records that the template refers to {@code name}, from inside the innermost list. */
    private void refer(String directive, String name) {
        String list = null;
        for (var block : this.stack) {
            if (block.list() != null) {
                list = block.list();
            }
        }
        this.references.add(new Template.Reference(directive, name, this.line, list));
    }

    private <R extends Renderer> R add(R renderer) {
        this.stack.getLast().body().nodes().add(renderer);
        return renderer;
    }

    /**
     * Changes the indentation of the lines that follow by {@code indent} levels; a negative value
     * outdents.
     */
    final void addIndent(int indent) {
        add(new IndentRenderer(indent));
    }

    /** Adds text, which is rendered as it stands. */
    final void addText(String text) {
        add(new TextRenderer(text));
    }

    /** Adds a placeholder, or an {@code //@invoke}, for the value of {@code name}. */
    final void addVar(String directive, String name) {
        refer(directive, name);
        add(new VarRenderer(name, where()));
    }

    /** Opens an {@code //@if} on {@code condition}. */
    final void addIf(String condition) {
        refer("if", condition.startsWith("!") ? condition.substring(1) : condition);
        var match = add(new MatchRenderer());
        var body = new ListRenderer();
        match.branches().add(new Branch(condition, body, where()));
        this.stack.add(new Block("if", this.line, body, match, null));
    }

    /**
     * Adds an {@code //@elif} on {@code condition} to the innermost {@code //@if}, or with no
     * condition its {@code //@else}.
     */
    final void addBranch(String condition) {
        var top = this.stack.getLast();
        var directive = (condition == null) ? "//@else" : "//@elif";
        if (top.match() == null) {
            throw error(directive + " outside an //@if");
        }
        var branches = top.match().branches();
        if (branches.getLast().condition() == null) {
            throw error(directive + " after the //@else of the //@if on line " + top.line());
        }
        if (condition != null) {
            refer("elif", condition.startsWith("!") ? condition.substring(1) : condition);
            // A repeated condition can never be taken: its branch is dead text.
            if (branches.stream().anyMatch(b -> condition.equals(b.condition()))) {
                throw error("//@elif(" + condition + ") repeats a condition of the //@if on line "
                        + top.line());
            }
        }
        var body = new ListRenderer();
        branches.add(new Branch(condition, body, where()));
        this.stack.set(this.stack.size() - 1,
                new Block(top.directive(), top.line(), body, top.match(), null));
    }

    /** Opens a {@code //@foreach} over the records of {@code list}. */
    final void addForeach(String list) {
        refer("foreach", list);
        var body = new ListRenderer();
        add(new ForEachRenderer(list, body, where()));
        this.stack.add(new Block("foreach", this.line, body, null, list));
    }

    /**
     * Renders the record bound to {@code attribute}, or each record of the list bound to it,
     * through the template named after its type (ADR-0031).
     */
    final void addApply(String attribute, String base) {
        refer("apply", attribute);
        add(new ApplyRenderer(attribute, base, where()));
    }

    /**
     * Closes the innermost block, which {@code directive} — {@code "if"} for {@code //@fi},
     * {@code "foreach"} for {@code //@end} — must have opened.
     */
    final void close(String directive) {
        var closer = directive.equals("if") ? "//@fi" : "//@end";
        if (this.stack.size() <= 1) {
            throw error(closer + " without a matching //@" + directive);
        }
        var top = this.stack.getLast();
        if (!top.directive().equals(directive)) {
            throw error(closer + " closes the //@" + top.directive() + " on line " + top.line());
        }
        this.stack.removeLast();
    }

    /** The names the template refers to, in the order it does. */
    final List<Template.Reference> references() {
        return List.copyOf(this.references);
    }

    /**
     * Returns the template's renderer.
     *
     * <p>Fails when a block is left open: returning anything else used to drop the rest of the
     * template silently, leaving a truncated file with a valid checksum footer.
     */
    final Renderer build() {
        if (this.stack.size() != 1) {
            var open = this.stack.getLast();
            throw new TemplateException(this.template + ":" + open.line() + ": //@"
                    + open.directive() + " is never closed — a //@"
                    + (open.directive().equals("if") ? "fi" : "end") + " is missing");
        }
        return this.stack.getFirst().body();
    }
}
