// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.lexer;

import org.hivevm.cc.generator.NfaStateData;

/**
 * A Non-deterministic Finite Automaton.
 */
public record Nfa(NfaState start, NfaState end) {

    Nfa(NfaStateData data) {
        this(new NfaState(data), new NfaState(data));
    }
}
