// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import org.hivevm.cc.generator.CodeBlock;
import org.hivevm.cc.generator.TreeGenerator;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.ZeroOrOne;
import org.hivevm.cc.model.ZeroOrMore;

public class JJTreeVisitor extends NodeDefaultVisitor {

    private final TreeGenerator generator;

    public JJTreeVisitor(TreeGenerator generator) {
        this.generator = generator;
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
     * ever find that we are enclosed in a unit which is not the final unit in a sequence we know
     * that an early close is not required.
     */
    @Override
    public final Object visit(ASTBNFAction node, ASTWriter writer) {
        var ns = JJTreeVisitor.getEnclosingNodeScope(node);
        if ((ns != null) && !ns.isVoid() && JJTreeVisitor.isNeedClose(ns, node)) {
            writer.append("\n").append(CodeBlock.begin());
            wrapCloseNodeCode(ns, writer, node);
            writer.append(CodeBlock.end());
        }

        writer.handleJJTreeNode(node, this);
        return null;
    }

    @Override
    public final Object visit(ASTCompilationUnit node, ASTWriter writer) {
        var token = node.getFirstToken();
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
            var indent = new StringBuilder();
            if (node.getLastToken().next == node.getFirstToken()) {
                indent = new StringBuilder("  ");
            }
            else {
                for (int i = 1; i < node.getFirstToken().beginColumn; ++i) {
                    indent.append(" ");
                }
            }

            writer.append("\n").append(CodeBlock.begin());
            wrapOpenNodeCode(node, indent.toString(), writer, node.node_scope.getNodeDescriptorText());
            writer.append(CodeBlock.end());
            writer.append("\n");
        }

        writer.handleJJTreeNode(node, this);
        return null;
    }

    @Override
    public final Object visit(ASTBNFNodeScope node, ASTWriter writer) {
        if (node.node_scope.isVoid()) {
            writer.handleJJTreeNode(node, this);
            return null;
        }

        String indent = getIndentation(node.expansion_unit);
        // tryExpansionUnit0(node.node_scope, io, indent, node.expansion_unit);
        node.expansion_unit.jjtAccept(this, writer);
        writer.println();
        writer.print("};");
        writer.append("\n").append(CodeBlock.begin());
        wrapCatchBlocks(node, indent, writer);
        writer.append(CodeBlock.end());
        return true;
    }

    @Override
    public final Object visit(ASTBNFExpansionScope node, ASTWriter writer) {
        String indent = getIndentation(node.expansion_unit) + "  ";
        writer.append("\n").append(CodeBlock.begin());
        wrapOpenNodeCode(node, indent, writer, node.node_scope.getNodeDescriptor().getDescriptor());
        writer.append(CodeBlock.end());
        writer.append("\n");

        node.expansion_unit.jjtAccept(this, writer);

        writer.append("\n").append(CodeBlock.begin());
        wrapCatchBlocks(node, indent, writer);
        writer.append(CodeBlock.end());

        // Print the "whiteOut" equivalent of the Node descriptor to preserve
        // line numbers in the generated file.
        node.jjtGetChild(1).jjtAccept(this, writer);
        return null;
    }

    private String getIndentation(ASTNode n) {
        StringBuilder s = new StringBuilder();
        for (int i = 1; i < n.getFirstToken().beginColumn; ++i) {
            s.append(" ");
        }
        return s.toString();
    }

    private void wrapOpenNodeCode(ASTNode node, String indent, ASTWriter writer, String comment) {
        String previous = writer.setIndent(indent);
        writer.print(indent);
        if (comment != null) {
            writer.println("// " + comment);
        }
        generator.insertOpenNodeCode(node, writer);
        writer.setIndent(previous);
        writer.append("\n");
    }

    private void wrapCloseNodeCode(NodeScope ns, ASTWriter writer, ASTNode node) {
        String indent = writer.setIndent(getIndentation(node) + "  ");
        generator.insertCloseNodeCode(ns, writer, node.jjtOptions(), false);
        writer.setIndent(indent);
    }

    private void wrapCatchBlocks(ASTNode node, String indent, ASTWriter writer) {
        String previous = writer.setIndent(indent);
        generator.insertCatchBlocks(node.node_scope, writer, node.expansion_unit.jjtOptions(), node.expansion_unit);
        writer.setIndent(previous);
    }

    private static boolean isNeedClose(NodeScope ns, ASTNode node) {
        boolean needClose = true;
        Node sp = node;
        for (var n = node.jjtGetParent(); n != null; n = n.jjtGetParent()) {
            if (n instanceof ASTNode ast && ast.node_scope == ns)
                sp = n;
        }

        ASTNode n = node;
        while (true) {
            var p = n.jjtGetParent();
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
        return needClose;
    }

    public static NodeScope getEnclosingNodeScope(Node node) {
        if (node instanceof ASTBNFDeclaration n)
            return n.node_scope;
        for (Node n = node.jjtGetParent(); n != null; n = n.jjtGetParent()) {
            switch (n) {
                case ASTBNFExpansionScope ast -> {
                    return ast.node_scope;
                }
                case ASTBNFNodeScope ast -> {
                    return ast.node_scope;
                }
                case ASTBNFDeclaration ast -> {
                    return ast.node_scope;
                }
                default -> {
                }
            }
        }
        return null;
    }

    public static boolean isNeedClose(NodeScope ns, Expansion node) {
        boolean needClose = true;
        Expansion sp = node;
        for (var n = node.parent(); n instanceof Expansion exp; n = exp.parent()) {
            if (exp.getNodeScope() == ns)
                sp = exp;
        }

        Expansion n = node;
        while (true) {
            var p = n.parent();
            if (p instanceof Sequence s) {
                if (!s.isLast(p)) {
                    /* We're not the final unit in the sequence. */
                    needClose = false;
                    break;
                }
            } else if ((p instanceof ZeroOrOne) || (p instanceof ZeroOrMore) || (p instanceof OneOrMore)) {
                needClose = false;
                break;
            }
            if (p == sp) {
                /* No more parents to look at. */
                break;
            }
            n = (Expansion) p;
        }
        return needClose;
    }
}
