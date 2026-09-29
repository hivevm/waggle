// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import java.util.List;


/**
 * One lexical state's generated code, as its target writes it (ADR-0031).
 *
 * <p>What the loop of {@code jjMoveNfa} does, and in which order, is the same everywhere; the
 * wording is not. The order is the templates', and so are the statements short enough to read
 * there. What stays a string is the text a target escapes -- the traces, and the prologue of a
 * state the string-literal DFA hands over to -- because the same wording serves the token loop and
 * would otherwise be written twice.
 */
public final class NfaModel {

    private NfaModel() {
    }

    /**
     * One lexical state.
     *
     * @param stop       its stop DFA, or null when it has no string literal to stop on
     * @param dfa        its string-literal DFA
     * @param nfa        its NFA loop, or null when it has none
     */
    public record LexicalState(DfaModel.StopDfa stop, DfaModel.DfaCode dfa, MoveNfa nfa) {
    }

    /**
     * The NFA loop. What it writes besides the arms is template text (ADR-0031).
     *
     * @param parserName      the parser whose token manager holds it
     * @param suffix          what tells this state's functions from the others'
     * @param generatedStates how many states the NFA has
     * @param mixed           whether it resumes after a string literal already matched: it saves
     *                        that match, rewinds and starts over, and decides at the end which of
     *                        the two matches wins
     * @param debug           whether it traces what it does
     * @param withLexState    whether the character trace names the lexical state
     * @param low             the arms for characters below 64
     * @param high            the arms for characters from 64 to 127
     * @param other           the arms for characters beyond ASCII
     */
    public record MoveNfa(String parserName, String suffix, int generatedStates, boolean mixed,
                          boolean debug, boolean withLexState, NfaSection low, NfaSection high,
                          NfaSection other) {
    }

    /**
     * The arms of the NFA switch for one group of characters: below 64, below 128 or beyond.
     *
     * @param low            whether it is the group below 64
     * @param high           whether it is the group from 64 to 127
     * @param other          whether it is the group beyond ASCII
     * @param breakInDefault whether the default arm leaves the loop over the state set
     * @param arms           the arms, in output order
     */
    public record NfaSection(boolean low, boolean high, boolean other, boolean breakInDefault,
                             List<NfaArm> arms) {
    }

    /** One arm of the NFA switch; its type says how it is laid out. */
    public sealed interface NfaArm permits AsciiComposite, AsciiArm, WideComposite, WideOne,
            WideSingle {
    }

    /**
     * A composite state below 128 of which several members move, as an if-else chain.
     *
     * @param first the label the arm is written under
     * @param joined all its labels, joined for a target that writes one arm for them
     * @param moves the members' moves
     */
    public record AsciiComposite(int first, String joined, List<CompositeMove> moves)
            implements NfaArm {
    }

    /**
     * A state below 128, or a composite state of which one member moves.
     *
     * @param labels     its labels
     * @param hasLabels  whether it has any
     * @param openIndent the indentation of the arm a target joins the labels into
     * @param joined     the labels, joined for a target that writes one arm for them
     * @param move       its move
     */
    public record AsciiArm(List<NfaLabel> labels, boolean hasLabels, String openIndent,
                           String joined, AsciiMove move) implements NfaArm {
    }

    /**
     * A composite state beyond ASCII of which several members move.
     *
     * @param first     the label the arm is written under
     * @param hasLabels whether it has labels
     * @param joined    all its labels, joined for a target that writes one arm for them
     * @param moves     the members' moves
     */
    public record WideComposite(int first, boolean hasLabels, String joined,
                                List<WideCompositeMove> moves) implements NfaArm {
    }

    /**
     * A composite state beyond ASCII of which one member moves.
     *
     * @param labels    its labels
     * @param hasLabels whether it has any
     * @param joined    the labels, joined for a target that writes one arm for them
     * @param move      its move
     */
    public record WideOne(List<NfaLabel> labels, boolean hasLabels, String joined, WideMove move)
            implements NfaArm {
    }

    /**
     * A state beyond ASCII; the labels of the states merged into it come out one level deeper.
     *
     * @param first     its own label
     * @param rest      the labels of the states merged into it
     * @param hasLabels whether it has labels
     * @param joined    all its labels, joined for a target that writes one arm for them
     * @param move      its move
     */
    public record WideSingle(int first, List<NfaLabel> rest, boolean hasLabels, String joined,
                             WideMove move) implements NfaArm {
    }

    /**
     * A case label of an arm.
     *
     * @param indent its indentation
     * @param c      the state
     */
    public record NfaLabel(String indent, int c) {
    }

    /**
     * The single move of an arm below 128.
     *
     * @param accept          whether it only accepts a kind under its guard
     * @param match           whether it leaves the arm unless its guard holds, accepts and moves
     * @param advance         whether it moves into its states under its guard
     * @param guarded         whether it is guarded at all
     * @param condition       the guard
     * @param acceptCondition the guard of an accepting move, with the yield to a lower kind
     * @param negated         the guard the other way round
     * @param raise           whether the kind is taken only when it beats the round's
     * @param kind            the kind
     * @param next            the states it moves into, or null
     */
    public record AsciiMove(boolean accept, boolean match, boolean advance, boolean guarded,
                            String condition, String acceptCondition, String negated,
                            boolean raise, int kind, StateAdd next) {
    }

    /**
     * One member's move in a composite state below 128.
     *
     * @param guarded   whether it is guarded
     * @param elseIf    whether its guard continues the chain of the previous one
     * @param condition the guard
     * @param hasAccept whether it accepts a kind
     * @param raise     whether the kind is taken only when it beats the round's
     * @param kind      the kind
     * @param next      the states it moves into, or null
     */
    public record CompositeMove(boolean guarded, boolean elseIf, String condition,
                                boolean hasAccept, boolean raise, int kind, StateAdd next) {
    }

    /**
     * One member's move in a composite state beyond ASCII.
     *
     * @param condition the guard
     * @param hasAccept whether it accepts a kind
     * @param raise     whether the kind is taken only when it beats the round's
     * @param kind      the kind
     * @param next      the states it moves into, or null
     */
    public record WideCompositeMove(String condition, boolean hasAccept, boolean raise, int kind,
                                    StateAdd next) {
    }

    /**
     * The single move of an arm beyond ASCII; see {@link AsciiMove} for the components.
     */
    public record WideMove(boolean accept, boolean match, boolean advance, String condition,
                           String acceptCondition, String negated, int kind, StateAdd next) {
    }

    /**
     * How a move adds the states it leads to.
     *
     * @param add            one state, without checking
     * @param checkAdd       one state, checking it is not there yet
     * @param checkAddTwo    two states, checking each
     * @param addStates      a range of jjnextStates, without checking
     * @param checkAddStates a range of jjnextStates, checking each
     * @param first          the state, or the start of the range
     * @param second         the second state, or the end of the range
     * @param isRange        whether a checked range has an end of its own
     */
    public record StateAdd(boolean add, boolean checkAdd, boolean checkAddTwo, boolean addStates,
                           boolean checkAddStates, int first, int second, boolean isRange) {
    }
}
