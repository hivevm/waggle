// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTProduction;
import org.hivevm.cc.jjtree.NodeType;
import org.hivevm.cc.parser.ParserDescriptor;

public class NodeScope {

    private static final List<String> nodeIds   = new ArrayList<>();
    private static final List<String> nodeNames = new ArrayList<>();
    private static final Set<String>  nodeSeen  = new LinkedHashSet<>();

    private final NodeDescriptor node_descriptor;
    private final int    scopeNumber;

    private final  String closedVar;
    private final  String exceptionVar;
    private final  String nodeVar;

    protected NodeScope(ASTProduction p, ASTNodeDescriptor n) {
        if (n == null) {
            String nm = p.name();
            if (p.jjtOptions().getNodeDefaultVoid())
                nm = "void";
            var nd = new ASTNodeDescriptor(p.jjtParser(), NodeType.JJTNODEDESCRIPTOR);
            nd.setName(nm);
            nd.setFaked();
            setId(nm);//NodeScope.setNodeId(nm);
            this.node_descriptor = nd;
        }
        else {
            this.node_descriptor = n;
            setId(n.getName());
        }

        this.scopeNumber = p.getNodeScopeNumber(this);
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
            setId(nm); //NodeScope.setNodeId(nm);
            this.node_descriptor = nd;
        }
        else {
            this.node_descriptor = n;
            setId(n.getName());
        }

        this.scopeNumber = p.getNodeScopeNumber(this);
        this.nodeVar = constructVariable("n");
        this.closedVar = constructVariable("c");
        this.exceptionVar = constructVariable("e");
    }

    public final int getScopeNumber() {
        return this.scopeNumber;
    }

    public final NodeDescriptor getNodeDescriptor() {
        return this.node_descriptor;
    }

    public final boolean isVoid() {
        return this.node_descriptor.isVoid();
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

    protected final String constructVariable(String id) {
        String s = "000" + this.scopeNumber;
        return "jjt" + id + s.substring(s.length() - 3);
    }

    public static NodeScope create(BNFProduction p, NodeDescriptor nd) {
        return new NodeScope(p, nd);
    }

    public void setId(String name) {
        var nodeId = NodeDescriptor.getNodeId(name);
        if (NodeScope.nodeSeen.add(nodeId)) {
            NodeScope.nodeNames.add(name);
            NodeScope.nodeIds.add(nodeId);
        }
    }

    public static List<String> getNodeIds() {
        return NodeScope.nodeIds;
    }

    public static List<String> getNodeNames() {
        return NodeScope.nodeNames;
    }
}