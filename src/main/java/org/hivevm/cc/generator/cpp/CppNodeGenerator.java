// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.NodeData;
import org.hivevm.cc.generator.NodeGenerator;
import org.hivevm.cc.parser.Options;
import org.hivevm.source.Context;
import org.hivevm.source.Template;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

class CppNodeGenerator implements NodeGenerator {

    @Override
    public final void generate(Options context, NodeData data) {
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

    private void generateTreeConstants(Options context, NodeData data) {
        var options = Template.newContext(context);
        options.add("NODES", data.getNodeIds().size())
                .set("ORDINAL", i -> i)
                .set("LABEL", i -> data.getNodeIds().get(i));
        options.add("NODE_NAMES", data.getNodeNames().size())
                .set("ORDINAL", i -> i)
                .set("label", i -> data.getNodeNames().get(i))
                .set("CHARS", i -> CppNodeGenerator.toCharArray(data.getNodeNames().get(i)));
        options.set(HiveCC.JJPARSER_CPP_DEFINE, context.getParserName().toUpperCase(Locale.ROOT));

        CppTemplate.TREE_CONSTANTS.render(options, context.getParserName());
    }

    private void generateVisitors(Options context, NodeData data) {
        if (!context.getVisitor()) {
            return;
        }

        var nodeNames = data.getNodeNames().stream()
                .filter(n -> !n.equals("void"))
                .collect(Collectors.toList());
        var argumentType = CppNodeGenerator.getVisitorArgumentType(context);
        var returnType = CppNodeGenerator.getVisitorReturnType(context);
        if (!context.getVisitorDataType().isEmpty()) {
            argumentType = context.getVisitorDataType();
        }

        var options = Template.newContext(context);
        options.add("NODES", nodeNames).set("NODES_TYPE", n -> "AST" + n);
        options.set(HiveCC.JJPARSER_CPP_DEFINE, context.getParserName().toUpperCase(Locale.ROOT));
        options.set("RETURN_TYPE", returnType);
        options.set("RETURN", returnType.equals("void") ? "" : "return ");
        options.set("ARGUMENT_TYPE", argumentType);
        options.set(HiveCC.JJTREE_MULTI, context.getMulti());

        CppTemplate.VISITOR.render(options, context.getParserName());
    }

    /**
     * Sets the three visitor-type options on a render context, computing the return type once
     * instead of the three repeated {@code getVisitorReturnType} lookups the call sites used to do.
     */
    private static void applyVisitorTypes(Context optionMap, Options context) {
        var returnType = CppNodeGenerator.getVisitorReturnType(context);
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, returnType);
        optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppNodeGenerator.getVisitorArgumentType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID, returnType.equals("void"));
    }

    private void generateNode(Options context) {
        var optionMap = Template.newContext(context);
        CppNodeGenerator.applyVisitorTypes(optionMap, context);

        CppTemplate.NODE.render(optionMap);
    }

    private void generateTreeNodes(Options context, Set<String> nodesToGenerate) {
        var excludes = context.getExcudeNodes();
        for (var nodeType : nodesToGenerate) {
            if (excludes.contains(nodeType)) {
                continue;
            }

            var options = Template.newContext(context);
            CppNodeGenerator.applyVisitorTypes(options, context);
            options.set(HiveCC.JJTREE_NODE_TYPE, nodeType);
            options.set(HiveCC.JJTREE_NODE_CLASS, CppNodeGenerator.nodeClass(context));

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
        CppNodeGenerator.applyVisitorTypes(optionMap, context);

        CppTemplate.NODE_H.render(optionMap);
    }

    private void generateTree(Options context) {
        var optionMap = Template.newContext(context);
        CppNodeGenerator.applyVisitorTypes(optionMap, context);
        optionMap.set(HiveCC.JJTREE_NODE_TYPE, "Tree");

        CppTemplate.TREE.render(optionMap);
    }

    private void generateOneTreeInterface(Options context, Set<String> nodesToGenerate) {
        var optionMap = Template.newContext(context);
        CppNodeGenerator.applyVisitorTypes(optionMap, context);
        optionMap.add("NODES", nodesToGenerate).set("NODES_NAME", v -> v);

        CppTemplate.TREE_ONE.render(optionMap, context.getParserName());
    }

    private static String getVisitorArgumentType(Options o) {
        var ret = o.stringValue(HiveCC.JJTREE_VISITOR_DATA_TYPE);
        return (ret == null) || ret.isEmpty() || ret.equals("Object") ? "void *" : ret;
    }

    private static String getVisitorReturnType(Options o) {
        String ret = o.stringValue(HiveCC.JJTREE_VISITOR_RETURN_TYPE);
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
