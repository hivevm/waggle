// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser.jjtree;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.Language;
import org.hivevm.cc.generator.CodeGenerator;
import org.hivevm.source.LinePrinter;
import org.jspecify.annotations.NonNull;

import java.io.Writer;
import java.util.stream.IntStream;


/**
 * The {@link ASTWriter} class.
 */
class ASTWriter implements LinePrinter, AutoCloseable {

    private static final String JJTREE = "jjtree";

    private final LinePrinter printer;
    private final Language language;

    // Indicates whether the token should be replaced by white space or replaced with the actual node
    // variable.
    private boolean whitingOut = false;

    private int indent;

    /**
     * Constructs an instance of {@link ASTWriter}.
     */
    ASTWriter(Writer writer, Language language) {
        this.printer = LinePrinter.wrap(writer);
        this.language = language;
        this.indent = 0;
    }

    public final LinePrinter indent() {
        this.indent++;
        return this;
    }

    public final LinePrinter outdent() {
        this.indent--;
        return this;
    }

    @Override
    public void print(@NonNull String line) {
        printer.print(line);
    }

    @Override
    public void println(@NonNull String line) {
        print(line);
        println();
    }

    @Override
    public void println() {
        printer.println();
        IntStream.of(indent).forEach(indent -> print("    "));
    }

    @Override
    public void close() {
        if (printer instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Prints the token for the node
     */
    final void printToken(ASTNode node, Token token) {
        var tt = token.specialToken;
        if (tt != null) {
            while (tt.specialToken != null) {
                tt = tt.specialToken;
            }
            while (tt != null) {
                print(Encoding.escapeUnicode(node.translateImage(tt), this.language));
                tt = tt.next;
            }
        }

        /*
         * If we're within a node scope we modify the source in the following ways:
         *
         * 1) we rename all references to `NODE' to be references to the actual node variable. 2) we
         * replace all calls to `jjtree.currentNode()' with references to the node variable.
         */
        var scope = JJTreeVisitor.getEnclosingNodeScope(node);
        if (scope == null) {
            // Not within a node scope so we don't need to modify the source.
            print(Encoding.escapeUnicode(node.translateImage(token), this.language));
            return;
        }

        if (CodeGenerator.can_replace(token.image)) {
            var text = Encoding.escapeUnicode(node.translateImage(token), this.language);
            print(CodeGenerator.replace(text, scope));
            return;
        }
        if (this.whitingOut) {
            if (token.image.equals(ASTWriter.JJTREE)) {
                print(scope.getNodeVariable());
                print(" ");
            } else if (token.image.equals(")")) {
                print(" ");
                this.whitingOut = false;
            } else {
                for (int i = 0; i < token.image.length(); ++i) {
                    print(" ");
                }
            }
            return;
        }
        print(Encoding.escapeUnicode(node.translateImage(token), this.language));
    }

    /**
     * This method prints the tokens corresponding to this node recursively calling the print
     * methods of its children. Overriding this print method in appropriate nodes gives the output
     * the added stuff not in the input.
     */
    final void handleJJTreeNode(ASTNode node, NodeVisitor visitor) {
        if (node.getLastToken().next != node.getFirstToken()) {
            Token tokenFirst = node.getFirstToken();
            Token token = new Token();
            token.next = tokenFirst;

            ASTNode n;
            Object end = null;
            for (int ord = 0; ord < node.jjtGetNumChildren(); ord++) {
                n = (ASTNode) node.jjtGetChild(ord);
                while (true) {
                    token = token.next;
                    if (token == n.getFirstToken()) {
                        break;
                    }
                    printToken(node, token);
                }
                end = n.jjtAccept(visitor, this);
                token = n.getLastToken();
            }
            if (end == null) {
                while (token != node.getLastToken()) {
                    token = token.next;
                    printToken(node, token);
                }
            }
        }
    }
}