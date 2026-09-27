// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/JavaCCParserInternals.java, org/javacc/jjtree/TokenUtils.java

package org.hivevm.waggle.grammar;
import org.hivevm.waggle.model.RegExprSpec;

import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.api.WaggleOptions;
import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.BNFProduction;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.REndOfFile;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.TokenKind;
import org.hivevm.waggle.model.TokenProduction;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities.
 */
abstract class AbstractGrammarParser implements ParserConstants {

    private GrammarData data;
    private int nextFreeLexState;

    /**
     * This int variable is incremented while parsing local lookaheads. Hence it keeps track of
     * *syntactic* lookahead nesting. This is used to provide warnings when actions and nested
     * lookaheads are used in syntactic lookahead productions. This is to prevent typos such as
     * leaving out the comma in LOOKAHEAD( foo(), {check()} ).
     */
    protected int inLocalLA;

    /**
     * Constructs an instance of {@link AbstractGrammarParser}.
     */
    protected AbstractGrammarParser() {
        this.nextFreeLexState = 1;
        this.inLocalLA = 0;
    }

    /** The options of this generation; a grammar's {@code options} block writes into them. */
    protected final WaggleOptions getOptions() {
        return this.data.options();
    }

    final void initialize(GrammarData data) {
        this.data = data;
    }

    /**
     * What this generation has to say about the grammar. Grammar actions report through it, so the
     * generated parser needs it too — which is why it lives on the hand-written base class
     * (ADR-0015).
     */
    protected final Diagnostics diagnostics() {
        return this.data.diagnostics();
    }

    protected void addproduction(NormalProduction p) {
        this.data.addNormalProduction(p);
    }

    protected void production_addexpansion(BNFProduction p, Expansion e) {
        e.setParent(p);
        p.setExpansion(e);
    }

    protected void addregexpr(TokenProduction p) {
        this.data.addTokenProduction(p);
        if (p.getLexStates() == null) {
            return;
        }
        for (int i = 0; i < p.getLexStates().length; i++) {
            for (int j = 0; j < i; j++) {
                if (p.getLexStates()[i].equals(p.getLexStates()[j])) {
                    diagnostics().error(p,
                            "Multiple occurrence of \"" + p.getLexStates()[i]
                                    + "\" in lexical state list.");
                }
            }
            if (this.data.isNewLexState(p.getLexStates()[i])) {
                this.data.setLexState(p.getLexStates()[i], this.nextFreeLexState++);
            }
        }
    }

    protected void add_inline_regexpr(RExpression r) {
        if (!(r instanceof REndOfFile)) {
            var p = new TokenProduction(TokenKind.TOKEN);
            p.setExplicit(false);

            var res = new RegExprSpec(r, p);
            res.act = new Action();
            res.nextState = null;
            res.nsTok = null;
            p.getRespecs().add(res);
            this.data.addTokenProduction(p);
        }
    }

    /**
     * The value of a string literal: the quotes dropped, the escapes resolved.
     *
     * <p>Only the escapes STRING_LITERAL admits reach here — {@code n t b r f \ ' "} and octal: the
     * character stream resolves a Unicode escape before the lexer runs.
     */
    protected String remove_escapes_and_quotes(Token t, String str) {
        var retval = new StringBuilder();
        int index = 1;
        while (index < (str.length() - 1)) {
            char ch = str.charAt(index++);
            if (ch != '\\') {
                retval.append(ch);
                continue;
            }
            ch = str.charAt(index++);
            switch (ch) {
                case 'b' -> retval.append('\b');
                case 't' -> retval.append('\t');
                case 'n' -> retval.append('\n');
                case 'f' -> retval.append('\f');
                case 'r' -> retval.append('\r');
                case '"', '\'', '\\' -> retval.append(ch);
                default -> {
                    int ordinal = ch - '0';
                    char ch1 = str.charAt(index);
                    if ((ch1 >= '0') && (ch1 <= '7')) {
                        ordinal = ((ordinal * 8) + ch1) - '0';
                        index++;
                        ch1 = str.charAt(index);
                        if ((ch <= '3') && (ch1 >= '0') && (ch1 <= '7')) {
                            ordinal = ((ordinal * 8) + ch1) - '0';
                            index++;
                        }
                    }
                    retval.append((char) ordinal);
                }
            }
        }
        return retval.toString();
    }

    protected char character_descriptor_assign(Token t, String s) {
        if (s.length() != 1) {
            diagnostics().error(t, "String in character list may contain only one character.");
            return ' ';
        } else {
            return s.charAt(0);
        }
    }

    protected char character_descriptor_assign(Token t, String s, String left) {
        if (s.length() != 1) {
            diagnostics().error(t, "String in character list may contain only one character.");
            return ' ';
        } else if ((left.charAt(0)) > (s.charAt(0))) {
            diagnostics().error(t, "Right end of character range '" + s
                    + "' has a lower ordinal value than the left end of character range '" + left
                    + "'.");
            return left.charAt(0);
        } else {
            return s.charAt(0);
        }
    }

    /*
     * Returns true if the next token is not in the FOLLOW list of "expansion". It is used to decide
     * when the end of an "expansion" has been reached.
     */
    protected boolean notTailOfExpansionUnit() {
        Token t;
        t = getToken(1);
        return (t.kind != ParserConstants.BIT_OR) && (t.kind != ParserConstants.COMMA)
                && (t.kind != ParserConstants.RPAREN) && (t.kind != ParserConstants.RBRACE)
                && (t.kind != ParserConstants.RBRACKET) && (t.kind != ParserConstants.SEMICOLON);
    }

    protected abstract Token getToken(int index);

    /**
     * Collects every token of the linked list from {@code first} up to and including {@code last}
     * into {@code tokens}. Does nothing for an empty sequence (i.e. when {@code last} precedes
     * {@code first}).
     */
    protected void collectTokens(List<Token> tokens, Token first, Token last) {
        if (last.next != first) { // i.e., this is not an empty sequence
            Token t = first;
            while (true) {
                tokens.add(t);
                if (t == last) {
                    break;
                }
                t = t.next;
            }
        }
    }

    /**
     * The value of an integer literal, read as Java reads it: {@code 0x10} is 16, {@code 010} is 8
     * and an {@code L} suffix is allowed. Integer.parseInt accepted only the decimal form the token
     * also admits, and failed on the others without a position.
     */
    protected final int integerValue(Token token) {
        String text = token.image;
        if (text.endsWith("l") || text.endsWith("L")) {
            text = text.substring(0, text.length() - 1);
        }
        try {
            return Integer.decode(text);
        } catch (NumberFormatException e) {
            diagnostics().error(token, "The number " + token.image + " is too large for an int.");
            return 0;
        }
    }

    /**
     * The tokens from {@code first} to {@code last} as one token, for a return type: the type
     * {@code List<Foo>} is four tokens, and only the last of them, {@code >}, used to be kept. The
     * images are joined as written, with one space where there was white space between them.
     */
    protected static Token typeToken(Token first, Token last) {
        var image = new StringBuilder(first.image);
        for (Token t = first; t != last; t = t.next) {
            Token next = t.next;
            if ((next.beginLine != t.endLine) || (next.beginColumn > t.endColumn + 1)) {
                image.append(' ');
            }
            image.append(next.image);
        }

        var type = new Token(last.kind, image.toString());
        type.beginLine = first.beginLine;
        type.beginColumn = first.beginColumn;
        type.endLine = last.endLine;
        type.endColumn = last.endColumn;
        return type;
    }

    /**
     * Parses an argument list whose tokens are not needed by the caller.
     */
    protected void Arguments() throws ParseException {
        Arguments(new ArrayList<>());
    }

    /**
     * Parses a nested block of an action whose tokens are collected by the outer block.
     */
    protected void Statement() throws ParseException {
        Statement(new ArrayList<>());
    }

    protected abstract void Arguments(List<Token> tokens) throws ParseException;

    protected abstract void Statement(List<Token> tokens) throws ParseException;

    protected boolean checkEmptyLA(boolean emptyLA, Token token) {
        return !emptyLA && (token.kind != ParserConstants.RPAREN);
    }

    protected boolean checkEmptyLAAndCommaEnd(boolean emptyLA, boolean commaAtEnd, Token token) {
        return !emptyLA && !commaAtEnd && (token.kind != ParserConstants.RPAREN);
    }

    protected boolean checkEmptyLAOrCommaEnd(boolean emptyLA, boolean commaAtEnd) {
        return emptyLA || commaAtEnd;
    }

    protected boolean checkEmpty(Token token) {
        return (token.kind != ParserConstants.RPAREN) && (token.kind
                != ParserConstants.LBRACE);
    }

    protected final void setParserName(Token v) {
        getOptions().setOption(diagnostics(), null, v, Waggle.PARSER_NAME, v.image);
    }

    protected final void setInputOption(Token o, Token v) {
        switch (v.kind) {
            case ParserConstants.INTEGER_LITERAL:
                getOptions().setOption(diagnostics(), o, v, o.image, integerValue(v));
                break;

            case ParserConstants.TRUE:
            case ParserConstants.FALSE:
                getOptions().setOption(diagnostics(), o, v, o.image, Boolean.valueOf(v.image));
                break;

            default:
                getOptions().setOption(diagnostics(), o, v, o.image, remove_escapes_and_quotes(v, v.image));
                break;
        }
    }
}
