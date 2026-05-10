// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.NodeData;
import org.hivevm.cc.generator.NodeGenerator;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.parser.Options;
import org.hivevm.source.Template;

import java.util.Set;
import java.util.stream.Collectors;

class CppNodeGenerator implements NodeGenerator {

    @Override
    public final void generate(Options context, NodeData data) {
        generateTreeState(context);
        generateTreeConstants(context);
        generateVisitors(context);

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

    private void generateTreeConstants(Options context) {
        var options = Template.newContext(context);
        options.add("NODES", NodeScope.getNodeIds().size())
                .set("ORDINAL", i -> i)
                .set("LABEL", i -> NodeScope.getNodeIds().get(i));
        options.add("NODE_NAMES", NodeScope.getNodeNames().size())
                .set("ORDINAL", i -> i)
                .set("label", i -> NodeScope.getNodeNames().get(i))
                .set("CHARS", i -> CppNodeGenerator.toCharArray(NodeScope.getNodeNames().get(i)));
        options.set(HiveCC.JJPARSER_CPP_DEFINE, context.getParserName().toUpperCase());

        CppTemplate.TREE_CONSTANTS.render(options, context.getParserName());
    }

    private void generateVisitors(Options context) {
        if (!context.getVisitor()) {
            return;
        }

        var nodeNames = NodeScope.getNodeNames().stream()
                .filter(n -> !n.equals("void"))
                .collect(Collectors.toList());
        var argumentType = CppNodeGenerator.getVisitorArgumentType(context);
        var returnType = CppNodeGenerator.getVisitorReturnType(context);
        if (!context.getVisitorDataType().isEmpty()) {
            argumentType = context.getVisitorDataType();
        }

        var options = Template.newContext(context);
        options.add("NODES", nodeNames).set("NODES_TYPE", n -> "AST" + n);
        options.set(HiveCC.JJPARSER_CPP_DEFINE, context.getParserName().toUpperCase());
        options.set("RETURN_TYPE", returnType);
        options.set("RETURN", returnType.equals("void") ? "" : "return ");
        options.set("ARGUMENT_TYPE", argumentType);
        options.set(HiveCC.JJTREE_MULTI, context.getMulti());

        CppTemplate.VISITOR.render(options, context.getParserName());
    }

    private void generateNode(Options context) {
        var optionMap = Template.newContext(context);
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE,
                CppNodeGenerator.getVisitorReturnType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE,
                CppNodeGenerator.getVisitorArgumentType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
                CppNodeGenerator.getVisitorReturnType(context).equals("void"));

        CppTemplate.NODE.render(optionMap);
    }

    private void generateTreeNodes(Options context, Set<String> nodesToGenerate) {
        var excludes = context.getExcudeNodes();
        for (var nodeType : nodesToGenerate) {
            if (excludes.contains(nodeType)) {
                continue;
            }

            var options = Template.newContext(context);
            options.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE,
                    CppNodeGenerator.getVisitorReturnType(context));
            options.set(HiveCC.JJTREE_VISITOR_DATA_TYPE,
                    CppNodeGenerator.getVisitorArgumentType(context));
            options.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
                    CppNodeGenerator.getVisitorReturnType(context).equals("void"));
            options.set(HiveCC.JJTREE_NODE_TYPE, nodeType);

            CppTemplate.MULTINODE.render(options);
        }
    }

    private void generateNodeInterface(Options context) {
        var optionMap = Template.newContext(context);
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE,
                CppNodeGenerator.getVisitorReturnType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE,
                CppNodeGenerator.getVisitorArgumentType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
                CppNodeGenerator.getVisitorReturnType(context).equals("void"));

        CppTemplate.NODE_H.render(optionMap);
    }

    private void generateTree(Options context) {
        var optionMap = Template.newContext(context);
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE,
                CppNodeGenerator.getVisitorReturnType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE,
                CppNodeGenerator.getVisitorArgumentType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
                CppNodeGenerator.getVisitorReturnType(context).equals("void"));
        optionMap.set(HiveCC.JJTREE_NODE_TYPE, "Tree");

        CppTemplate.TREE.render(optionMap);
    }

    private void generateOneTreeInterface(Options context, Set<String> nodesToGenerate) {
        var optionMap = Template.newContext(context);
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE,
                CppNodeGenerator.getVisitorReturnType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE,
                CppNodeGenerator.getVisitorArgumentType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
                CppNodeGenerator.getVisitorReturnType(context).equals("void"));
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
