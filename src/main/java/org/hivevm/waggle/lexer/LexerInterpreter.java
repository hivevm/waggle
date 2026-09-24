// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java, org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.lexer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs a lexical specification over input directly, without generating a token manager.
 *
 * <p>JavaCC had this as an "interpreted token manager"
 * ({@code JavaCCInterpreter}, in this repository's own history at {@code 4ace1ec8}). It walked a
 * {@code TokenizerData} structure that the code generator filled as a side product, because the NFA
 * itself was not available as data. Here it is: stage 4 owns the automaton and hands it over
 * finished (ADR-0012), so the interpreter simulates the NFA that the generated token manager would
 * have been compiled from.
 *
 * <p>It is the same longest-match rule the generated lexer follows: from the start states of the
 * current lexical state, advance on each character, and remember the lowest token kind reached — the
 * grammar's declaration order is its precedence. A match resets to the position after it and, when
 * the token switches lexical state, to that state's start.
 */
public final class LexerInterpreter {

    /**
     * One thing the lexer recognised.
     *
     * @param kind      the token's ordinal, as the grammar numbered it
     * @param image     the matched text
     * @param begin     the offset of the first character
     * @param end       the offset after the last character
     * @param lexState  the lexical state the match was made in
     */
    public record Match(int kind, String image, int begin, int end, String lexState) {
    }

    /** Why the lexer stopped before the end of the input. */
    public static final class LexerError extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int offset;

        LexerError(String message, int offset) {
            super(message);
            this.offset = offset;
        }

        /** Where in the input no token could be matched. */
        public int offset() {
            return this.offset;
        }
    }

    private final LexerData data;

    public LexerInterpreter(LexerData data) {
        this.data = data;
    }

    /**
     * The tokens of {@code input}, in order.
     *
     * <p>SKIP and MORE tokens do not appear: a SKIP is dropped and a MORE is folded into the token
     * that follows it, which is what the generated token manager does with them.
     *
     * @throws LexerError when no token matches at some position
     */
    public List<Match> tokenize(String input) {
        var matches = new ArrayList<Match>();
        var state = this.data.defaultLexState();
        var pos = 0;
        var moreFrom = -1;

        while (pos < input.length()) {
            var match = match(input, pos, state);
            if (match == null) {
                throw new LexerError("No token matches the input at offset " + pos
                        + " (lexical state " + this.data.getStateName(state) + ", character '"
                        + input.charAt(pos) + "')", pos);
            }

            var begin = (moreFrom < 0) ? match.begin() : moreFrom;
            moreFrom = -1;

            if (isMore(match.kind())) {
                moreFrom = begin;
            } else if (!isSkipped(match.kind())) {
                matches.add(new Match(match.kind(), input.substring(begin, match.end()), begin,
                        match.end(), this.data.getStateName(state)));
            }

            var next = this.data.newLexState(match.kind());
            if (next != null) {
                state = this.data.getStateIndex(next);
            }
            pos = match.end();
        }
        return matches;
    }

    /** The longest match at {@code pos}, or null when nothing matches there. */
    private Match match(String input, int pos, int state) {
        var stateData = this.data.getStateData(this.data.getStateName(state));
        var current = closure(Set.of(stateData.getInitialState()));

        var matchedKind = Integer.MAX_VALUE;
        var matchedEnd = -1;

        for (var at = pos; (at < input.length()) && !current.isEmpty(); at++) {
            var c = character(input.charAt(at));
            var next = new LinkedHashSet<NfaState>();
            for (var s : current) {
                if (LexerInterpreter.matches(s, c) && (s.next != null)) {
                    next.addAll(closure(Set.of(s.next)));
                }
            }
            current = next;

            // Longest match: a later accepting position always wins. Among the states accepting at
            // the same position, the lowest kind does -- that is the grammar's declaration order.
            var here = Integer.MAX_VALUE;
            for (var s : current) {
                here = Math.min(here, s.kind);
            }
            if (here != Integer.MAX_VALUE) {
                matchedKind = here;
                matchedEnd = at + 1;
            }
        }

        // A catch-all token (~[]) is not part of the NFA: the generated lexer applies it after the
        // automaton, as a one-character match that wins over nothing or over a later declaration.
        var anyChar = this.data.canMatchAnyChar(state);
        if ((anyChar != -1) && ((matchedEnd < 0) || ((matchedEnd == (pos + 1)) && (matchedKind > anyChar)))) {
            matchedKind = anyChar;
            matchedEnd = pos + 1;
        }

        return (matchedEnd < 0) ? null
                : new Match(matchedKind, input.substring(pos, matchedEnd), pos, matchedEnd, null);
    }

    private char character(char c) {
        return this.data.ignoreCase() ? Character.toLowerCase(c) : c;
    }

    /** The states reachable from {@code states} without consuming a character. */
    private static Set<NfaState> closure(Set<NfaState> states) {
        var seen = new LinkedHashSet<NfaState>();
        var pending = new ArrayList<>(states);
        while (!pending.isEmpty()) {
            var s = pending.removeLast();
            if (seen.add(s)) {
                pending.addAll(s.epsilonMoves);
            }
        }
        return Collections.unmodifiableSet(seen);
    }

    /** Whether {@code state} has a move on {@code c}. */
    private static boolean matches(NfaState state, char c) {
        if (c < 128) {
            return (state.asciiMoves[c / 64] & (1L << (c % 64))) != 0L;
        }
        if (state.charMoves != null) {
            for (var move : state.charMoves) {
                if (move == 0) {
                    break;
                }
                if (move == c) {
                    return true;
                }
            }
        }
        if (state.rangeMoves != null) {
            for (var i = 0; i < (state.rangeMoves.length - 1); i += 2) {
                if (state.rangeMoves[i] == 0) {
                    break;
                }
                if ((c >= state.rangeMoves[i]) && (c <= state.rangeMoves[i + 1])) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSkipped(int kind) {
        return ((this.data.toSkip(kind / 64) | this.data.toSpecial(kind / 64))
                & (1L << (kind % 64))) != 0L;
    }

    private boolean isMore(int kind) {
        return (this.data.toMore(kind / 64) & (1L << (kind % 64))) != 0L;
    }
}
