// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.NodeData;
import org.hivevm.cc.generator.NodeGenerator;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.parser.Options;
import org.hivevm.source.Template;

import java.util.Set;
import java.util.stream.Collectors;

class JavaNodeGenerator implements NodeGenerator {

    @Override
    public final void generate(Options context, NodeData data) {
        generateTreeConstants(context);
        generateVisitors(context);

        // TreeClasses
        generateNode(context);
        generateTreeNodes(context, data.getNodesToGenerate());

        JavaTemplate.NODESTATE.render(context);
    }

    private void generateTreeConstants(Options context) {
        var options = Template.newContext(context);
        options.add("NODE_NAMES", NodeScope.getNodeNames())
                .set("NODE_NAMES_TITLE", i -> i);
        options.add("NODES", NodeScope.getNodeIds().size())
                .set("NODES_ORDINAL", i -> i)
                .set("NODES_LABEL", i -> NodeScope.getNodeIds().get(i));

        JavaTemplate.NODETYPE.render(options, context.getParserName());
    }

    private void generateVisitors(Options context) {
        if (!context.getVisitor()) {
            return;
        }

        var nodeNames = NodeScope.getNodeNames().stream()
                .filter(n -> !n.equals("void"))
                .collect(Collectors.toList());
        var argumentType = JavaNodeGenerator.visitorDataType(context);
        var returnValue = JavaNodeGenerator.returnValue(context.getVisitorReturnType(), argumentType);
        var isVoidReturnType = "void".equals(context.getVisitorReturnType());

        var options = Template.newContext(context);
        options.add("NODES", nodeNames).set("NODES_NAME", i -> i);
        options.set("RETURN_TYPE", context.getVisitorReturnType());
        options.set("RETURN_VALUE", returnValue);
        options.set("RETURN", isVoidReturnType ? "" : "return ");
        options.set("ARGUMENT_TYPE", argumentType);
        options.set("EXCEPTION", JavaNodeGenerator.mergeVisitorException(context));
        options.set(HiveCC.JJTREE_MULTI, context.getMulti());

        JavaTemplate.MULTI_NODE_VISITOR.render(options);
        JavaTemplate.MULTI_NODE_DEFAULT_VISITOR.render(options);
    }

    private void generateNode(Options context) {
        var options = Template.newContext(context);
        options.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, JavaNodeGenerator.visitorDataType(context));

        JavaTemplate.NODE.render(options);
    }

    private void generateTreeNodes(Options context, Set<String> nodesToGenerate) {
        var options = Template.newContext(context);
        options.set(HiveCC.JJTREE_VISITOR_RETURN_VOID, context.getVisitorReturnType().equals("void"));
        options.set(HiveCC.JJTREE_NODE_CLASS, JavaNodeGenerator.nodeClass(context));
        options.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, JavaNodeGenerator.visitorDataType(context));

        var excludes = context.getExcudeNodes();
        for (var nodeType : nodesToGenerate) {
            if (!context.getBuildNodeFiles() || excludes.contains(nodeType)) {
                continue;
            }
            options.set(HiveCC.JJTREE_NODE_TYPE, nodeType);

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
}
