// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.List;

import org.hivevm.cc.parser.Token;

/**
 * Describes actions that may occur on the right hand side of productions.
 */
public class Action extends Expansion {

    private final List<Token> action_tokens = new ArrayList<>();

    public Action() {
    }

    public List<Token> getActionTokens() {
        return this.action_tokens;
    }
}
