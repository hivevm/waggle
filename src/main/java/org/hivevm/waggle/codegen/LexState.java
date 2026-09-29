// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.lexer.LexerPlan;
import org.hivevm.waggle.lexer.LexerPlan.Handoff;
import org.hivevm.waggle.lexer.LexerPlan.LexStatePlan;

/**
 * The lexical state an emitter is writing: its plan, the plan around it and the name of the
 * parser, which the C++ back end qualifies its functions with (ADR-0029).
 *
 * @param parserName the name of the parser
 * @param lexer      the plan of the whole token manager
 * @param plan       the plan of this lexical state
 */
public record LexState(String parserName, LexerPlan lexer, LexStatePlan plan) {

    /** The suffix that tells the functions of this lexical state apart. */
    public String suffix() {
        return "_" + this.plan.index();
    }

    /** Whether the token manager traces what it does. */
    public boolean debug() {
        return this.lexer.debug();
    }

    /** Whether a trace names the lexical state: there is more than one. */
    public boolean withLexState() {
        return this.lexer.tokenLoop().switchOnLexState();
    }

    /** Whether the NFA of this state runs after the string-literal DFA in a mixed state. */
    public boolean mixed() {
        return this.plan.handoff() == Handoff.MOVE_NFA;
    }

    /** How many states the NFA has. */
    public int generatedStates() {
        return this.plan.moves().generatedStates();
    }
}
