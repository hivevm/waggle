// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.generator.java.tree;

import org.hivevm.source.LinePrinter;
import org.hivevm.source.Template;
import org.hivevm.waggle.Waggle;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.generator.java.JavaTemplate;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.parser.Options;
import org.hivevm.waggle.tree.ScopeVariables;
import org.hivevm.waggle.tree.TreeModel;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java's tree support: the code around one node scope, and the tree runtime it calls into.
 */
public class JavaTreeEmitter implements TreeEmitter {

    @Override
    public void openScope(NodeScope ns, String nodeClass, LinePrinter printer, Options options) {
        printer.print(nodeClass + " " + ScopeVariables.node(ns) + " = ");
        if (options.getNodeFactory().equals("*")) {
            // Old-style multiple-implementations.
            printer.println("(" + nodeClass + ")" + nodeClass + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        } else if (!options.getNodeFactory().isEmpty()) {
            printer.println("(" + nodeClass + ")"
                    + options.getNodeFactory() + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        } else {
            printer.println("new " + nodeClass + "(this, " + "NodeType." + ns.getNodeDescriptor().getNodeId() + ");");
        }

        printer.println("boolean " + ScopeVariables.closed(ns) + " = true;");

        printer.println(openNodeScope(ns));
        if (options.getNodeScopeHook())
            printer.println("jjtreeOpenNodeScope(" + ScopeVariables.node(ns) + ");");

        if (options.getTrackTokens()) {
            printer.println(ScopeVariables.node(ns) + ".jjtSetFirstToken(getToken(1));");
        }
        printer.print("try {");
    }

    @Override
    public void closeScope(NodeScope ns, LinePrinter printer, Options options, boolean isFinal) {
        printer.println(closeNodeScope(ns));
        if (!isFinal) {
            printer.println(ScopeVariables.closed(ns) + " = false;");
        }
        if (options.getNodeScopeHook()) {
            printer.println("if (jjtree.nodeCreated()) {");
            printer.indent();
            printer.println("jjtreeCloseNodeScope(" + ScopeVariables.node(ns) + ");");
            printer.outdent();
            printer.println("}");
        }

        if (options.getTrackTokens()) {
            printer.println(ScopeVariables.node(ns) + ".jjtSetLastToken(getToken(0));");
        }
    }

    @Override
    public void catchBlocks(NodeScope ns, LinePrinter printer, Options options, Collection<String> thrown_names) {
        printer.println();
        if (!thrown_names.isEmpty()) {
            printer.println("} catch (Throwable " + ScopeVariables.exception(ns) + ") {");
            printer.indent();
            printer.println("if (" + ScopeVariables.closed(ns) + ") {");
            printer.indent();
            printer.println("jjtree.clearNodeScope(" + ScopeVariables.node(ns) + ");");
            printer.println(ScopeVariables.closed(ns) + " = false;");
            printer.outdent();
            printer.println("} else {");
            printer.indent();
            printer.println("jjtree.popNode();");
            printer.outdent();
            printer.println("}");

            for (var thrown : thrown_names) {
                printer.println("if (" + ScopeVariables.exception(ns) + " instanceof " + thrown + ") {");
                printer.indent();
                printer.println("throw (" + thrown + ")" + ScopeVariables.exception(ns) + ";");
                printer.outdent();
                printer.println("}");
            }
            // This is either an Error or an undeclared Exception. If it's an Error then the cast is good,
            // otherwise we want to force the user to declare it by crashing on the bad cast.
            printer.println("throw (Error)" + ScopeVariables.exception(ns) + ";");
            printer.outdent();
        }

        printer.println("} finally {");
        printer.indent();
        printer.println("if (" + ScopeVariables.closed(ns) + ") {");
        printer.indent();
        closeScope(ns, printer, options, true);
        printer.outdent();
        printer.println("}");
        printer.outdent();
        printer.print("}");
    }

    @Override
    public void emitRuntime(Options context, TreeModel data) {
        generateTreeConstants(context, data);
        generateVisitors(context, data);

        // TreeClasses
        generateNode(context);
        generateTreeNodes(context, data.getNodesToGenerate());

        JavaTemplate.NODESTATE.render(context);
    }

    private void generateTreeConstants(Options context, TreeModel data) {
        var options = Template.newContext(context);
        options.add("NODE_NAMES", data.getNodeNames())
                .set("NODE_NAMES_TITLE", i -> i);
        options.add("NODES", data.getNodeIds().size())
                .set("NODES_ORDINAL", i -> i)
                .set("NODES_LABEL", i -> data.getNodeIds().get(i));

        JavaTemplate.NODETYPE.render(options, context.getParserName());
    }

    private void generateVisitors(Options context, TreeModel data) {
        if (!context.getVisitor()) {
            return;
        }

        var nodeNames = data.getNodeNames().stream()
                .filter(n -> !n.equals("void"))
                .collect(Collectors.toList());
        var argumentType = JavaTreeEmitter.visitorDataType(context);
        var returnValue = JavaTreeEmitter.returnValue(context.getVisitorReturnType(), argumentType);
        var isVoidReturnType = "void".equals(context.getVisitorReturnType());

        var options = Template.newContext(context);
        options.add("NODES", nodeNames).set("NODES_NAME", i -> i);
        options.set("RETURN_TYPE", context.getVisitorReturnType());
        options.set("RETURN_VALUE", returnValue);
        options.set("RETURN", isVoidReturnType ? "" : "return ");
        options.set("ARGUMENT_TYPE", argumentType);
        options.set("EXCEPTION", JavaTreeEmitter.mergeVisitorException(context));
        options.set(Waggle.JJTREE_MULTI, context.getMulti());

        JavaTemplate.MULTI_NODE_VISITOR.render(options);
        JavaTemplate.MULTI_NODE_DEFAULT_VISITOR.render(options);
    }

    private void generateNode(Options context) {
        var options = Template.newContext(context);
        options.set(Waggle.JJTREE_VISITOR_DATA_TYPE, JavaTreeEmitter.visitorDataType(context));

        JavaTemplate.NODE.render(options);
    }

    private void generateTreeNodes(Options context, Set<String> nodesToGenerate) {
        if (!context.getBuildNodeFiles()) {
            return;
        }

        var options = Template.newContext(context);
        options.set(Waggle.JJTREE_VISITOR_RETURN_VOID, context.getVisitorReturnType().equals("void"));
        options.set(Waggle.JJTREE_NODE_CLASS, JavaTreeEmitter.nodeClass(context));
        options.set(Waggle.JJTREE_VISITOR_DATA_TYPE, JavaTreeEmitter.visitorDataType(context));

        var excludes = context.getExcudeNodes();
        for (var nodeType : nodesToGenerate) {
            if (excludes.contains(nodeType)) {
                continue;
            }
            options.set(Waggle.JJTREE_NODE_TYPE, nodeType);

            JavaTemplate.MULTI_NODE.render(options, nodeType);
        }
    }

    /**
     * The base class the generated node classes extend. Defaults to the generated {@code Node}, so
     * that a grammar which does not supply a NODE_CLASS still yields compilable node classes.
     */
    private static String nodeClass(Options context) {
        var nodeClass = context.getNodeClass();
        return nodeClass.isEmpty() ? "Node" : nodeClass.trim();
    }

    /**
     * The type of the payload passed through {@code jjtAccept}. Defaults to {@code Object}, so that
     * VISITOR without an explicit VISITOR_DATA_TYPE still yields a typed parameter.
     */
    private static String visitorDataType(Options context) {
        var dataType = context.getVisitorDataType();
        return dataType.isEmpty() ? "Object" : dataType.trim();
    }

    private static String mergeVisitorException(Options context) {
        var ve = context.getVisitorException();
        return "".equals(ve) ? ve : " throws " + ve;
    }

    private static String returnValue(String returnType, String argumentType) {
        var isVoidReturnType = "void".equals(returnType);
        if (isVoidReturnType) {
            return "";
        }

        if (returnType.equals(argumentType)) {
            return " data";
        }

        return switch (returnType) {
            case "boolean" -> " false";
            case "int", "short", "byte" -> " 0";
            case "long" -> " 0L";
            case "double" -> " 0.0d";
            case "float" -> " 0.0f";
            case "char" -> " '\u0000'";
            default -> " null";
        };
    }
    /** The tree-runtime call that opens a node scope. */
    private static String openNodeScope(NodeScope ns) {
        return "jjtree.openNodeScope(" + ScopeVariables.node(ns) + ");";
    }

    /** The tree-runtime call that closes a node scope, under the descriptor's arity condition. */
    private static String closeNodeScope(NodeScope ns) {
        var node = ScopeVariables.node(ns);
        var descriptor = ns.getNodeDescriptor();
        if (descriptor.getText() == null) {
            return "jjtree.closeNodeScope(" + node + ", true);";
        }
        return descriptor.isGt()
                ? "jjtree.closeNodeScope(" + node + ", jjtree.nodeArity() >" + descriptor.getText() + ");"
                : "jjtree.closeNodeScope(" + node + ", " + descriptor.getText() + ");";
    }
}
