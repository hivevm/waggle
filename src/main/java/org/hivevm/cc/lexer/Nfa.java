// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.lexer;

import org.hivevm.cc.generator.NfaStateData;

/**
 * A Non-deterministic Finite Automaton.
 */
public class Nfa {

  public final NfaState start;
  public final NfaState end;

  Nfa(NfaState startGiven, NfaState finalGiven) {
    this.start = startGiven;
    this.end = finalGiven;
  }

  Nfa(NfaStateData data) {
    this(new NfaState(data), new NfaState(data));
  }
}
