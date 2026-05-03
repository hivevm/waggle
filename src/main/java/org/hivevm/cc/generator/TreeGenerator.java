// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;

import org.hivevm.cc.jjtree.ASTBNFNonTerminal;
import org.hivevm.cc.jjtree.ASTNode;
import org.hivevm.cc.model.BNFProduction;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.NodeDescriptor;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.parser.Options;

public abstract class TreeGenerator {

    private final NodeData data;

    protected TreeGenerator() {
        this.data = new NodeData();
    }

    public NodeData getData() {
        return data;
    }

    public final void insertOpenNodeCode(ASTNode node, PrintWriter writer) {
        var options = node.jjtOptions();
        var descriptor = node.node_scope.getNodeDescriptor();

        this.data.addNodeDescriptor(descriptor, options);

        var nodeClass = NodeDescriptor.getNodeClass(descriptor.getName(), options);
        insertOpenNodeCode(node.node_scope, nodeClass, writer, options);
    }

    public final void insertCatchBlocks(NodeScope ns, PrintWriter writer, Options options,
        ASTNode expansion_unit) {
        var thrown_names = new HashSet<String>();
        TreeGenerator.findThrown(expansion_unit, thrown_names);
        insertCatchBlocks(ns, writer, options, thrown_names);
    }

    public abstract void insertOpenNodeCode(NodeScope ns, String nodeClass, PrintWriter writer, Options options);

    public abstract void insertCloseNodeCode(NodeScope ns, PrintWriter writer, Options options, boolean isFinal);

    public abstract void insertCatchBlocks(NodeScope ns, PrintWriter writer, Options options,
        Collection<String> thrown_set);

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
