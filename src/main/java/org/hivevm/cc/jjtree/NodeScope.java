// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

public class NodeScope {

    private final ASTNodeDescriptor node_descriptor;

    public final  String closedVar;
    public final  String exceptionVar;
    public final  String nodeVar;
    private final int    scopeNumber;

    NodeScope(ASTProduction p, ASTNodeDescriptor n) {
        if (n == null) {
            String nm = p.name;
            if (p.jjtOptions().getNodeDefaultVoid())
                nm = "void";
            this.node_descriptor = ASTNodeDescriptor.indefinite(p.jjtParser(), nm);
        }
        else
            this.node_descriptor = n;

        this.scopeNumber = p.getNodeScopeNumber(this);
        this.nodeVar = constructVariable("n");
        this.closedVar = constructVariable("c");
        this.exceptionVar = constructVariable("e");
    }


    public ASTNodeDescriptor getNodeDescriptor() {
        return this.node_descriptor;
    }


    public boolean isVoid() {
        return this.node_descriptor.isVoid();
    }


    public String getNodeDescriptorText() {
        return this.node_descriptor.getDescriptor();
    }


    public String getNodeVariable() {
        return this.nodeVar;
    }


    private String constructVariable(String id) {
        String s = "000" + this.scopeNumber;
        return "jjt" + id + s.substring(s.length() - 3);
    }


    public boolean usesCloseNodeVar() {
        return true;
    }

    public static NodeScope getEnclosingNodeScope(Node node) {
        if (node instanceof ASTBNFDeclaration n)
            return n.node_scope;
        for (Node n = node.jjtGetParent(); n != null; n = n.jjtGetParent()) {
            switch (n) {
                case ASTBNFDeclaration astbnfDeclaration -> {
                    return astbnfDeclaration.node_scope;
                }
                case ASTBNFNodeScope astbnfNodeScope -> {
                    return astbnfNodeScope.node_scope;
                }
                case ASTExpansionNodeScope astExpansionNodeScope -> {
                    return astExpansionNodeScope.node_scope;
                }
                default -> {
                }
            }
        }
        return null;
    }
}