// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

public class ASTOptionBinding extends ASTNode {

  private String  name;
  private boolean suppressed;

  public ASTOptionBinding(JJTreeParser p, int id) {
    super(p, id);
    this.suppressed = false;
  }

  void initialize(String n, String v) {
    this.name = n;

    // If an option is specific to JJTree it should not be written out
    // to the output file for JavaCC.

    if (getParser().isOptionJJTreeOnly(this.name)) {
      this.suppressed = true;
    }
  }

  @Override
  public String translateImage(Token t) {
    if (this.suppressed) {
      return whiteOut(t);
    } else {
      return t.image;
    }
  }

  @Override
  public final Object jjtAccept(JJTreeParserVisitor visitor, ASTWriter data) {
    return visitor.visit(this, data);
  }
}