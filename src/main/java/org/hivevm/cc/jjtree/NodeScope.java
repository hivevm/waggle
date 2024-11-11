// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

public class NodeScope {

  private final ASTProduction production;
  private ASTNodeDescriptor   node_descriptor;

  public String               closedVar;
  public String               exceptionVar;
  public String               nodeVar;
  private final int           scopeNumber;

  NodeScope(ASTProduction p, ASTNodeDescriptor n) {
    this.production = p;

    if (n == null) {
      String nm = this.production.name;
      if (p.jjtOptions().getNodeDefaultVoid()) {
        nm = "void";
      }
      this.node_descriptor = ASTNodeDescriptor.indefinite(p.parser, nm);
    } else {
      this.node_descriptor = n;
    }

    this.scopeNumber = this.production.getNodeScopeNumber(this);
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
    if (node instanceof ASTBNFDeclaration) {
      return ((ASTBNFDeclaration) node).node_scope;
    }
    for (Node n = node.jjtGetParent(); n != null; n = n.jjtGetParent()) {
      if (n instanceof ASTBNFDeclaration) {
        return ((ASTBNFDeclaration) n).node_scope;
      } else if (n instanceof ASTBNFNodeScope) {
        return ((ASTBNFNodeScope) n).node_scope;
      } else if (n instanceof ASTExpansionNodeScope) {
        return ((ASTExpansionNodeScope) n).node_scope;
      }
    }
    return null;
  }
}