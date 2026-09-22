// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/JavaCodeGenerator.java, org/javacc/jjtree/CPPCodeGenerator.java

package org.hivevm.waggle.jjtree;

import org.hivevm.waggle.tree.TreeModel;
import org.hivevm.waggle.tree.TreeOptions;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.model.NodeDescriptor;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.api.Options;
import org.hivevm.source.LinePrinter;

import java.util.Collection;
import java.util.HashSet;

class TreeGenerator {

    private final TreeModel data;
    private final TreeEmitter emitter;

    TreeGenerator(TreeEmitter emitter) {
        this.data = new TreeModel();
        this.emitter = emitter;
    }

    public TreeModel getData() {
        return data;
    }

    final void insertOpenNodeCode(ASTNode node, LinePrinter printer) {
        var tree = TreeOptions.from(node.jjtOptions());
        var descriptor = node.node_scope.getNodeDescriptor();

        this.data.addNodeDescriptor(descriptor, tree);

        var nodeClass =
                NodeDescriptor.getNodeClass(descriptor.getName(), tree.multi(), tree.nodeClass());
        insertOpenNodeCode(node.node_scope, nodeClass, printer, tree);
    }

    final void insertCatchBlocks(NodeScope ns, LinePrinter printer, Options options,
                                 ASTNode expansion_unit) {
        var thrown_names = new HashSet<String>();
        TreeGenerator.findThrown(expansion_unit, thrown_names);
        insertCatchBlocks(ns, printer, options, thrown_names);
    }

    private void insertOpenNodeCode(NodeScope ns, String nodeClass, LinePrinter printer, TreeOptions tree) {
        this.emitter.openScope(ns, nodeClass, printer, tree);
    }

    void insertCloseNodeCode(NodeScope ns, LinePrinter printer, Options options, boolean isFinal) {
        this.emitter.closeScope(ns, printer, TreeOptions.from(options), isFinal);
    }

    private void insertCatchBlocks(NodeScope ns, LinePrinter printer, Options options, Collection<String> thrown_set) {
        this.emitter.catchBlocks(ns, printer, TreeOptions.from(options), thrown_set);
    }

    private static void findThrown(ASTNode expansion_unit, Collection<String> thrown_set) {
        if (expansion_unit instanceof ASTBNFNonTerminal) {
            // Should really make the nonterminal explicitly maintain its name.
            var nt = expansion_unit.getFirstToken().image;
            var prod = expansion_unit.jjtParser().getProduction(nt);
            if (prod != null) {
                prod.throwElements().forEach(thrown_set::add);
            }
        }
        for (int i = 0; i < expansion_unit.jjtGetNumChildren(); ++i) {
            TreeGenerator.findThrown((ASTNode) expansion_unit.jjtGetChild(i), thrown_set);
        }
    }
}
