// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import java.io.PrintWriter;
import java.util.Collection;

import org.hivevm.cc.generator.TreeGenerator;
import org.hivevm.cc.model.NodeDescriptor;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.parser.Options;

class RustTreeGenerator extends TreeGenerator {

    @Override
    public final void insertOpenNodeCode(NodeScope ns, String nodeClass, PrintWriter writer, Options options) {
        writer.print("let " + ns.getNodeVariable() + " = ");
        if (options.getNodeFactory().equals("*")) {
            // Old-style multiple-implementations.
            writer.println(
                    "(" + nodeClass + ")" + nodeClass + ".jjtCreate(" +
                            NodeDescriptor.getNodeId(ns.getNodeDescriptor().getName()) + ");");
        }
        else if (!options.getNodeFactory().isEmpty()) {
            writer.println("(" + nodeClass + ")"
                    + options.getNodeFactory() + ".jjtCreate("
                    + NodeDescriptor.getNodeId(ns.getNodeDescriptor().getName()) + ");");
        }
        else {
            writer.println("new_node(&TreeConstants::" + NodeDescriptor.getNodeId(ns.getNodeDescriptor().getName()) + ");");
        }

        writer.println("let mut " + ns.getClosedVariable() + " = true;");

        writer.println("self.jjtree.open_node_scope(&" + ns.getNodeVariable() + ");");
        if (options.getNodeScopeHook())
            writer.println("self.jjtree_open_node_scope(" + ns.getNodeVariable() + ".as_ref());");

        if (options.getTrackTokens()) {
            writer.println(ns.getNodeVariable() + ".jjtSetFirstToken(getToken(1));");
        }
        writer.print("// TRY_CATCH");
    }

    @Override
    public final void insertCloseNodeCode(NodeScope ns, PrintWriter writer, Options options, boolean isFinal) {
        writer.println("self.jjtree.close_node_scope_bool(&" + ns.getNodeVariable() + ", true);");
        if (!isFinal) {
            writer.println(ns.getClosedVariable() + " = false;");
        }
        if (options.getNodeScopeHook()) {
            writer.println("if self.jjtree.is_node_created() {");
            writer.println("  self.jjtree_close_node_scope(" + ns.getNodeVariable() + ".as_ref());");
            writer.println("}");
        }

        if (options.getTrackTokens()) {
            writer.println(ns.getNodeVariable() + ".jjtSetLastToken(getToken(0));");
        }
    }

    @Override
    public final void insertCatchBlocks(NodeScope ns, PrintWriter writer, Options options, Collection<String> thrown_names) {
        if (!thrown_names.isEmpty()) {
            writer.println("  if try_catch.is_err() {");
            writer.println("// CATCH " + ns.getExceptionVariable());
            writer.println("  if " + ns.getClosedVariable() + " {");
            writer.println("//    self.jjtree.clear_node_scope(" + ns.getNodeVariable() + ".clone());");
            writer.println("//    " + ns.getClosedVariable() + " = false;");
            writer.println("  } else {");
            writer.println("//    self.jjtree.pop_node();");
            writer.println("  }");
            // This is either an Error or an undeclared Exception. If it's an Error then the cast is good,
            // otherwise we want to force the user to declare it by crashing on the bad cast.
            writer.println("  }");
        }

        writer.println("    // FINALLY");
        writer.println("if " + ns.getClosedVariable() + " {");
        insertCloseNodeCode(ns, writer, options, true);
        writer.println("}");
        if (!thrown_names.isEmpty()) {
            writer.println("    if try_catch.is_err() {");
            writer.println("        return Err(std::io::Error::new(std::io::ErrorKind::Other, \""
                    + ns.getExceptionVariable() + "\"));");
            writer.println("    }");
        }
        writer.print("// END TRY_CATCH");
    }
}
