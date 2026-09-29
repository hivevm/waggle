// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/JavaCodeGenerator.java, org/javacc/jjtree/CPPCodeGenerator.java

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.GenerationException;
import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.tree.ScopePlan;
import org.hivevm.waggle.tree.ScopeVariables;
import org.hivevm.waggle.tree.TreeModel;
import org.hivevm.waggle.tree.TreeOptions;


/**
 * Rust's tree support: the code around one node scope, and the tree runtime it calls into.
 *
 * <p>Rust has no templates for per-node classes (NODE_MULTI) or visitors (VISITOR) — the ones it had
 * were unported Java leftovers under names that did not exist, so such a grammar died with "Invalid
 * template name". Both are rejected up front with a message that says what is missing, as
 * DEPTH_LIMIT is, and so are NODE_FACTORY and TRACK_TOKENS, which came out as Java.
 */
public class RustTreeEmitter implements TreeEmitter {

    @Override
    public void openScope(ScopePlan ns, LinePrinter printer, TreeOptions options) {
        printer.println("let " + ns.nodeVar() + " = new_node(&TreeConstants::"
                + ns.nodeId() + ");");

        printer.println("let mut " + ns.closedVar() + " = true;");

        printer.println("self.jjtree.open_node_scope(&" + ns.nodeVar() + ");");
        if (options.scopeHook())
            printer.println("self.jjtree_open_node_scope(" + ns.nodeVar() + ".as_ref());");
    }

    @Override
    public void closeScope(ScopePlan ns, LinePrinter printer, TreeOptions options, boolean isFinal) {
        printer.println(RustTreeEmitter.closeCall(ns));
        if (!isFinal) {
            printer.println(ns.closedVar() + " = false;");
        }
        if (options.scopeHook()) {
            printer.println("if self.jjtree.is_node_created() {");
            printer.println("  self.jjtree_close_node_scope(" + ns.nodeVar() + ".as_ref());");
            printer.println("}");
        }
    }

    /**
     * {@link ScopeVariables#closeCall} for Rust, which has no overloads: a number of children goes to
     * {@code close_node_scope}, a condition to {@code close_node_scope_bool}. An expression that is
     * not an integer literal is a condition, and rustc rejects it if it is not a bool.
     */
    private static String closeCall(ScopePlan ns) {
        var node = ns.nodeVar();
        return switch (ns.arity()) {
            case ScopePlan.Arity.Always a -> "self.jjtree.close_node_scope_bool(&" + node + ", true);";
            case ScopePlan.Arity.GreaterThan a -> "self.jjtree.close_node_scope_bool(&" + node
                    + ", self.jjtree.node_arity() > " + a.text().strip() + ");";
            case ScopePlan.Arity.Count a ->
                    "self.jjtree.close_node_scope(&" + node + ", " + a.text().strip() + ");";
            case ScopePlan.Arity.Condition a ->
                    "self.jjtree.close_node_scope_bool(&" + node + ", " + a.text() + ");";
        };
    }

    @Override
    public void catchBlocks(ScopePlan ns, LinePrinter printer, TreeOptions options) {
        printer.println();
        printer.println("if " + ns.closedVar() + " {");
        closeScope(ns, printer, options, true);
        printer.println("}");
    }

    /** What the Rust nodes cannot do; the options would otherwise come out as Java. */
    @Override
    public void validate(TreeOptions tree, TreeModel data) {
        if (tree.visitor()) {
            throw new GenerationException("VISITOR is not supported for the Rust target.");
        }
        if (!tree.nodeFactory().isEmpty()) {
            throw new GenerationException("NODE_FACTORY is not supported for the Rust target.");
        }
        if (tree.trackTokens()) {
            throw new GenerationException("TRACK_TOKENS is not supported for the Rust target.");
        }

        var excludes = tree.customNodes();
        if (tree.buildNodeFiles()
                && data.getNodesToGenerate().stream().anyMatch(n -> !excludes.contains(n))) {
            throw new GenerationException("Node classes (NODE_MULTI with BUILD_NODE_FILES) are not "
                    + "supported for the Rust target.");
        }
    }

    @Override
    public void emitRuntime(Options context, TreeOptions tree, TreeModel data) {
        RustTemplate.TREE_STATE.render(context);
        generateTreeConstants(context, data);
        RustTemplate.NODE.render(context);
    }

    private void generateTreeConstants(Options context, TreeModel data) {
        var options = OptionsContext.of(context);
        options.add("NODES", data.getNodeIds().size())
                .set("LABEL", i -> data.getNodeIds().get(i))
                .set("TITLE", i -> data.getNodeNames().get(i));

        RustTemplate.TREE_CONSTANTS.render(options);
    }
}
