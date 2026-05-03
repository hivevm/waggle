// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import java.io.PrintWriter;
import java.util.Collection;

import org.hivevm.cc.generator.TreeGenerator;
import org.hivevm.cc.model.NodeDescriptor;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.parser.Options;

class JavaTreeGenerator extends TreeGenerator {

    @Override
    public final void insertOpenNodeCode(NodeScope ns, String nodeClass, PrintWriter writer, Options options) {
        writer.print(nodeClass + " " + ns.getNodeVariable() + " = ");
        if (options.getNodeFactory().equals("*")) {
            // Old-style multiple-implementations.
            writer.println("(" + nodeClass + ")" + nodeClass + ".jjtCreate("
                + NodeDescriptor.getNodeId(ns.getNodeDescriptor().getName()) + ");");
        }
        else if (!options.getNodeFactory().isEmpty()) {
            writer.println("(" + nodeClass + ")"
                + options.getNodeFactory() + ".jjtCreate("
                + NodeDescriptor.getNodeId(ns.getNodeDescriptor().getName()) + ");");
        }
        else {
            writer.println("new " + nodeClass + "(this, " + "NodeType."
                + NodeDescriptor.getNodeId(ns.getNodeDescriptor().getName()) + ");");
        }

        writer.println("boolean " + ns.getClosedVariable() + " = true;");

        writer.println(ns.getNodeDescriptor().openNode(ns.getNodeVariable()));
        if (options.getNodeScopeHook())
            writer.println("jjtreeOpenNodeScope(" + ns.getNodeVariable() + ");");

        if (options.getTrackTokens()) {
            writer.println(ns.getNodeVariable() + ".jjtSetFirstToken(getToken(1));");
        }
        writer.print("try {");
    }

    @Override
    public final void insertCloseNodeCode(NodeScope ns, PrintWriter writer, Options options, boolean isFinal) {
        writer.println(ns.getNodeDescriptor().closeNode(ns.getNodeVariable()));
        if (!isFinal) {
            writer.println(ns.getClosedVariable() + " = false;");
        }
        if (options.getNodeScopeHook()) {
            writer.println("if (jjtree.nodeCreated()) {");
            writer.println(" jjtreeCloseNodeScope(" + ns.getNodeVariable() + ");");
            writer.println("}");
        }

        if (options.getTrackTokens()) {
            writer.println(ns.getNodeVariable() + ".jjtSetLastToken(getToken(0));");
        }
    }

    @Override
    public final void insertCatchBlocks(NodeScope ns, PrintWriter writer, Options options, Collection<String> thrown_names) {
        if (!thrown_names.isEmpty()) {
            writer.println("} catch (Throwable " + ns.getExceptionVariable() + ") {");
            writer.println("  if (" + ns.getClosedVariable() + ") {");
            writer.println("    jjtree.clearNodeScope(" + ns.getNodeVariable() + ");");
            writer.println("    " + ns.getClosedVariable() + " = false;");
            writer.println("  } else {");
            writer.println("    jjtree.popNode();");
            writer.println("  }");

            for (var thrown : thrown_names) {
                writer.println("  if (" + ns.getExceptionVariable() + " instanceof " + thrown + ") {");
                writer.println("    throw (" + thrown + ")" + ns.getExceptionVariable() + ";");
                writer.println("  }");
            }
            // This is either an Error or an undeclared Exception. If it's an Error then the cast is good,
            // otherwise we want to force the user to declare it by crashing on the bad cast.
            writer.println("  throw (Error)" + ns.getExceptionVariable() + ";");
        }

        writer.println("} finally {");
        writer.println("  if (" + ns.getClosedVariable() + ") {");
        insertCloseNodeCode(ns, writer, options, true);
        writer.println("  }");
        writer.print("}");
    }
}
