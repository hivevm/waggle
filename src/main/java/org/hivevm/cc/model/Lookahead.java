// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.List;

import org.hivevm.cc.parser.Token;

/**
 * Describes lookahead rule for a particular expansion or expansion sequence (See Sequence.java). In
 * case this describes the lookahead rule for a single expansion unit, then a sequence is created
 * with this node as the first element, and the expansion unit as the second and last element.
 */
public class Lookahead extends Expansion {

    private final List<Token> action_tokens = new ArrayList<>();

    /**
     * The lookahead amount. Its default value essentially gives us infinite lookahead.
     */
    private int amount = Integer.MAX_VALUE;

    /**
     * The expansion used to determine whether or not to choose the corresponding parse option. This
     * expansion is parsed upto "amount" tokens of lookahead or until a complete match for it is
     * found. Usually, this is the same as the expansion to be parsed.
     */
    private Expansion la_expansion;

    /**
     * Is set to true if this is an explicit lookahead specification
     */
    private boolean isExplicit;

    public Lookahead() {
    }

    public List<Token> getActionTokens() {
        return this.action_tokens;
    }

    public int getAmount() {
        return this.amount;
    }

    public boolean isExplicit() {
        return this.isExplicit;
    }

    public Expansion getLaExpansion() {
        return this.la_expansion;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void setExplicit(boolean isExplicit) {
        this.isExplicit = isExplicit;
    }

    public void setLaExpansion(Expansion la_expansion) {
        this.la_expansion = la_expansion;
    }
}
