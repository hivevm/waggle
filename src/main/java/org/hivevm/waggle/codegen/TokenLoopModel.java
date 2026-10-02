// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import java.util.List;

/**
 * {@code getNextToken} as a target's templates write it (ADR-0031): the token loop, the lexical
 * states it dispatches on, and the branches a match takes. Every value is already spelled for the
 * target; the structure is what the lexer plan decided ({@code LexerPlan.TokenLoop}).
 *
 * <p>The records nest as the generated blocks do, so a template that opens a block applies the
 * record of what goes inside it, and the indentation follows the nesting.
 */
public final class TokenLoopModel {

    private TokenLoopModel() {
    }

    /**
     * The rest of the end-of-input branch, and the loop.
     *
     * @param eofActions whether the end of input runs the token actions
     * @param imageInit  whether the image buffer is reset first: there are lexical actions
     * @param moreLoop   whether a MORE goes round the loop again
     * @param body       what the loop runs
     */
    public record GetNextToken(boolean eofActions, boolean imageInit, boolean moreLoop,
                               LoopBody body) {
    }

    /**
     * One pass of the loop: match in the current lexical state, then dispatch.
     *
     * @param noLexState whether there is no lexical state, so nothing ever matches
     * @param states     the lexical states
     * @param dispatch   what happens with a match, or null when there is no lexical state
     */
    public record LoopBody(boolean noLexState, List<LexStateCase> states, MatchDispatch dispatch) {
    }

    /**
     * One lexical state, as a case of the switch on it when there is one.
     *
     * @param index the lexical state
     * @param body  what it runs
     */
    public record LexStateCase(int index, StateBody body) {
    }

    /**
     * What a lexical state runs to find the longest match. It is applied within its
     * {@link LexStateCase}, whose {@code index} it reads.
     *
     * @param hasSkip      whether characters that can only be skipped are skipped first
     * @param skip         that loop, or null
     * @param matchesEmpty whether the state starts out having matched the empty string
     * @param emptyMatch   the kind it matched the empty string as
     * @param anyChar      whether a catch-all kind takes what the string-literal DFA did not
     * @param anyCharKind  that kind
     */
    public record StateBody(boolean hasSkip, SkipLoop skip, boolean matchesEmpty, int emptyMatch,
                            boolean anyChar, int anyCharKind) {
    }

    /**
     * Skips the characters that can only ever be skipped: those in the lower half of the ASCII bit
     * vector, those in the upper half, or both.
     *
     * @param both      whether skipped characters lie in both halves
     * @param upperOnly whether they lie only in the upper half
     * @param lowerMask the bits of the lower half, as the target spells a 64-bit literal
     * @param upperMask the bits of the upper half, likewise
     * @param maxChar   the highest skipped character, when they lie in one half
     */
    public record SkipLoop(boolean both, boolean upperOnly, String lowerMask, String upperMask,
                           int maxChar) {
    }

    /**
     * What happens with a match.
     *
     * @param tokenTest   whether a kind needs a test to be a TOKEN: there are other kinds
     * @param moreBranch  whether there is a MORE branch, which the C++ loop leaves on an error
     * @param token       the TOKEN branch
     * @param skip        the SKIP or SPECIAL_TOKEN branch, or null
     * @param more        the MORE branch, or null
     */
    public record MatchDispatch(boolean tokenTest, boolean moreBranch, TokenBranch token,
                                SkipBranch skip, MoreBranch more) {
    }

    /**
     * The matched kind is a TOKEN: build it, run its actions and hand it to the parser.
     *
     * @param special      whether there are special tokens to attach to it
     * @param tokenActions whether the TOKEN productions have lexical actions
     * @param newLexState  whether a kind can switch the lexical state
     */
    public record TokenBranch(boolean special, boolean tokenActions, boolean newLexState) {
    }

    /**
     * The matched kind is a SKIP or a SPECIAL_TOKEN: keep it out of the parser's way.
     *
     * @param moreBranch  whether a MORE branch follows, so the kind needs a test
     * @param special     whether there are special tokens
     * @param skipActions whether the SKIP productions have lexical actions
     * @param newLexState whether a kind can switch the lexical state
     */
    public record SkipBranch(boolean moreBranch, boolean special, boolean skipActions,
                             boolean newLexState) {
    }

    /**
     * The matched kind is a MORE: keep the image and go round again.
     *
     * @param moreActions  whether the MORE productions have lexical actions
     * @param moreImageLen whether a MORE without actions of its own still records the image length
     * @param newLexState  whether a kind can switch the lexical state
     */
    public record MoreBranch(boolean moreActions, boolean moreImageLen, boolean newLexState) {
    }
}
