// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.generator;

import org.hivevm.waggle.tree.ActionRewriter;
import org.hivevm.waggle.Encoding;
import org.hivevm.waggle.Language;
import org.hivevm.waggle.model.CodeBlock;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.parser.ParserConstants;
import org.hivevm.waggle.parser.Token;
import org.hivevm.source.LinePrinter;

public abstract class CodeGenerator<D> {


    private final Language language;
    private int crow, ccol;

    protected CodeGenerator(Language language) {
        this.language = language;
    }

    protected final Language getLanguage() {
        return this.language;
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
        printer.print(specialTokensOf(t));
        if ((this.ccol != 1) && (this.crow != t.beginLine)) {
            printer.println();
            this.crow++;
            this.ccol = 1;
        }
    }

    protected final void printToken(Token t, LinePrinter printer) {
        printToken(t, null, printer);
    }

    /** Prints the token with the comments before it; {@code $NODE}/{@code $BOOL} refer to {@code ns}. */
    protected final void printToken(Token t, NodeScope ns, LinePrinter printer) {
        var text = specialTokensOf(t) + getStringForTokenOnly(t);
        printer.print(ns != null ? ActionRewriter.rewrite(text, ns) : text);
    }

    /** The special tokens (comments) in front of {@code t}, oldest first, laid out in place. */
    private String specialTokensOf(Token t) {
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
        return sb.toString();
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

}
