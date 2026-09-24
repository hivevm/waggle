// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NormalProduction.java

package org.hivevm.waggle.model;

import org.hivevm.waggle.grammar.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes JavaCC productions.
 */
public abstract sealed class NormalProduction extends Expansion permits BNFProduction {

    // The NonTerminal nodes which refer to this production.
    /** Every NonTerminal that references this production. */
    private final List<NonTerminal> parents = new ArrayList<>();

    // The name of the non-terminal of this production.
    private String lhs;

    // The return type of this production, as one token (see AbstractGrammarParser.typeToken), or
    // null when it returns nothing.
    private Token returnType;

    // The tokens that make up the parameters of this production.
    private final List<Token> parameter_list_tokens = new ArrayList<>();

    // The RHS of this production.
    private Expansion expansion;

    // This boolean flag is true if this production can expand to empty.
    private boolean emptyPossible = false;

    // The first token from the input stream that represents this production.
    private Token firstToken;

    protected NormalProduction() {
    }

    /**
     * @return the parents
     */
    public List<NonTerminal> getParents() {
        return this.parents;
    }

    /**
     * @param lhs the lhs to set
     */
    public void setLhs(String lhs) {
        this.lhs = lhs;
    }

    /**
     * @return the lhs
     */
    public String getLhs() {
        return this.lhs;
    }

    /** The return type, or null when the production returns nothing. */
    public Token getReturnTypeToken() {
        return this.returnType;
    }

    public void setReturnTypeToken(Token token) {
        this.returnType = token;
    }

    /**
     * @return the parameter_list_tokens
     */
    public List<Token> getParameterListTokens() {
        return this.parameter_list_tokens;
    }


    /**
     * @param expansion the expansion to set
     */
    public void setExpansion(Expansion expansion) {
        this.expansion = expansion;
    }

    /**
     * @return the expansion
     */
    public Expansion getExpansion() {
        return this.expansion;
    }

    /**
     * @param emptyPossible the emptyPossible to set
     */
    public boolean setEmptyPossible(boolean emptyPossible) {
        this.emptyPossible = emptyPossible;
        return emptyPossible;
    }

    /**
     * @return the emptyPossible
     */
    public boolean isEmptyPossible() {
        return this.emptyPossible;
    }

    /**
     * @param firstToken the firstToken to set
     */
    public Token setFirstToken(Token firstToken) {
        this.firstToken = firstToken;
        return firstToken;
    }

    /**
     * @return the firstToken
     */
    public Token getFirstToken() {
        return this.firstToken;
    }
}
