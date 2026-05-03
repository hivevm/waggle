// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import org.hivevm.cc.HiveCCOptions;

/**
 * The {@link ASTParser} class.
 */
abstract class ASTParser {

    private final Map<String, ASTProduction> productions;

    protected ASTParser() {
        this.productions = new HashMap<>();
    }

    protected final void addProduction(ASTProduction prod) {
        this.productions.put(prod.name(), prod);
    }

    public final ASTProduction getProduction(String name) {
        return this.productions.get(name);
    }

    protected final void setInputOption(Token o, Token v) {
        String image = v.image;
        switch (v.kind) {
            case ParserConstants.INTEGER_LITERAL:
                getOptions().setOption(o, v, o.image, Integer.valueOf(image));
                break;

            case ParserConstants.TRUE:
            case ParserConstants.FALSE:
                getOptions().setOption(o, v, o.image, Boolean.valueOf(image));
                break;

            default:
                image = TokenUtils.remove_escapes_and_quotes(v, image);
                getOptions().setOption(o, v, o.image, image);
                break;
        }
    }

    protected abstract Token getToken(int index);

    protected void jjtreeOpenNodeScope(Node n) throws ParseException {
    }

    protected void jjtreeCloseNodeScope(Node n) throws ParseException {
    }

    protected HiveCCOptions getOptions() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if the next token is not in the FOLLOW list of "expansion". It is used to decide
     * when the end of an "expansion" has been reached.
     */
    protected boolean notTailOfExpansionUnit() {
        Token t = getToken(1);
        return (t.kind != ParserConstants.BIT_OR) && (t.kind != ParserConstants.COMMA)
                && (t.kind != ParserConstants.RPAREN) && (t.kind != ParserConstants.RBRACE)
                && (t.kind != ParserConstants.RBRACKET);
    }

    protected boolean checkEmptyLA(boolean emptyLA, Token token) {
        return !emptyLA && (token.kind != ParserConstants.RPAREN);
    }

    protected boolean checkEmptyLAAndCommandEnd(boolean emptyLA, boolean commaAtEnd, Token token) {
        return !emptyLA && !commaAtEnd && (getToken(1).kind != ParserConstants.RPAREN);
    }

    protected boolean checkEmptyLAOrCommandEnd(boolean emptyLA, boolean commaAtEnd) {
        return emptyLA || commaAtEnd;
    }

    protected boolean checkEmpty(Token token) {
        return (token.kind != ParserConstants.RPAREN) && (token.kind != ParserConstants.LBRACE);
    }
}
