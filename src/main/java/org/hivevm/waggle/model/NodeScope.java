// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.model;

import java.util.function.Function;

/**
 * The region of a production that one {@code #Node} descriptor covers, and the number that tells it
 * apart from the other scopes of the same production.
 *
 * <p>It is data: the names a back end gives the scope's locals are the back end's business
 * (ADR-0016).
 */
public class NodeScope {

    private final NodeDescriptor node_descriptor;
    private final int scopeNumber;

    protected NodeScope(NodeConfig config, Function<NodeScope, Integer> scope_number) {
        this.node_descriptor = config.node_descriptor();
        this.scopeNumber = scope_number.apply(this);
    }

    private NodeScope(BNFProduction p, NodeDescriptor n) {
        if (n == null) {
            String nm = p.getLhs(); // name
//                if (p.jjtOptions().getNodeDefaultVoid())
//                    nm = "void";
            var nd = new ParserDescriptor(/*p.jjtParser(), NodeType.JJTNODEDESCRIPTOR*/);
            nd.setName(nm);
//                nd.setFaked();
            this.node_descriptor = nd;
        } else {
            this.node_descriptor = n;
        }

        this.scopeNumber = p.getNodeScopeNumber(this);
    }

    public final NodeDescriptor getNodeDescriptor() {
        return this.node_descriptor;
    }

    public final boolean isVoid() {
        return this.node_descriptor.getName().equals("void");
    }

    public final String getNodeDescriptorText() {
        return this.node_descriptor.getDescriptor();
    }

    /** Distinguishes this scope from the other scopes of the same production. */
    public final int getScopeNumber() {
        return this.scopeNumber;
    }

    public static NodeScope create(BNFProduction p, NodeDescriptor nd) {
        return new NodeScope(p, nd);
    }

    public record NodeConfig(String id, NodeDescriptor node_descriptor) {
    }
}