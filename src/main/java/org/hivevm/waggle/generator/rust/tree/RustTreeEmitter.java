// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.generator.rust.tree;

import org.hivevm.source.LinePrinter;
import org.hivevm.source.Template;
import org.hivevm.waggle.GenerationException;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.generator.rust.RustTemplate;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.parser.Options;
import org.hivevm.waggle.tree.ScopeVariables;
import org.hivevm.waggle.tree.TreeModel;

import java.util.Collection;

/**
 * Rust's tree support: the code around one node scope, and the tree runtime it calls into.
 *
 * <p>Rust has no templates for per-node classes (NODE_MULTI) or visitors (VISITOR) — the ones it had
 * were unported Java leftovers under names that did not exist, so such a grammar died with "Invalid
 * template name". Both are rejected up front with a message that says what is missing, as
 * DEPTH_LIMIT is.
 */
public class RustTreeEmitter implements TreeEmitter {

    @Override
    public void openScope(NodeScope ns, String nodeClass, LinePrinter printer, Options options) {
        printer.print("let " + ScopeVariables.node(ns) + " = ");
        if (options.getNodeFactory().equals("*")) {
            // Old-style multiple-implementations.
            printer.println("(" + nodeClass + ")" + nodeClass + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        } else if (!options.getNodeFactory().isEmpty()) {
            printer.println("(" + nodeClass + ")"
                    + options.getNodeFactory() + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        } else {
            printer.println("new_node(&TreeConstants::" + ns.getNodeDescriptor().getNodeId() + ");");
        }

        printer.println("let mut " + ScopeVariables.closed(ns) + " = true;");

        printer.println("self.jjtree.open_node_scope(&" + ScopeVariables.node(ns) + ");");
        if (options.getNodeScopeHook())
            printer.println("self.jjtree_open_node_scope(" + ScopeVariables.node(ns) + ".as_ref());");

        if (options.getTrackTokens()) {
            printer.println(ScopeVariables.node(ns) + ".jjtSetFirstToken(getToken(1));");
        }
        printer.print("// TRY_CATCH");
    }

    @Override
    public void closeScope(NodeScope ns, LinePrinter printer, Options options, boolean isFinal) {
        printer.println("self.jjtree.close_node_scope_bool(&" + ScopeVariables.node(ns) + ", true);");
        if (!isFinal) {
            printer.println(ScopeVariables.closed(ns) + " = false;");
        }
        if (options.getNodeScopeHook()) {
            printer.println("if self.jjtree.is_node_created() {");
            printer.println("  self.jjtree_close_node_scope(" + ScopeVariables.node(ns) + ".as_ref());");
            printer.println("}");
        }

        if (options.getTrackTokens()) {
            printer.println(ScopeVariables.node(ns) + ".jjtSetLastToken(getToken(0));");
        }
    }

    @Override
    public void catchBlocks(NodeScope ns, LinePrinter printer, Options options, Collection<String> thrown_names) {
        if (!thrown_names.isEmpty()) {
            printer.println("  if try_catch.is_err() {");
            printer.println("// CATCH " + ScopeVariables.exception(ns));
            printer.println("  if " + ScopeVariables.closed(ns) + " {");
            printer.println("//    self.jjtree.clear_node_scope(" + ScopeVariables.node(ns) + ".clone());");
            printer.println("//    " + ScopeVariables.closed(ns) + " = false;");
            printer.println("  } else {");
            printer.println("//    self.jjtree.pop_node();");
            printer.println("  }");
            // This is either an Error or an undeclared Exception. If it's an Error then the cast is good,
            // otherwise we want to force the user to declare it by crashing on the bad cast.
            printer.println("  }");
        }

        printer.println("    // FINALLY");
        printer.println("if " + ScopeVariables.closed(ns) + " {");
        closeScope(ns, printer, options, true);
        printer.println("}");
        if (!thrown_names.isEmpty()) {
            printer.println("    if try_catch.is_err() {");
            printer.println("        return Err(std::io::Error::new(std::io::ErrorKind::Other, \""
                    + ScopeVariables.exception(ns) + "\"));");
            printer.println("    }");
        }
        printer.println("// END TRY_CATCH");
    }

    @Override
    public void emitRuntime(Options context, TreeModel data) {
        rejectUnsupported(context, data);

        RustTemplate.TREE_STATE.render(context);
        generateTreeConstants(context, data);
        RustTemplate.NODE.render(context);
    }

    private static void rejectUnsupported(Options context, TreeModel data) {
        if (context.getVisitor()) {
            throw new GenerationException("VISITOR is not supported for the Rust target.");
        }

        var excludes = context.getExcudeNodes();
        if (context.getBuildNodeFiles()
                && data.getNodesToGenerate().stream().anyMatch(n -> !excludes.contains(n))) {
            throw new GenerationException("Node classes (NODE_MULTI with BUILD_NODE_FILES) are not "
                    + "supported for the Rust target.");
        }
    }

    private void generateTreeConstants(Options context, TreeModel data) {
        var options = Template.newContext(context);
        options.add("NODES", data.getNodeIds().size())
                .set("LABEL", i -> data.getNodeIds().get(i))
                .set("TITLE", i -> data.getNodeNames().get(i));

        RustTemplate.TREE_CONSTANTS.render(options);
    }
}
