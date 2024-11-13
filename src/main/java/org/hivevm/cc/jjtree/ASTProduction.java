// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;


public class ASTProduction extends ASTNode {

  String                     name;
  private int                nextNodeScopeNumber;
  private final List<String> throws_list;


  private final Hashtable<NodeScope, Integer> scopes;

  public ASTProduction(JJTreeParser p, int id) {
    super(p, id);
    this.nextNodeScopeNumber = 0;
    this.scopes = new Hashtable<>();
    this.throws_list = new ArrayList<>();
  }

  protected void addThrow(String throw_name) {
    this.throws_list.add(throw_name);
  }

  public final Iterable<String> throwElements() {
    return this.throws_list;
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
