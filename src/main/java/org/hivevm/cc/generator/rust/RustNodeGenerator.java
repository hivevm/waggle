// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import java.util.Set;
import java.util.stream.Collectors;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.NodeData;
import org.hivevm.cc.generator.NodeGenerator;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.parser.Options;
import org.hivevm.source.Template;

class RustNodeGenerator implements NodeGenerator {

    @Override
    public final void generate(Options context, NodeData data) {
        generateTreeState(context);
        generateTreeConstants(context);
        generateVisitors(context);

        // TreeClasses
        generateNode(context);
        generateTreeNodes(context, data.getNodesToGenerate());
    }

    private void generateTreeState(Options context) {
        RustSources.TREE_STATE.render(context);
    }

    private void generateTreeConstants(Options context) {
        var options = Template.newContext(context);
        options.add("NODES", NodeScope.getNodeIds().size())
                .set("LABEL", i -> NodeScope.getNodeIds().get(i))
                .set("TITLE", i -> NodeScope.getNodeNames().get(i));
        RustSources.TREE_CONSTANTS.render(options);
    }

    private void generateVisitors(Options context) {
        if (!context.getVisitor()) {
            return;
        }

        var nodes = NodeScope.getNodeNames().stream()
                .filter(n -> !n.equals("void"))
                .collect(Collectors.toList());
        var argumentType =
                context.getVisitorDataType().isEmpty() ? "Object" : context.getVisitorDataType().trim();
        var returnValue = RustNodeGenerator.returnValue(context.getVisitorReturnType(),
                argumentType);
        var isVoidReturnType = "void".equals(context.getVisitorReturnType());

        var options = Template.newContext(context);
        options.add("NODES", nodes);
        options.set("RETURN_TYPE", context.getVisitorReturnType());
        options.set("RETURN_VALUE", returnValue);
        options.set("RETURN", isVoidReturnType ? "" : "return ");
        options.set("ARGUMENT_TYPE", argumentType);
        options.set("EXCEPTION", RustNodeGenerator.mergeVisitorException(context));
        options.set(HiveCC.JJTREE_MULTI, context.getMulti());

        RustSources.VISITOR.render(options, context.getParserName());
        RustSources.DEFAULT_VISITOR.render(options, context.getParserName());
    }

    private void generateNode(Options context) {
        RustSources.NODE.render(context);
    }

    private void generateTreeNodes(Options context, Set<String> nodesToGenerate) {
        var options = Template.newContext(context);
        options.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
                context.getVisitorReturnType().equals("void"));

        var excludes = context.getExcudeNodes();
        for (String nodeType : nodesToGenerate) {
            if (!context.getBuildNodeFiles() || excludes.contains(nodeType)) {
                continue;
            }
            options.set(HiveCC.JJTREE_NODE_TYPE, nodeType);

            RustSources.MULTI_NODE.render(options, nodeType);
        }
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
}
