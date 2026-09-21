// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

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
        var options = node.jjtOptions();
        var descriptor = node.node_scope.getNodeDescriptor();

        this.data.addNodeDescriptor(descriptor, TreeOptions.from(options));

        var nodeClass =
                NodeDescriptor.getNodeClass(descriptor.getName(), options.getMulti(), options.getNodeClass());
        insertOpenNodeCode(node.node_scope, nodeClass, printer, options);
    }

    final void insertCatchBlocks(NodeScope ns, LinePrinter printer, Options options,
                                 ASTNode expansion_unit) {
        var thrown_names = new HashSet<String>();
        TreeGenerator.findThrown(expansion_unit, thrown_names);
        insertCatchBlocks(ns, printer, options, thrown_names);
    }

    private void insertOpenNodeCode(NodeScope ns, String nodeClass, LinePrinter printer, Options options) {
        this.emitter.openScope(ns, nodeClass, printer, options);
    }

    void insertCloseNodeCode(NodeScope ns, LinePrinter printer, Options options, boolean isFinal) {
        this.emitter.closeScope(ns, printer, options, isFinal);
    }

    private void insertCatchBlocks(NodeScope ns, LinePrinter printer, Options options, Collection<String> thrown_set) {
        this.emitter.catchBlocks(ns, printer, options, thrown_set);
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
