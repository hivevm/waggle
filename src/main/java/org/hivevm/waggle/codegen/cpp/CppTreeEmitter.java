// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/CPPCodeGenerator.java, org/javacc/jjtree/JavaCodeGenerator.java

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.source.Context;
import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.api.GenerationException;
import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.tree.ScopeVariables;
import org.hivevm.waggle.tree.TreeModel;
import org.hivevm.waggle.tree.TreeOptions;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * C++'s tree support: the code around one node scope, and the tree runtime it calls into.
 */
public class CppTreeEmitter implements TreeEmitter {

    @Override
    public void openScope(NodeScope ns, String nodeClass, LinePrinter printer, TreeOptions options) {
        // NODE_FACTORY is rejected in emitRuntime.
        printer.println(nodeClass + " *" + ScopeVariables.node(ns) + " = new " + nodeClass + "("
                + ns.getNodeDescriptor().getNodeId() + ");");

        printer.println("bool " + ScopeVariables.closed(ns) + " = true;");

        printer.println(ScopeVariables.openCall(ns));
        if (options.scopeHook())
            printer.println("jjtreeOpenNodeScope(" + ScopeVariables.node(ns) + ");");

        if (options.trackTokens()) {
            printer.println(ScopeVariables.node(ns) + "->jjtSetFirstToken(getToken(1));");
        }
        printer.print("try {");
    }

    @Override
    public void closeScope(NodeScope ns, LinePrinter printer, TreeOptions options, boolean isFinal) {
        printer.println(ScopeVariables.closeCall(ns));
        if (!isFinal) {
            printer.println(ScopeVariables.closed(ns) + " = false;");
        }
        if (options.scopeHook()) {
            printer.println("if (jjtree.nodeCreated()) {");
            printer.println(" jjtreeCloseNodeScope(" + ScopeVariables.node(ns) + ");");
            printer.println("}");
        }

        if (options.trackTokens()) {
            printer.println(ScopeVariables.node(ns) + "->jjtSetLastToken(getToken(0));");
        }
    }

    @Override
    public void catchBlocks(NodeScope ns, LinePrinter printer, TreeOptions options) {
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


    @Override
    public void emitRuntime(Options context, TreeOptions tree, TreeModel data) {
        if (!tree.nodeFactory().isEmpty()) {
            // The C++ nodes have no jjtCreate, and a named factory came out as "Factory->jjtCreate"
            // on a class name. Fail instead of emitting C++ that cannot compile (SPECIFICATION.md
            // §3: target feature gaps are tracked, not silently produced).
            throw new GenerationException("NODE_FACTORY is not supported for the C++ target.");
        }
        generateTreeState(context);
        generateTreeConstants(context, data);
        generateVisitors(context, tree, data);

        // TreeClasses
        generateNode(context, tree);
        generateNodeInterface(context, tree);
        generateTree(context, tree);
        generateTreeNodes(context, tree, data.getNodesToGenerate());
        generateOneTreeInterface(context, tree, data.getNodesToGenerate());
    }

    private void generateTreeState(Options context) {
        CppTemplate.TREESTATE_H.render(context);
        CppTemplate.TREESTATE.render(context);
    }

    private void generateTreeConstants(Options context, TreeModel data) {
        var options = OptionsContext.of(context);
        options.add("NODES", data.getNodeIds().size())
                .set("ORDINAL", i -> i)
                .set("LABEL", i -> data.getNodeIds().get(i));
        options.add("NODE_NAMES", data.getNodeNames().size())
                .set("ORDINAL", i -> i)
                .set("label", i -> data.getNodeNames().get(i))
                .set("CHARS", i -> CppTreeEmitter.toCharArray(data.getNodeNames().get(i)));
        options.set(Waggle.CPP_DEFINE, context.getParserName().toUpperCase(Locale.ROOT));

        CppTemplate.TREE_CONSTANTS.render(options, context.getParserName());
    }

    private void generateVisitors(Options context, TreeOptions tree, TreeModel data) {
        if (!tree.visitor()) {
            return;
        }

        var nodeNames = data.getNodeNames().stream()
                .filter(n -> !n.equals("void"))
                .collect(Collectors.toList());
        var argumentType = CppTreeEmitter.getVisitorArgumentType(tree);
        var returnType = CppTreeEmitter.getVisitorReturnType(tree);
        if (!tree.visitorDataType().isEmpty()) {
            argumentType = tree.visitorDataType();
        }

        var options = OptionsContext.of(context);
        options.add("NODES", nodeNames).set("NODES_TYPE", n -> "AST" + n);
        options.set(Waggle.CPP_DEFINE, context.getParserName().toUpperCase(Locale.ROOT));
        options.set("RETURN_TYPE", returnType);
        options.set("RETURN", returnType.equals("void") ? "" : "return ");
        options.set("ARGUMENT_TYPE", argumentType);
        options.set(Waggle.NODE_MULTI, tree.multi());

        CppTemplate.VISITOR.render(options, context.getParserName());
    }

    /**
     * Sets the three visitor-type options on a render context, computing the return type once
     * instead of the three repeated {@code getVisitorReturnType} lookups the call sites used to do.
     */
    private static void applyVisitorTypes(Context optionMap, TreeOptions tree) {
        var returnType = CppTreeEmitter.getVisitorReturnType(tree);
        optionMap.set(Waggle.VISITOR_RETURN_TYPE, returnType);
        optionMap.set(Waggle.VISITOR_DATA_TYPE, CppTreeEmitter.getVisitorArgumentType(tree));
        optionMap.set(Waggle.VISITOR_RETURN_TYPE_VOID, returnType.equals("void"));
    }

    private void generateNode(Options context, TreeOptions tree) {
        var optionMap = OptionsContext.of(context);
        CppTreeEmitter.applyVisitorTypes(optionMap, tree);

        CppTemplate.NODE.render(optionMap);
    }

    private void generateTreeNodes(Options context, TreeOptions tree, Set<String> nodesToGenerate) {
        var excludes = tree.customNodes();
        for (var nodeType : nodesToGenerate) {
            if (excludes.contains(nodeType)) {
                continue;
            }

            var options = OptionsContext.of(context);
            CppTreeEmitter.applyVisitorTypes(options, tree);
            options.set(Waggle.NODE_TYPE, nodeType);
            options.set(Waggle.NODE_CLASS, tree.nodeBaseClass());

            CppTemplate.MULTINODE_H.render(options, nodeType);
            CppTemplate.MULTINODE.render(options, nodeType);
        }
    }

    /**
     * The base class the generated node classes extend. Defaults to the generated {@code Node}, so
     * that a grammar which does not supply a NODE_CLASS still yields compilable node classes.
     */
    private void generateNodeInterface(Options context, TreeOptions tree) {
        var optionMap = OptionsContext.of(context);
        CppTreeEmitter.applyVisitorTypes(optionMap, tree);

        CppTemplate.NODE_H.render(optionMap);
    }

    private void generateTree(Options context, TreeOptions tree) {
        var optionMap = OptionsContext.of(context);
        CppTreeEmitter.applyVisitorTypes(optionMap, tree);
        optionMap.set(Waggle.NODE_TYPE, "Tree");

        CppTemplate.TREE.render(optionMap);
    }

    private void generateOneTreeInterface(Options context, TreeOptions tree, Set<String> nodesToGenerate) {
        var optionMap = OptionsContext.of(context);
        CppTreeEmitter.applyVisitorTypes(optionMap, tree);
        optionMap.add("NODES", nodesToGenerate).set("NODES_NAME", v -> v);

        CppTemplate.TREE_ONE.render(optionMap, context.getParserName());
    }

    private static String getVisitorArgumentType(TreeOptions tree) {
        var ret = tree.visitorDataType();
        return (ret == null) || ret.isEmpty() || ret.equals("Object") ? "void *" : ret;
    }

    private static String getVisitorReturnType(TreeOptions tree) {
        String ret = tree.visitorReturn();
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
