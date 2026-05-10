// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.Language;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.parser.ParserConstants;
import org.hivevm.cc.parser.Token;
import org.hivevm.source.LinePrinter;

public abstract class CodeGenerator<D> {

    private static final String JJTREE_BOOL = "$BOOL";
    private static final String JJTREE_NODE = "$NODE";

    private final Language language;
    private int crow, ccol;

    protected CodeGenerator(Language language) {
        this.language = language;
    }

    public abstract void generate(D context);

    protected final void setup_token(Token t) {
        Token tt = t;
        while (tt.specialToken != null) {
            tt = tt.specialToken;
        }
        this.crow = tt.beginLine;
        this.ccol = tt.beginColumn;
    }

    protected final void reset_column() {
        this.ccol = 1;
    }

    protected final void printLeadingComments(LinePrinter printer, Token t) {
        if (t.specialToken == null) {
            return;
        }
        var tt = t.specialToken;
        while (tt.specialToken != null) {
            tt = tt.specialToken;
        }
        while (tt != null) {
            printer.print(getStringForTokenOnly(tt));
            tt = tt.next;
        }
        if ((this.ccol != 1) && (this.crow != t.beginLine)) {
            printer.println();
            this.crow++;
            this.ccol = 1;
        }
    }

    protected final void printToken(Token t, LinePrinter printer) {
        var tt = t.specialToken;
        if (tt != null) {
            while (tt.specialToken != null) {
                tt = tt.specialToken;
            }
            while (tt != null) {
                printer.print(getStringForTokenOnly(tt));
                tt = tt.next;
            }
        }
        printer.print(getStringForTokenOnly(t));
    }

    protected final void printToken(Token t, NodeScope ns, LinePrinter printer) {
        var sb = new StringBuilder();
        var tt = t.specialToken;
        if (tt != null) {
            while (tt.specialToken != null) {
                tt = tt.specialToken;
            }
            while (tt != null) {
                sb.append(getStringForTokenOnly(tt));
                tt = tt.next;
            }
        }
        var text = sb.append(getStringForTokenOnly(t)).toString();
        printer.print(ns != null ? CodeGenerator.replace(text, ns) : text);
    }

    private String getStringForTokenOnly(Token t) {
        var retval = new StringBuilder();
        for (; this.crow < t.beginLine; this.crow++) {
            retval.append("\n");
            this.ccol = 1;
        }
        for (; this.ccol < t.beginColumn; this.ccol++) {
            retval.append(" ");
        }
        if ((t.kind == ParserConstants.STRING_LITERAL)
                || (t.kind == ParserConstants.CHARACTER_LITERAL)) {
            retval.append(Encoding.escapeUnicode(t.image, this.language));
        } else {
            retval.append(CodeBlock.strip(t.image));
        }
        this.crow = t.endLine;
        this.ccol = t.endColumn + 1;
        if (!t.image.isEmpty()) {
            char last = t.image.charAt(t.image.length() - 1);
            if ((last == '\n') || (last == '\r')) {
                this.crow++;
                this.ccol = 1;
            }
        }
        return retval.toString();
    }

    public static boolean can_replace(String image) {
        return image.contains(CodeGenerator.JJTREE_BOOL) || image.contains(CodeGenerator.JJTREE_NODE);
    }

    public static String replace(String text, NodeScope scope) {
        return text.replace(CodeGenerator.JJTREE_BOOL, scope.getClosedVariable()).replace(CodeGenerator.JJTREE_NODE, scope.getNodeVariable());
    }
}
