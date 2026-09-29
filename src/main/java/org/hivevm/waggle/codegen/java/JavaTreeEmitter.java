// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/JavaCodeGenerator.java, org/javacc/jjtree/CPPCodeGenerator.java

package org.hivevm.waggle.codegen.java;

import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.tree.TreeModel;
import org.hivevm.waggle.tree.TreeOptions;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java's tree support: the code around one node scope, and the tree runtime it calls into.
 */
public class JavaTreeEmitter implements TreeEmitter {

    @Override
    public void emitRuntime(Options context, TreeOptions tree, TreeModel data) {
        generateTreeConstants(context, data);
        generateVisitors(context, tree, data);

        // TreeClasses
        generateNode(context, tree);
        generateTreeNodes(context, tree, data.getNodesToGenerate());

        JavaTemplate.NODESTATE.render(context);
    }

    private void generateTreeConstants(Options context, TreeModel data) {
        var options = OptionsContext.of(context);
        options.add("NODE_NAMES", data.getNodeNames())
                .set("NODE_NAMES_TITLE", i -> i);
        options.add("NODES", data.getNodeIds().size())
                .set("NODES_ORDINAL", i -> i)
                .set("NODES_LABEL", i -> data.getNodeIds().get(i));

        JavaTemplate.NODETYPE.render(options);
    }

    private void generateVisitors(Options context, TreeOptions tree, TreeModel data) {
        if (!tree.visitor()) {
            return;
        }

        var nodeNames = data.getNodeNames().stream()
                .filter(n -> !n.equals("void"))
                .collect(Collectors.toList());
        var argumentType = JavaTreeEmitter.visitorDataType(tree);
        var returnValue = JavaTreeEmitter.returnValue(tree.visitorReturn(), argumentType);
        var isVoidReturnType = "void".equals(tree.visitorReturn());

        var options = OptionsContext.of(context);
        options.add("NODES", nodeNames).set("NODES_NAME", i -> i);
        options.set("RETURN_TYPE", tree.visitorReturn());
        options.set("RETURN_VALUE", returnValue);
        options.set("RETURN", isVoidReturnType ? "" : "return ");
        options.set("ARGUMENT_TYPE", argumentType);
        options.set("EXCEPTION", JavaTreeEmitter.mergeVisitorException(tree));
        options.set(Waggle.NODE_MULTI, tree.multi());

        JavaTemplate.MULTI_NODE_VISITOR.render(options);
        JavaTemplate.MULTI_NODE_DEFAULT_VISITOR.render(options);
    }

    private void generateNode(Options context, TreeOptions tree) {
        var options = OptionsContext.of(context);
        options.set(Waggle.VISITOR_RETURN_TYPE_VOID, tree.visitorReturn().equals("void"));
        options.set(Waggle.VISITOR_DATA_TYPE, JavaTreeEmitter.visitorDataType(tree));

        JavaTemplate.NODE.render(options);
    }

    private void generateTreeNodes(Options context, TreeOptions tree, Set<String> nodesToGenerate) {
        if (!tree.buildNodeFiles()) {
            return;
        }

        var options = OptionsContext.of(context);
        options.set(Waggle.VISITOR_RETURN_TYPE_VOID, tree.visitorReturn().equals("void"));
        options.set(Waggle.NODE_CLASS, tree.nodeBaseClass());
        options.set(Waggle.VISITOR_DATA_TYPE, JavaTreeEmitter.visitorDataType(tree));

        var excludes = tree.customNodes();
        for (var nodeType : nodesToGenerate) {
            if (excludes.contains(nodeType)) {
                continue;
            }
            options.set(Waggle.NODE_TYPE, nodeType);

            JavaTemplate.MULTI_NODE.render(options, nodeType);
        }
    }

    /**
     * The type of the payload passed through {@code jjtAccept}. Defaults to {@code Object}, so that
     * VISITOR without an explicit VISITOR_DATA_TYPE still yields a typed parameter.
     */
    private static String visitorDataType(TreeOptions tree) {
        var dataType = tree.visitorDataType();
        return dataType.isEmpty() ? "Object" : dataType.trim();
    }

    private static String mergeVisitorException(TreeOptions tree) {
        var ve = tree.visitorException();
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
