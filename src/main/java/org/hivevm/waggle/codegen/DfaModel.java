// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import java.util.List;

/**
 * The string-literal DFA of a lexical state as a target's templates write it (ADR-0031): the
 * functions per position, the trivial first position of a state without literals, and the stop
 * DFA that hands over to the NFA. Every expression is already spelled for the target; which
 * statement is written is what the lexer plan decided.
 *
 * <p>A template is named after the type of the record it renders, under
 * {@code templates/<target>/lexer/apply/}.
 */
public final class DfaModel {

    private DfaModel() {
    }

    /**
     * {@code jjStopStringLiteralDfa}, which reports how far the DFA got, and {@code jjStartNfa},
     * which hands that over to the NFA.
     *
     * @param suffix       what tells this state's functions from the others'
     * @param activeParams the active vectors as parameters
     * @param arguments    the active vectors as arguments
     * @param noState      the state that says there is none
     * @param positions    one case per position
     */
    public record StopDfa(String suffix, String activeParams, String arguments, String noState,
                          List<StopPos> positions) {
    }

    /**
     * The tests of one position.
     *
     * @param pos     the position
     * @param noState the state that says there is none
     * @param cases   the tests, in order
     */
    public record StopPos(int pos, String noState, List<StopCase> cases) {
    }

    /**
     * One test: if one of the tested literals is still active, record what was matched and resume
     * the NFA.
     *
     * @param condition         the test of the active vectors
     * @param hasKind           whether a kind is recorded at all
     * @param first             the kind, at the first position
     * @param firstAfterEmpty   the kind and the first position, in a state that matches the empty
     *                          string
     * @param here              the kind and this position
     * @param hereUnlessMatched the kind and this position, unless this position matched already
     * @param earlier           the kind and an earlier position, unless one before it matched
     * @param earlierAtFirst    the kind and an earlier position, if the first one matched
     * @param kind              the kind
     * @param pos               this position
     * @param matchedPos        the earlier position
     * @param resume            the state the NFA resumes in
     */
    public record StopCase(String condition, boolean hasKind, boolean first,
                           boolean firstAfterEmpty, boolean here, boolean hereUnlessMatched,
                           boolean earlier, boolean earlierAtFirst, int kind, int pos,
                           int matchedPos, String resume) {
    }

    /**
     * The string-literal DFA proper of a lexical state.
     *
     * @param suffix          what tells this state's functions from the others'
     * @param trivial         whether the state has no string literal, so the first position only
     *                        hands over or returns
     * @param trivialReturn   what that first position returns
     * @param stopAtPos       whether this state writes the jjStopAtPos all states share
     * @param positions       one function per position
     * @param startWithStates jjStartNfaWithStates, or null when the state needs none
     */
    public record DfaCode(String suffix, boolean trivial, String trivialReturn, boolean stopAtPos,
                          List<DfaFn> positions, StartWithStates startWithStates) {
    }

    /**
     * {@code jjMoveStringLiteralDfa<pos>}.
     *
     * @param pos             the position
     * @param suffix          what tells this state's functions from the others'
     * @param params          its parameters, as the target lists them
     * @param activeCheck     whether it first masks the active vectors with the previous ones
     * @param masked          that masking, as one test, where the target writes it so
     * @param lets            that masking, one statement per vector, where the target needs it so
     * @param activeTest      the test of the masked vectors, where the target writes it apart
     * @param checkReturn     what it returns when no literal survives the masking
     * @param debugMatches    the trace of the literals that can still match
     * @param readChar        whether it reads a character first: every position but the first
     * @param eofStartNfa     whether the end of input hands the matches so far to the NFA; the
     *                        tail hands the active vectors over in the same case
     * @param stopCall        that hand-over
     * @param eofReturn       what it returns at the end of input otherwise
     * @param cases           the characters that continue a literal here
     * @param otherwise       what every other character does
     * @param tail            whether it ends by handing over after the switch
     * @param tailStartCall   the tail's hand-over of the active vectors
     * @param tailMoveNfa     whether it moves the NFA from its start state
     * @param tailMoveCall    that move
     * @param tailReturn      the position it returns otherwise
     */
    public record DfaFn(int pos, String suffix, String params, boolean activeCheck,
                        String masked, List<ActiveLet> lets, String activeTest,
                        String checkReturn, PossibleMatches debugMatches, boolean readChar,
                        boolean eofStartNfa, String stopCall, String eofReturn,
                        List<DfaCase> cases, DfaExit otherwise, boolean tail,
                        String tailStartCall, boolean tailMoveNfa, String tailMoveCall,
                        int tailReturn) {
    }

    /**
     * Masks one active vector with the previous one.
     *
     * @param word the vector
     */
    public record ActiveLet(int word) {
    }

    /**
     * One character that continues a literal.
     *
     * @param labels the other cases that share its block
     * @param c      the character
     * @param finals the literals that end here, tested in order
     * @param exit   how the case ends
     */
    public record DfaCase(List<DfaLabel> labels, int c, List<DfaFinal> finals, DfaExit exit) {
    }

    /**
     * A case label that shares the block of the next one.
     *
     * @param c the character
     */
    public record DfaLabel(int c) {
    }

    /**
     * A literal that ends here.
     *
     * @param guarded          whether it is guarded by its bit in the active vectors
     * @param elseIf           whether that guard continues the chain of the previous one
     * @param guard            the test of that bit
     * @param startNfa         whether it is matched and the NFA may still find a longer match
     * @param startNfaCall     the hand-over to the NFA
     * @param stopAtPos        whether it is matched and nothing longer can be
     * @param stopAtPosCall    the stop
     * @param kindAndPos       whether a longer literal may still match: kind and position
     * @param kindOnly         whether a longer literal may still match: the kind
     * @param kind             the kind
     * @param pos              the position
     */
    public record DfaFinal(boolean guarded, boolean elseIf, String guard, boolean startNfa,
                           String startNfaCall, boolean stopAtPos, String stopAtPosCall,
                           boolean kindAndPos, boolean kindOnly, int kind, int pos) {
    }

    /**
     * How a case of a position ends.
     *
     * @param callNext   the call of the next position, or null when it does not go on with it
     * @param moveNfa    whether it moves the NFA from its start state
     * @param moveCall   that move
     * @param leave      whether it leaves the switch
     * @param ret        whether it returns the next position
     * @param returnPos  that position
     */
    public record DfaExit(String callNext, boolean moveNfa, String moveCall,
                          boolean leave, boolean ret, int returnPos) {
    }

    /**
     * {@code jjStartNfaWithStates}: a literal matched, but the NFA may still find a longer match.
     *
     * @param suffix what tells this state's functions from the others'
     */
    public record StartWithStates(String suffix) {
    }

    /**
     * The DEBUG_TOKEN_MANAGER trace of the literals that can still match at a position.
     *
     * @param formats one per vector with a literal left, where the target builds a format string
     * @param vectors the same vectors, as the trace names their kinds
     */
    public record PossibleMatches(List<KindsFormat> formats, List<KindsVector> vectors) {
    }

    /** The place one vector takes in that format; {@code first} tells it from the others. */
    public record KindsFormat(boolean first) {
    }

    /** The kinds of one vector with a literal left; {@code first} tells it from the others. */
    public record KindsVector(boolean first, int index) {
    }
}
