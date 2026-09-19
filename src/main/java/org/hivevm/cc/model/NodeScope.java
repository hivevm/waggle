// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.function.Function;

public class NodeScope {

    private final NodeDescriptor node_descriptor;
    private final int scopeNumber;

    private final String closedVar;
    private final String exceptionVar;
    private final String nodeVar;

    protected NodeScope(NodeConfig config, Function<NodeScope, Integer> scope_number) {
        this.node_descriptor = config.node_descriptor();

        this.scopeNumber = scope_number.apply(this);
        this.nodeVar = constructVariable("n");
        this.closedVar = constructVariable("c");
        this.exceptionVar = constructVariable("e");
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
        this.nodeVar = constructVariable("n");
        this.closedVar = constructVariable("c");
        this.exceptionVar = constructVariable("e");
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

    public final String getClosedVariable() {
        return this.closedVar;
    }

    public final String getExceptionVariable() {
        return this.exceptionVar;
    }

    public final String getNodeVariable() {
        return this.nodeVar;
    }

    private String constructVariable(String id) {
        String s = "000" + this.scopeNumber;
        return "jjt" + id + s.substring(s.length() - 3);
    }

    public static NodeScope create(BNFProduction p, NodeDescriptor nd) {
        return new NodeScope(p, nd);
    }

    public record NodeConfig(String id, NodeDescriptor node_descriptor) {
    }
}