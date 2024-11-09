// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

public class ASTExpansionNodeScope extends ASTNode {

  public NodeScope  node_scope;
  public ASTNode expansion_unit;

  public ASTExpansionNodeScope(JJTreeParser p, int id) {
    super(p, id);
  }

  @Override
  public final Object jjtAccept(JJTreeParserVisitor visitor, ASTWriter data) {
    return visitor.visit(this, data);
  }
}