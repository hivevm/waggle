// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.util.HashSet;
import java.util.Set;

import org.hivevm.cc.jjtree.ASTBNFAction;
import org.hivevm.cc.jjtree.ASTBNFDeclaration;
import org.hivevm.cc.jjtree.ASTBNFNodeScope;
import org.hivevm.cc.jjtree.ASTBNFOneOrMore;
import org.hivevm.cc.jjtree.ASTBNFSequence;
import org.hivevm.cc.jjtree.ASTBNFZeroOrMore;
import org.hivevm.cc.jjtree.ASTBNFZeroOrOne;
import org.hivevm.cc.jjtree.ASTCompilationUnit;
import org.hivevm.cc.jjtree.ASTExpansionNodeScope;
import org.hivevm.cc.jjtree.ASTGrammar;
import org.hivevm.cc.jjtree.ASTNode;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeParserDefaultVisitor;
import org.hivevm.cc.jjtree.Node;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.cc.jjtree.Token;

public abstract class TreeGenerator extends JJTreeParserDefaultVisitor {

  private final Set<String> nodesToGenerate = new HashSet<>();

  protected final void addType(String nodeType) {
    if (!nodeType.equals("Node")) {
      this.nodesToGenerate.add(nodeType);
    }
  }

  protected final Iterable<String> nodesToGenerate() {
    return this.nodesToGenerate;
  }

  @Override
  public final Object defaultVisit(Node node, ASTWriter data) {
    data.handleJJTreeNode((ASTNode) node, this);
    return null;
  }

  @Override
  public final Object visit(ASTGrammar node, ASTWriter data) {
    return node.childrenAccept(this, data);
  }

  /**
   * Assume that this action requires an early node close, and then try to decide whether this
   * assumption is false. Do this by looking outwards through the enclosing expansion units. If we
   * ever find that we are enclosed in a unit which is not the final unit in a sequence we know that
   * an early close is not required.
   */
  @Override
  public final Object visit(ASTBNFAction node, ASTWriter writer) {
    NodeScope ns = NodeScope.getEnclosingNodeScope(node);
    if ((ns != null) && !ns.isVoid()) {
      boolean needClose = true;
      Node sp = node.getScopingParent(ns);

      ASTNode n = node;
      while (true) {
        Node p = n.jjtGetParent();
        if (p instanceof ASTBNFSequence) {
          if (n.getOrdinal() != (p.jjtGetNumChildren() - 1)) {
            /* We're not the final unit in the sequence. */
            needClose = false;
            break;
          }
        } else if ((p instanceof ASTBNFZeroOrOne) || (p instanceof ASTBNFZeroOrMore)
            || (p instanceof ASTBNFOneOrMore)) {
          needClose = false;
          break;
        }
        if (p == sp) {
          /* No more parents to look at. */
          break;
        }
        n = (ASTNode) p;
      }
      if (needClose) {
        writer.openCodeBlock(null);
        String indent = writer.setIndent(getIndentation(node) + "  ");
        insertCloseNodeCode(ns, writer, node.jjtOptions(), false);
        writer.setIndent(indent);
        writer.closeCodeBlock();
      }
    }

    return writer.handleJJTreeNode(node, this);
  }

  @Override
  public final Object visit(ASTCompilationUnit node, ASTWriter writer) {
    Token token = node.getFirstToken();

    while (true) {
      writer.printToken(node, token);
      if (token == node.getLastToken()) {
        return null;
      }
      token = token.next;
    }
  }

  @Override
  public final Object visit(ASTBNFDeclaration node, ASTWriter writer) {
    if (!node.node_scope.isVoid()) {
      String indent = "";
      if (node.getLastToken().next == node.getFirstToken()) {
        indent = "  ";
      } else {
        for (int i = 1; i < node.getFirstToken().beginColumn; ++i) {
          indent += " ";
        }
      }

      writer.openCodeBlock(node.node_scope.getNodeDescriptorText());
      String previous = writer.setIndent(indent);
      writer.print(indent);
      insertOpenNodeCode(node.node_scope, writer, node.jjtOptions());
      writer.setIndent(previous);
      writer.closeCodeBlock();
    }

    return writer.handleJJTreeNode(node, this);
  }

  @Override
  public final Object visit(ASTBNFNodeScope node, ASTWriter writer) {
    if (node.node_scope.isVoid()) {
      return writer.handleJJTreeNode(node, this);
    }

    String indent = getIndentation(node.expansion_unit);
    // tryExpansionUnit0(node.node_scope, io, indent, node.expansion_unit);
    node.expansion_unit.jjtAccept(this, writer);
    writer.println();
    writer.print("}");
    String previous = writer.setIndent(indent);
    insertCatchBlocks(node.node_scope, writer, node.expansion_unit);
    writer.setIndent(previous);
    return true;
  }

  @Override
  public final Object visit(ASTExpansionNodeScope node, ASTWriter writer) {
    String indent = getIndentation(node.expansion_unit) + "  ";
    writer.openCodeBlock(node.node_scope.getNodeDescriptor().getDescriptor());
    String previous = writer.setIndent(indent);
    writer.print(indent);
    insertOpenNodeCode(node.node_scope, writer, node.jjtOptions());
    writer.setIndent(previous);
    writer.closeCodeBlock();

    node.expansion_unit.jjtAccept(this, writer);

    previous = writer.setIndent(indent);
    insertCatchBlocks(node.node_scope, writer, node.expansion_unit);
    writer.setIndent(previous);

    // Print the "whiteOut" equivalent of the Node descriptor to preserve
    // line numbers in the generated file.
    ((ASTNodeDescriptor) node.jjtGetChild(1)).jjtAccept(this, writer);
    return null;
  }

  private String getIndentation(ASTNode n) {
    String s = "";
    for (int i = 1; i < n.getFirstToken().beginColumn; ++i) {
      s += " ";
    }
    return s;
  }

  public abstract void generate(TreeOptions context);

  protected abstract void insertOpenNodeCode(NodeScope ns, ASTWriter writer, TreeOptions context);

  protected abstract void insertCloseNodeCode(NodeScope ns, ASTWriter writer, TreeOptions context, boolean isFinal);

  protected abstract void insertCatchBlocks(NodeScope ns, ASTWriter writer, ASTNode expansion_unit);
}
