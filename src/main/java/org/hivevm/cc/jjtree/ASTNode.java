// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

public class ASTNode extends Node {

  private int myOrdinal;

  ASTNode(JJTreeParser p, int i) {
    super(p, i);
  }

  public final JJTreeOptions jjtOptions() {
    return this.parser.getOptions();
  }

  @Override
  public void jjtAddChild(Node n, int i) {
    super.jjtAddChild(n, i);
    ((ASTNode) n).setOrdinal(i);
  }

  public int getOrdinal() {
    return this.myOrdinal;
  }

  public void setOrdinal(int o) {
    this.myOrdinal = o;
  }


  /*****************************************************************
   *
   * The following is added manually to enhance all tree nodes with attributes that store the first
   * and last tokens corresponding to each node, as well as to print the tokens back to the
   * specified output stream.
   *
   *****************************************************************/

  private Token first, last;

  public Token getFirstToken() {
    return this.first;
  }

  public void setFirstToken(Token t) {
    this.first = t;
  }

  public Token getLastToken() {
    return this.last;
  }

  public void setLastToken(Token t) {
    this.last = t;
  }

  public String translateImage(Token t) {
    return t.image;
  }

  String whiteOut(Token t) {
    StringBuilder sb = new StringBuilder(t.image.length());

    for (int i = 0; i < t.image.length(); ++i) {
      char ch = t.image.charAt(i);
      if ((ch != '\t') && (ch != '\n') && (ch != '\r') && (ch != '\f')) {
        sb.append(' ');
      } else {
        sb.append(ch);
      }
    }

    return sb.toString();
  }
}
