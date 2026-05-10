// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser.jjtree;

import org.hivevm.cc.generator.CodeBlock;
import org.hivevm.cc.model.NodeScope;

public class JJTreeVisitor extends NodeDefaultVisitor {

    private final TreeGenerator generator;

    JJTreeVisitor(TreeGenerator generator) {
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
            writer.println();
            writer.print(CodeBlock.begin());
            wrapCloseNodeCode(ns, writer, node);
            writer.print(CodeBlock.end());
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
            } else {
                for (int i = 1; i < node.getFirstToken().beginColumn; ++i) {
                    indent.append(" ");
                }
            }

            writer.println();
            writer.print(CodeBlock.begin());
            wrapOpenNodeCode(node, indent.toString(), writer, node.node_scope.getNodeDescriptorText());
            writer.println(CodeBlock.end());
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
        writer.println("};");
        writer.print(CodeBlock.begin());
        wrapCatchBlocks(node, indent, writer);
        writer.print(CodeBlock.end());
        return true;
    }

    @Override
    public final Object visit(ASTBNFExpansionScope node, ASTWriter writer) {
        String indent = getIndentation(node.expansion_unit) + "  ";
        writer.println();
        writer.print(CodeBlock.begin());
        wrapOpenNodeCode(node, indent, writer, node.node_scope.getNodeDescriptor().getDescriptor());
        writer.println(CodeBlock.end());

        node.expansion_unit.jjtAccept(this, writer);

        writer.println();
        writer.print(CodeBlock.begin());
        wrapCatchBlocks(node, indent, writer);
        writer.print(CodeBlock.end());

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
        writer.print(indent);
        if (comment != null) {
            writer.println("// " + comment);
        }
        generator.insertOpenNodeCode(node, writer);
        writer.outdent();
        writer.println();
    }

    private void wrapCloseNodeCode(NodeScope ns, ASTWriter writer, ASTNode node) {
        writer.indent();
        generator.insertCloseNodeCode(ns, writer, node.jjtOptions(), false);
        writer.outdent();
    }

    private void wrapCatchBlocks(ASTNode node, String indent, ASTWriter writer) {
        writer.indent();
        generator.insertCatchBlocks(node.node_scope, writer, node.expansion_unit.jjtOptions(), node.expansion_unit);
        writer.outdent();
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

    static NodeScope getEnclosingNodeScope(Node node) {
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
}
