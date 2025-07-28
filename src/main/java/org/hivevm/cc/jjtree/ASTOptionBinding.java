// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

public class ASTOptionBinding extends ASTNode {

  private boolean suppressed;

  public ASTOptionBinding(JJTreeParser p, int id) {
    super(p, id);
    this.suppressed = false;
  }

  void initialize(String n, String v) {
    // If an option is specific to JJTree it should not be written out
    // to the output file for JavaCC.
    if (getParser().isOptionJJTreeOnly(n))
      this.suppressed = true;
  }

  @Override
  public String translateImage(Token t) {
    return this.suppressed ? whiteOut(t) : t.image;
  }

  @Override
  public final Object jjtAccept(JJTreeParserVisitor visitor, ASTWriter data) {
    return visitor.visit(this, data);
  }
}