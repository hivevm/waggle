// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

public class ASTBNFDeclaration extends ASTNode {

  public NodeScope node_scope;

  public ASTBNFDeclaration(JJTreeParser p, int id) {
    super(p, id);
  }

  /** Accept the visitor. **/
  @Override
  public final Object jjtAccept(JJTreeParserVisitor visitor, ASTWriter data) {
    return visitor.visit(this, data);
  }
}