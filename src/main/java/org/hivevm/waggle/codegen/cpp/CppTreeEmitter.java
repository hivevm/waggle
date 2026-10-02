// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/CPPCodeGenerator.java, org/javacc/jjtree/JavaCodeGenerator.java

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.source.TemplateSet;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.tree.TreeModel;
import org.hivevm.waggle.tree.TreeOptions;
import org.hivevm.waggle.tree.TreeSupport;

import java.util.List;
import java.util.Set;

/**
 * C++'s tree support: the code around one node scope, and the tree runtime it calls into.
 */
public class CppTreeEmitter implements TreeEmitter {


    /**
     * The C++ nodes have no jjtCreate, and a named factory came out as "Factory->jjtCreate" on a
     * class name.
     */
    @Override
    public TreeSupport support() {
        return new TreeSupport(true, false, true, true);
    }

    @Override
    public void emitRuntime(Options context, TreeOptions tree, TreeModel data) {
        generateTreeState(context);
        generateTreeConstants(context, data);
        generateVisitors(context, tree, data);

        // TreeClasses
        renderWithVisitorTypes(CppTemplate.NODE, context, tree);
        renderWithVisitorTypes(CppTemplate.NODE_H, context, tree);
        renderWithVisitorTypes(CppTemplate.TREE, context, tree);
        generateTreeNodes(context, tree, data.nodeClassesToWrite(tree));
        generateOneTreeInterface(context, data.getNodesToGenerate());
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
                .set("CHARS", i -> CppChars.of(data.getNodeNames().get(i)));

        CppTemplate.TREE_CONSTANTS.render(options, context.getParserName());
    }

    private void generateVisitors(Options context, TreeOptions tree, TreeModel data) {
        if (!tree.visitor()) {
            return;
        }

        var options = OptionsContext.of(context);
        CppTreeEmitter.applyVisitorTypes(options, tree);
        options.add("NODES", data.visitedNodeNames()).set("NODES_TYPE", n -> "AST" + n);

        CppTemplate.VISITOR.render(options, context.getParserName());
    }

    /** Sets the visitor types every tree runtime file that accepts a visitor reads. */
    private static void applyVisitorTypes(OptionsContext optionMap, TreeOptions tree) {
        var returnType = CppTreeEmitter.getVisitorReturnType(tree);
        optionMap.set(Waggle.VISITOR_RETURN_TYPE, returnType);
        optionMap.set(Waggle.VISITOR_DATA_TYPE, CppTreeEmitter.getVisitorArgumentType(tree));
        optionMap.set(Waggle.VISITOR_RETURN_TYPE_VOID, returnType.equals("void"));
    }

    /** Renders a tree runtime file that needs nothing beyond the visitor types. */
    private static void renderWithVisitorTypes(TemplateSet.Source<Options> template, Options context,
                                               TreeOptions tree) {
        var options = OptionsContext.of(context);
        CppTreeEmitter.applyVisitorTypes(options, tree);

        template.render(options);
    }

    private void generateTreeNodes(Options context, TreeOptions tree, List<String> nodeClasses) {
        for (var nodeType : nodeClasses) {
            var options = OptionsContext.of(context);
            CppTreeEmitter.applyVisitorTypes(options, tree);
            options.set(Waggle.NODE_TYPE, nodeType);
            options.set(Waggle.NODE_CLASS, tree.nodeBaseClass());

            CppTemplate.MULTINODE_H.render(options, nodeType);
            CppTemplate.MULTINODE.render(options, nodeType);
        }
    }

    private void generateOneTreeInterface(Options context, Set<String> nodesToGenerate) {
        var optionMap = OptionsContext.of(context);
        optionMap.add("NODES", nodesToGenerate).set("NODES_NAME", v -> v);

        CppTemplate.TREE_ONE.render(optionMap, context.getParserName());
    }

    private static String getVisitorArgumentType(TreeOptions tree) {
        var ret = tree.visitorDataType();
        return (ret == null) || ret.isEmpty() || ret.equals("Object") ? "void *" : ret;
    }

    private static String getVisitorReturnType(TreeOptions tree) {
        String ret = tree.visitorReturn();
        return (ret == null) || ret.isEmpty() || ret.equals("Object") ? "void" : ret;
    }
}
