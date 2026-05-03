// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.Language;
import org.hivevm.cc.parser.ParserConstants;
import org.hivevm.cc.parser.Token;

public abstract class CodeGenerator<D> {

    private int crow, ccol;


    public abstract void generate(D context);

    protected abstract Language getLanguage();

    protected final void genTokenSetup(Token t) {
        Token tt = t;

        while (tt.specialToken != null) {
            tt = tt.specialToken;
        }

        this.crow = tt.beginLine;
        this.ccol = tt.beginColumn;
    }

    protected final void resetColumn() {
        this.ccol = 1;
    }

    protected final String getLeadingComments(Token t) {
        StringBuilder retval = new StringBuilder();
        if (t.specialToken == null) {
            return retval.toString();
        }
        Token tt = t.specialToken;
        while (tt.specialToken != null) {
            tt = tt.specialToken;
        }
        while (tt != null) {
            retval.append(getStringForTokenOnly(tt));
            tt = tt.next;
        }
        if ((this.ccol != 1) && (this.crow != t.beginLine)) {
            retval.append("\n");
            this.crow++;
            this.ccol = 1;
        }
        return retval.toString();
    }

    protected final String getStringToPrint(Token t) {
        var sb = new StringBuilder();
        Token tt = t.specialToken;
        if (tt != null) {
            while (tt.specialToken != null) {
                tt = tt.specialToken;
            }
            while (tt != null) {
                sb.append(getStringForTokenOnly(tt));
                tt = tt.next;
            }
        }

        return sb.append(getStringForTokenOnly(t)).toString();
    }

    protected final String getStringForTokenOnly(Token t) {
        StringBuilder retval = new StringBuilder();
        for (; this.crow < t.beginLine; this.crow++) {
            retval.append("\n");
            this.ccol = 1;
        }
        for (; this.ccol < t.beginColumn; this.ccol++) {
            retval.append(" ");
        }
        if ((t.kind == ParserConstants.STRING_LITERAL)
                || (t.kind == ParserConstants.CHARACTER_LITERAL)) {
            retval.append(Encoding.escapeUnicode(t.image, getLanguage()));
        }
        else {
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
