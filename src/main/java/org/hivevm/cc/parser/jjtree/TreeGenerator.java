// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser.jjtree;

import org.hivevm.cc.generator.NodeData;
import org.hivevm.cc.generator.ParserGenerator;
import org.hivevm.cc.model.NodeDescriptor;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.parser.Options;
import org.hivevm.source.LinePrinter;

import java.util.Collection;
import java.util.HashSet;

class TreeGenerator {

    private final NodeData data;
    private final ParserGenerator parser;

    TreeGenerator(ParserGenerator parser) {
        this.data = new NodeData();
        this.parser = parser;
    }

    public NodeData getData() {
        return data;
    }

    final void insertOpenNodeCode(ASTNode node, LinePrinter printer) {
        var options = node.jjtOptions();
        var descriptor = node.node_scope.getNodeDescriptor();

        this.data.addNodeDescriptor(descriptor, options);

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
        this.parser.insertOpenNodeCode(ns, nodeClass, printer, options);
    }

    void insertCloseNodeCode(NodeScope ns, LinePrinter printer, Options options, boolean isFinal) {
        this.parser.insertCloseNodeCode(ns, printer, options, isFinal);
    }

    private void insertCatchBlocks(NodeScope ns, LinePrinter printer, Options options, Collection<String> thrown_set) {
        this.parser.insertCatchBlocks(ns, printer, options, thrown_set);
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
