// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

public class ASTBNFAction extends ASTNode {

  public ASTBNFAction(JJTreeParser p, int id) {
    super(p, id);
  }

  public Node getScopingParent(NodeScope ns) {
    for (Node n = jjtGetParent(); n != null; n = n.jjtGetParent()) {
      if (n instanceof ASTBNFNodeScope) {
        if (((ASTBNFNodeScope) n).node_scope == ns) {
          return n;
        }
      } else if ((n instanceof ASTExpansionNodeScope) && (((ASTExpansionNodeScope) n).node_scope == ns)) {
        return n;
      }
    }
    return null;
  }


  @Override
  public final Object jjtAccept(JJTreeParserVisitor visitor, ASTWriter data) {
    return visitor.visit(this, data);
  }
}
