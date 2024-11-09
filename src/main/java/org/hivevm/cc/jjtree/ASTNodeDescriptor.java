// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class ASTNodeDescriptor extends ASTNode {


  private static List<String>              nodeIds   = new ArrayList<>();
  private static List<String>              nodeNames = new ArrayList<>();
  private static Hashtable<String, String> nodeSeen  = new Hashtable<>();


  String          name;
  boolean         isGT;
  private String  text;
  private boolean faked;

  public ASTNodeDescriptor(JJTreeParser p, int id) {
    super(p, id);
    this.faked = false;
  }

  static ASTNodeDescriptor indefinite(JJTreeParser p, String s) {
    ASTNodeDescriptor nd = new ASTNodeDescriptor(p, JJTreeParserTreeConstants.JJTNODEDESCRIPTOR);
    nd.name = s;
    nd.setNodeIdValue();
    nd.faked = true;
    return nd;
  }

  public static List<String> getNodeIds() {
    return ASTNodeDescriptor.nodeIds;
  }

  public static List<String> getNodeNames() {
    return ASTNodeDescriptor.nodeNames;
  }

  void setNodeIdValue() {
    String k = getNodeId();
    if (!ASTNodeDescriptor.nodeSeen.containsKey(k)) {
      ASTNodeDescriptor.nodeSeen.put(k, k);
      ASTNodeDescriptor.nodeNames.add(this.name);
      ASTNodeDescriptor.nodeIds.add(k);
    }
  }

  public String getNodeId() {
    return "JJT" + this.name.toUpperCase().replace('.', '_');
  }


  boolean isVoid() {
    return this.name.equals("void");
  }

  @Override
  public String toString() {
    if (this.faked) {
      return "(faked) " + this.name;
    } else {
      return super.toString() + ": " + this.name;
    }
  }

  public void setExpressionText(String text) {
    this.text = text;
  }

  public String getDescriptor() {
    if (this.text == null) {
      return this.name;
    } else {
      return "#" + this.name + "(" + (this.isGT ? ">" : "") + this.text + ")";
    }
  }

  public String getNodeType() {
    return jjtOptions().getMulti() ? jjtOptions().getNodePrefix() + this.name : "Node";
  }


  public String openNode(String nodeVar) {
    return "jjtree.openNodeScope(" + nodeVar + ");";
  }


  public String closeNode(String nodeVar) {
    if (this.text == null) {
      return "jjtree.closeNodeScope(" + nodeVar + ", true);";
    } else if (this.isGT) {
      return "jjtree.closeNodeScope(" + nodeVar + ", jjtree.nodeArity() >" + this.text + ");";
    } else {
      return "jjtree.closeNodeScope(" + nodeVar + ", " + this.text + ");";
    }
  }

  @Override
  public String translateImage(Token t) {
    return whiteOut(t);
  }

  @Override
  public final Object jjtAccept(JJTreeParserVisitor visitor, ASTWriter data) {
    return visitor.visit(this, data);
  }
}