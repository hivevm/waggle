// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.List;

import org.hivevm.cc.parser.Token;

/**
 * Describes non terminals. The LHS to which the return value of the non-terminal is assigned. In
 * case there is no LHS, then the vector remains empty.
 */

public class NonTerminal extends Expansion {

    private String           name;
    private NormalProduction prod;

    private       List<Token> lhsTokens;
    private final List<Token> argument_tokens;

    public NonTerminal() {
        this.lhsTokens = new ArrayList<>();
        this.argument_tokens = new ArrayList<>();
    }

    public void setLhsTokens(List<Token> lhsTokens) {
        this.lhsTokens = lhsTokens;
    }

    public List<Token> getLhsTokens() {
        return this.lhsTokens;
    }

    public String getName() {
        return this.name;
    }

    public List<Token> getArgumentTokens() {
        return this.argument_tokens;
    }

    public NormalProduction getProd() {
        return this.prod;
    }

    public void setName(String name) {
        this.name = name;
    }

    public NormalProduction setProd(NormalProduction prod) {
        return this.prod = prod;
    }
}