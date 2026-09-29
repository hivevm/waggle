// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/LexGen.java, org/javacc/parser/LexGenCPP.java

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.codegen.GetNextTokenEmitter;
import org.hivevm.waggle.codegen.TargetSyntax;
import org.hivevm.waggle.lexer.LexerPlan.SkipSingles;

/**
 * How Rust spells {@code getNextToken} (ADR-0017).
 */
class RustGetNextTokenEmitter extends GetNextTokenEmitter {

    RustGetNextTokenEmitter(TargetSyntax syntax) {
        super(syntax);
    }

    @Override
    protected String skipCondition(SkipSingles skip) {
        String lower = "0x" + Long.toHexString(skip.lower()) + "u64";
        String upper = "0x" + Long.toHexString(skip.upper()) + "u64";
        String condition = switch (skip.range()) {
            case BOTH -> "(self.cur_char < 64 && (" + lower
                    + " & (1u64 << self.cur_char)) != 0) || ((self.cur_char >> 6) == 1 && ("
                    + upper + " & (1u64 << (self.cur_char & 0o77))) != 0)";
            case LOWER -> "self.cur_char <= " + skip.maxChar() + " && (" + lower
                    + " & (1u64 << self.cur_char)) != 0";
            case UPPER -> "self.cur_char > 63 && self.cur_char <= " + skip.maxChar() + " && ("
                    + upper + " & (1u64 << (self.cur_char & 0o77))) != 0";
        };
        return condition;
    }

}
