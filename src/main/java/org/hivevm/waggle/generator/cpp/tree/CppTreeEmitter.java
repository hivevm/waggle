// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.generator.cpp.tree;

import org.hivevm.source.Context;
import org.hivevm.source.LinePrinter;
import org.hivevm.source.Template;
import org.hivevm.waggle.Waggle;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.generator.cpp.CppTemplate;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.parser.Options;
import org.hivevm.waggle.tree.ScopeVariables;
import org.hivevm.waggle.tree.TreeModel;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * C++'s tree support: the code around one node scope, and the tree runtime it calls into.
 */
public class CppTreeEmitter implements TreeEmitter {

    @Override
    public void openScope(NodeScope ns, String nodeClass, LinePrinter printer, Options options) {
        printer.print(nodeClass + " *" + ScopeVariables.node(ns) + " = ");
        if (options.getNodeFactory().equals("*")) {
            // Old-style multiple-implementations.
            printer.println("(" + nodeClass + "*)" + nodeClass + "::jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        } else if (!options.getNodeFactory().isEmpty()) {
            printer.println("(" + nodeClass + "*)"
                    + options.getNodeFactory() + "->jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        } else {
            printer.println("new " + nodeClass + "(" + ns.getNodeDescriptor().getNodeId() + ");");
        }

        printer.println("bool " + ScopeVariables.closed(ns) + " = true;");

        printer.println(openNodeScope(ns));
        if (options.getNodeScopeHook())
            printer.println("jjtreeOpenNodeScope(" + ScopeVariables.node(ns) + ");");

        if (options.getTrackTokens()) {
            printer.println(ScopeVariables.node(ns) + "->jjtSetFirstToken(getToken(1));");
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
            printer.println(" jjtreeCloseNodeScope(" + ScopeVariables.node(ns) + ");");
            printer.println("}");
        }

        if (options.getTrackTokens()) {
            printer.println(ScopeVariables.node(ns) + "->jjtSetLastToken(getToken(0));");
        }
    }

    @Override
    public void catchBlocks(NodeScope ns, LinePrinter printer, Options options, Collection<String> thrown_names) {
        printer.println("} catch (...) {"); // " + ns.exceptionVar + ") {");
        printer.println("  if (" + ScopeVariables.closed(ns) + ") {");
        printer.println("    jjtree.clearNodeScope(" + ScopeVariables.node(ns) + ");");
        printer.println("    " + ScopeVariables.closed(ns) + " = false;");
        printer.println("  } else {");
        printer.println("    jjtree.popNode();");
        printer.println("  }");

        printer.println("} {");
        printer.println("  if (" + ScopeVariables.closed(ns) + ") {");
        closeScope(ns, printer, options, true);
        printer.println("  }");
        printer.print("}");
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

    @Override
    public void emitRuntime(Options context, TreeModel data) {
        generateTreeState(context);
        generateTreeConstants(context, data);
        generateVisitors(context, data);

        // TreeClasses
        generateNode(context);
        generateNodeInterface(context);
        generateTree(context);
        generateTreeNodes(context, data.getNodesToGenerate());
        generateOneTreeInterface(context, data.getNodesToGenerate());
    }

    private void generateTreeState(Options context) {
        CppTemplate.TREESTATE_H.render(context);
        CppTemplate.TREESTATE.render(context);
    }

    private void generateTreeConstants(Options context, TreeModel data) {
        var options = Template.newContext(context);
        options.add("NODES", data.getNodeIds().size())
                .set("ORDINAL", i -> i)
                .set("LABEL", i -> data.getNodeIds().get(i));
        options.add("NODE_NAMES", data.getNodeNames().size())
                .set("ORDINAL", i -> i)
                .set("label", i -> data.getNodeNames().get(i))
                .set("CHARS", i -> CppTreeEmitter.toCharArray(data.getNodeNames().get(i)));
        options.set(Waggle.JJPARSER_CPP_DEFINE, context.getParserName().toUpperCase(Locale.ROOT));

        CppTemplate.TREE_CONSTANTS.render(options, context.getParserName());
    }

    private void generateVisitors(Options context, TreeModel data) {
        if (!context.getVisitor()) {
            return;
        }

        var nodeNames = data.getNodeNames().stream()
                .filter(n -> !n.equals("void"))
                .collect(Collectors.toList());
        var argumentType = CppTreeEmitter.getVisitorArgumentType(context);
        var returnType = CppTreeEmitter.getVisitorReturnType(context);
        if (!context.getVisitorDataType().isEmpty()) {
            argumentType = context.getVisitorDataType();
        }

        var options = Template.newContext(context);
        options.add("NODES", nodeNames).set("NODES_TYPE", n -> "AST" + n);
        options.set(Waggle.JJPARSER_CPP_DEFINE, context.getParserName().toUpperCase(Locale.ROOT));
        options.set("RETURN_TYPE", returnType);
        options.set("RETURN", returnType.equals("void") ? "" : "return ");
        options.set("ARGUMENT_TYPE", argumentType);
        options.set(Waggle.JJTREE_MULTI, context.getMulti());

        CppTemplate.VISITOR.render(options, context.getParserName());
    }

    /**
     * Sets the three visitor-type options on a render context, computing the return type once
     * instead of the three repeated {@code getVisitorReturnType} lookups the call sites used to do.
     */
    private static void applyVisitorTypes(Context optionMap, Options context) {
        var returnType = CppTreeEmitter.getVisitorReturnType(context);
        optionMap.set(Waggle.JJTREE_VISITOR_RETURN_TYPE, returnType);
        optionMap.set(Waggle.JJTREE_VISITOR_DATA_TYPE, CppTreeEmitter.getVisitorArgumentType(context));
        optionMap.set(Waggle.JJTREE_VISITOR_RETURN_VOID, returnType.equals("void"));
    }

    private void generateNode(Options context) {
        var optionMap = Template.newContext(context);
        CppTreeEmitter.applyVisitorTypes(optionMap, context);

        CppTemplate.NODE.render(optionMap);
    }

    private void generateTreeNodes(Options context, Set<String> nodesToGenerate) {
        var excludes = context.getExcudeNodes();
        for (var nodeType : nodesToGenerate) {
            if (excludes.contains(nodeType)) {
                continue;
            }

            var options = Template.newContext(context);
            CppTreeEmitter.applyVisitorTypes(options, context);
            options.set(Waggle.JJTREE_NODE_TYPE, nodeType);
            options.set(Waggle.JJTREE_NODE_CLASS, CppTreeEmitter.nodeClass(context));

            CppTemplate.MULTINODE_H.render(options, nodeType);
            CppTemplate.MULTINODE.render(options, nodeType);
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

    private void generateNodeInterface(Options context) {
        var optionMap = Template.newContext(context);
        CppTreeEmitter.applyVisitorTypes(optionMap, context);

        CppTemplate.NODE_H.render(optionMap);
    }

    private void generateTree(Options context) {
        var optionMap = Template.newContext(context);
        CppTreeEmitter.applyVisitorTypes(optionMap, context);
        optionMap.set(Waggle.JJTREE_NODE_TYPE, "Tree");

        CppTemplate.TREE.render(optionMap);
    }

    private void generateOneTreeInterface(Options context, Set<String> nodesToGenerate) {
        var optionMap = Template.newContext(context);
        CppTreeEmitter.applyVisitorTypes(optionMap, context);
        optionMap.add("NODES", nodesToGenerate).set("NODES_NAME", v -> v);

        CppTemplate.TREE_ONE.render(optionMap, context.getParserName());
    }

    private static String getVisitorArgumentType(Options o) {
        var ret = o.stringValue(Waggle.JJTREE_VISITOR_DATA_TYPE);
        return (ret == null) || ret.isEmpty() || ret.equals("Object") ? "void *" : ret;
    }

    private static String getVisitorReturnType(Options o) {
        String ret = o.stringValue(Waggle.JJTREE_VISITOR_RETURN_TYPE);
        return (ret == null) || ret.isEmpty() || ret.equals("Object") ? "void " : ret;
    }

    // Used by the CPP code generatror
    private static String toCharArray(String s) {
        var charArray = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            charArray.append("0x").append(Integer.toHexString(s.charAt(i))).append(", ");
        }
        return charArray.toString();
    }
}
