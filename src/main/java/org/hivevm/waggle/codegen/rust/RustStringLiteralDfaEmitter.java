// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.codegen.StringLiteralDfaEmitter;
import org.hivevm.waggle.codegen.TargetSyntax;
import org.hivevm.waggle.codegen.LexState;
import org.hivevm.waggle.lexer.LexerPlan.DfaPos;



/**
 * Rust's string-literal DFA: the shared one, with Rust's signatures, its "let" per active vector,
 * block guards and snake-case calls.
 */
class RustStringLiteralDfaEmitter extends StringLiteralDfaEmitter {

    RustStringLiteralDfaEmitter(TargetSyntax syntax) {
        super(syntax);
    }

    /** Rust always leads with "&mut self", so every parameter brings its own comma. */
    @Override
    protected String signatureParams(DfaPos pos) {
        var params = new StringBuilder();
        for (int j : pos.params()) {
            params.append((pos.pos() == 1) ? ", active" + j + ": u64"
                    : ", old" + j + ": u64, active_old" + j + ": u64");
        }
        return params.toString();
    }

    @Override
    protected String startNfaName(LexState lex) {
        return "self." + super.startNfaName(lex);
    }

    @Override
    protected String startNfaWithStatesName(LexState lex) {
        return "self." + super.startNfaWithStatesName(lex);
    }

    @Override
    protected String stopAtPosName() {
        return "self.jj_stop_at_pos";
    }

    @Override
    protected String moveStringLiteralDfaName(LexState lex, int i) {
        return "self.jj_move_string_literal_dfa" + i + lex.suffix();
    }

}
