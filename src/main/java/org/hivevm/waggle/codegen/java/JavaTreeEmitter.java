// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/JavaCodeGenerator.java, org/javacc/jjtree/CPPCodeGenerator.java

package org.hivevm.waggle.codegen.java;

import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.codegen.ListModel;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.tree.TreeModel;
import org.hivevm.waggle.tree.TreeOptions;

import java.util.List;

/**
 * Java's tree support: the code around one node scope, and the tree runtime it calls into.
 */
public class JavaTreeEmitter implements TreeEmitter {

    /** What the default visitor returns, when it returns something. */
    private static final String VISITOR_RETURN_VALUE = "VISITOR_RETURN_VALUE";

    @Override
    public void emitRuntime(Options context, TreeOptions tree, TreeModel data) {
        generateTreeConstants(context, data);
        generateVisitors(context, tree, data);

        // TreeClasses
        generateNode(context, tree);
        generateTreeNodes(context, tree, data.nodeClassesToWrite(tree));

        JavaTemplate.NODESTATE.render(context);
    }

    private void generateTreeConstants(Options context, TreeModel data) {
        var options = OptionsContext.of(context);
        options.set("NODE_NAMES", ListModel.names(data.getNodeNames()));
        options.set("NODES", ListModel.numbered(data.getNodeIds(), name -> name));

        JavaTemplate.NODETYPE.render(options);
    }

    private void generateVisitors(Options context, TreeOptions tree, TreeModel data) {
        if (!tree.visitor()) {
            return;
        }

        var options = OptionsContext.of(context);
        JavaTreeEmitter.applyVisitorTypes(options, tree);
        options.set("NODES", ListModel.names(data.visitedNodeNames()));
        options.set(JavaTreeEmitter.VISITOR_RETURN_VALUE,
                JavaTreeEmitter.returnValue(tree.visitorReturn(),
                        JavaTreeEmitter.visitorDataType(tree)));

        JavaTemplate.MULTI_NODE_VISITOR.render(options);
        JavaTemplate.MULTI_NODE_DEFAULT_VISITOR.render(options);
    }

    private void generateNode(Options context, TreeOptions tree) {
        var options = OptionsContext.of(context);
        JavaTreeEmitter.applyVisitorTypes(options, tree);

        JavaTemplate.NODE.render(options);
    }

    private void generateTreeNodes(Options context, TreeOptions tree, List<String> nodeClasses) {
        var options = OptionsContext.of(context);
        JavaTreeEmitter.applyVisitorTypes(options, tree);
        options.set(Waggle.NODE_CLASS, tree.nodeBaseClass());

        for (var nodeType : nodeClasses) {
            options.set(Waggle.NODE_TYPE, nodeType);

            JavaTemplate.MULTI_NODE.render(options, nodeType);
        }
    }

    /** Sets the visitor types every tree runtime file that accepts a visitor reads. */
    private static void applyVisitorTypes(OptionsContext options, TreeOptions tree) {
        options.set(Waggle.VISITOR_RETURN_TYPE, tree.visitorReturn());
        options.set(Waggle.VISITOR_RETURN_TYPE_VOID, tree.visitorReturn().equals("void"));
        options.set(Waggle.VISITOR_DATA_TYPE, JavaTreeEmitter.visitorDataType(tree));
    }

    /**
     * The type of the payload passed through {@code jjtAccept}. Defaults to {@code Object}, so that
     * VISITOR without an explicit VISITOR_DATA_TYPE still yields a typed parameter.
     */
    private static String visitorDataType(TreeOptions tree) {
        var dataType = tree.visitorDataType();
        return dataType.isEmpty() ? "Object" : dataType.trim();
    }

    /**
     * What the default visitor returns: the payload when it has the return type, else the default
     * value of that type.
     */
    private static String returnValue(String returnType, String argumentType) {
        if (returnType.equals(argumentType)) {
            return "data";
        }
        return switch (returnType) {
            case "boolean" -> "false";
            case "int", "short", "byte" -> "0";
            case "long" -> "0L";
            case "double" -> "0.0d";
            case "float" -> "0.0f";
            case "char" -> "'\\u0000'";
            default -> "null";
        };
    }
}
