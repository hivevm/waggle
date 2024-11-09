// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import java.util.Hashtable;
import java.util.Vector;


public class ASTProduction extends ASTNode {

  String                name;
  private int           nextNodeScopeNumber;
  public Vector<String> throws_list;


  private final Hashtable<NodeScope, Integer> scopes;

  public ASTProduction(JJTreeParser p, int id) {
    super(p, id);
    this.nextNodeScopeNumber = 0;
    this.throws_list = new Vector<>();
    this.scopes = new Hashtable<>();
  }


  int getNodeScopeNumber(NodeScope s) {
    Integer i = this.scopes.get(s);
    if (i == null) {
      i = this.nextNodeScopeNumber++;
      this.scopes.put(s, i);
    }
    return i;
  }

  @Override
  public Object jjtAccept(JJTreeParserVisitor visitor, ASTWriter data) {
    return visitor.visit(this, data);
  }
}
