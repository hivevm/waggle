// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/CodeGenerator.java, org/javacc/parser/JavaCCGlobals.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.api.Encoding;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.grammar.ParserConstants;
import org.hivevm.waggle.grammar.Token;
import org.hivevm.waggle.model.CodeBlock;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.tree.ActionRewriter;

/**
 * Lays out verbatim grammar code as the grammar wrote it: the line breaks and the columns between
 * the tokens, and the comments in front of them. It is spelling, not a decision (ADR-0029), and it
 * returns the text rather than writing it, so the output model can hold it (ADR-0031 §4).
 *
 * <p>A cursor lives for one run of tokens. The position used to be a pair of fields on the
 * generator, carried from one run to the next; every run starts over with {@link #at}, so nothing
 * needs to be carried.
 */
public final class TokenCursor {

    private final Language language;
    private int row;
    private int column;

    TokenCursor(Language language, int row, int column) {
        this.language = language;
        this.row = row;
        this.column = column;
    }

    /** A cursor at the start of {@code t}, or of the first comment in front of it. */
    public static TokenCursor at(Token t, Language language) {
        Token tt = t;
        while (tt.specialToken != null) {
            tt = tt.specialToken;
        }
        return new TokenCursor(language, tt.beginLine, tt.beginColumn);
    }

    /** Lays the next token out as if its line started in the first column. */
    void resetColumn() {
        this.column = 1;
    }

    /** The comments in front of {@code t}, and a line break after them if they need one. */
    public String leadingComments(Token t) {
        if (t.specialToken == null) {
            return "";
        }
        var text = specialTokensOf(t);
        if ((this.column != 1) && (this.row != t.beginLine)) {
            this.row++;
            this.column = 1;
            return text + "\n";
        }
        return text;
    }

    /** The comments that follow {@code t}. */
    public String trailingComments(Token t) {
        return (t.next != null) ? leadingComments(t.next) : "";
    }

    /** The token with the comments before it; {@code $NODE}/{@code $BOOL} refer to {@code ns}. */
    public String text(Token t, NodeScope ns) {
        var text = specialTokensOf(t) + tokenOnly(t);
        return (ns != null) ? ActionRewriter.rewrite(text, ns) : text;
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
                sb.append(tokenOnly(tt));
                tt = tt.next;
            }
        }
        return sb.toString();
    }

    private String tokenOnly(Token t) {
        var retval = new StringBuilder();
        for (; this.row < t.beginLine; this.row++) {
            retval.append("\n");
            this.column = 1;
        }
        for (; this.column < t.beginColumn; this.column++) {
            retval.append(" ");
        }
        if ((t.kind == ParserConstants.STRING_LITERAL)
                || (t.kind == ParserConstants.CHARACTER_LITERAL)) {
            retval.append(Encoding.escapeUnicode(t.image, this.language));
        } else {
            retval.append(CodeBlock.strip(t.image));
        }
        this.row = t.endLine;
        this.column = t.endColumn + 1;
        if (!t.image.isEmpty()) {
            char last = t.image.charAt(t.image.length() - 1);
            if ((last == '\n') || (last == '\r')) {
                this.row++;
                this.column = 1;
            }
        }
        return retval.toString();
    }
}
