// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.model.CodeText;

import java.util.List;

/**
 * The token manager as the lexer stage decided it: every choice taken and every number assigned,
 * so that a back end only writes it out (ADR-0029). It holds numbers, enums, records and model
 * actions, never a name of a target.
 *
 * <p>It is built once, at the end of {@link LexerBuilder#build}, after everything it records is
 * final, and reached through {@link LexerData#plan()}.
 *
 * @param debug     whether the token manager traces what it does
 * @param kindWords how many 64-bit words a set of token kinds takes
 * @param states    the lexical states, by index
 * @param tokenLoop what {@code getNextToken} does around the lexical states
 * @param actions   the lexical actions
 * @param tables    the tables the token manager reads at run time
 * @param canMoves  the {@code jjCanMove} functions, by number
 * @param tokenNames what a token is called when the parser reports it: the end of input first,
 *                   then every regular expression of the token productions, in their order
 */
public record LexerPlan(boolean debug, int kindWords, List<LexStatePlan> states,
                        TokenLoop tokenLoop, Actions actions, Tables tables,
                        List<CanMove> canMoves, List<TokenName> tokenNames,
                        List<NamedToken> namedTokens, Shape shape) {

    /**
     * What the generated lexer's scaffolding is sized and switched by: the numbers the templates
     * fill in, and the literal images the parser reports a token by. The back ends used to read
     * these off the construction data while they rendered.
     *
     * @param lexStates       how many lexical states the grammar has
     * @param defaultLexState the state the lexer starts in
     * @param stateNames      the names of the lexical states, by index
     * @param stateSetSize    how many NFA states the largest state set holds
     * @param dualNeed        whether {@code jjCheckNAddStates} is called with two indices
     * @param unaryNeed       whether it is called with one
     * @param hasLoop         whether a lexical state can match the empty string over and over
     * @param hasSpecial      whether the grammar has a SPECIAL_TOKEN
     * @param hasEmptyMatch   whether a lexical state matches the empty string
     * @param tokenActions    whether any TOKEN has a lexical action
     * @param skipActions     whether any SKIP or SPECIAL_TOKEN has one
     * @param moreActions     whether any MORE has one
     * @param images          the literal image of each kind, null where a kind has none
     */
    public record Shape(int lexStates, int defaultLexState, List<String> stateNames,
                        int stateSetSize, boolean dualNeed, boolean unaryNeed, boolean hasLoop,
                        boolean hasSpecial, boolean hasEmptyMatch, boolean tokenActions,
                        boolean skipActions, boolean moreActions, List<String> images) {
    }

    /**
     * A token the grammar gave a name, as the constants file declares it.
     *
     * @param ordinal its kind
     * @param label   its name in the grammar
     */
    public record NamedToken(int ordinal, String label) {
    }

    /**
     * One lexical state.
     *
     * @param index      the index of the lexical state
     * @param words      how many 64-bit words the active vectors of its string literals take
     * @param emptyMatch the kind the state matches the empty string as, or -1
     * @param initState  the state the NFA starts in, or -1 when it has no NFA
     * @param positions  the string-literal DFA, one entry per position
     * @param skip       the characters that are only ever skipped, or null when there are none
     * @param anyChar    the kind of the catch-all token, or -1 when there is none
     * @param stopDfa    where the NFA takes over from the string-literal DFA, or null when it
     *                   never does: there is no NFA, no string literal, or the state is mixed
     * @param handoff    how the string-literal DFA hands over to the NFA
     * @param startNfaWithStates whether {@code jjStartNfaWithStates} is written
     * @param stopAtPos  whether {@code jjStopAtPos}, which all lexical states share, is written
     *                   with this one: the first that has a string literal
     * @param moves      the NFA, or null when the state has none
     */
    public record LexStatePlan(int index, int words, int emptyMatch, int initState,
                               List<DfaPos> positions, SkipSingles skip, int anyChar,
                               List<StopPos> stopDfa, Handoff handoff,
                               boolean startNfaWithStates, boolean stopAtPos, NfaMoves moves) {

        /** Whether the state starts out having matched the empty string. */
        public boolean matchesEmpty() {
            return this.emptyMatch >= 0;
        }
    }

    /** How the string-literal DFA of a lexical state hands over to the NFA. */
    public enum Handoff {
        /** There is no NFA: the DFA returns the position it got to. */
        NONE,
        /** A mixed state: the DFA moves the NFA from its start state. */
        MOVE_NFA,
        /** The DFA starts the NFA in the states the literals matched so far lead to. */
        START_NFA
    }

    /**
     * One position of the string-literal DFA: {@code jjMoveStringLiteralDfa<pos>}.
     *
     * @param pos       the position
     * @param params    the active vectors it takes: those that still hold a literal long enough
     *                  to reach it. The first position takes none.
     * @param active    per word of the active vectors, whether it still holds a literal that
     *                  reaches this position
     * @param old       per word, whether it still holds one that reaches the position before
     * @param cases     the characters that continue a literal here, in character order
     * @param otherwise what every other character does
     * @param tail      whether the position ends by handing over to the NFA: a case left the
     *                  switch
     */
    public record DfaPos(int pos, List<Integer> params, List<Boolean> active, List<Boolean> old,
                         List<CharCase> cases, Exit otherwise, boolean tail) {
    }

    /**
     * One character of a position of the string-literal DFA.
     *
     * @param c      the character
     * @param labels the characters that share the case before it: its other case, when case is
     *               ignored
     * @param finals the literals that end here, by ascending kind
     * @param next   what the case does then
     * @param masks  for {@link Exit#CALL}, the vectors of the next position with the literals
     *               that continue through this character
     */
    public record CharCase(int c, List<Integer> labels, List<Final> finals, Exit next,
                           List<ActiveMask> masks) {
    }

    /**
     * A literal that ends at a character of the string-literal DFA.
     *
     * @param word     the word of the active vectors it is in
     * @param bit      its bit in that word
     * @param kind     the kind the DFA reports, which a shorter or a catch-all token may shadow
     * @param action   what the DFA does with it
     * @param stateSet for {@link FinalAction#START_NFA_WITH_STATES}, the state the NFA goes on in
     */
    public record Final(int word, long bit, int kind, FinalAction action, int stateSet) {
    }

    /** What the string-literal DFA does with a literal that ends. */
    public enum FinalAction {
        /** It is matched, and the NFA may still find a longer match. */
        START_NFA_WITH_STATES,
        /** It is matched, and nothing longer can be. */
        STOP_AT_POS,
        /** A longer literal may still match: record kind and position. */
        KIND_AND_POS,
        /** A longer literal may still match: record the kind. */
        KIND
    }

    /** How a case of the string-literal DFA ends. */
    public enum Exit {
        /** It does not end: it falls into what follows. */
        NONE,
        /** It goes on with the next position. */
        CALL,
        /** It moves the NFA from its start state. */
        MOVE_NFA,
        /** It leaves the switch, to hand over to the NFA after it. */
        BREAK,
        /** It returns the next position. */
        RETURN
    }

    /**
     * The NFA of a lexical state: {@code jjMoveNfa}, a switch over the states to advance, one per
     * group of characters.
     *
     * @param generatedStates how many states the NFA has
     * @param low             the arms for characters below 64
     * @param high            the arms for characters from 64 to 127
     * @param other           the arms for characters beyond ASCII
     */
    public record NfaMoves(int generatedStates, List<MoveArm> low, List<MoveArm> high,
                           List<MoveArm> other) {
    }

    /** Where an arm of the NFA switch comes from, which also fixes how it is laid out. */
    public enum ArmShape {
        /** A state, with the states that move exactly like it. */
        SINGLE,
        /** A composite state of which only one member moves on the group. */
        COMPOSITE_ONE,
        /** A composite state of which several members move. */
        COMPOSITE
    }

    /**
     * One arm of the NFA switch.
     *
     * @param shape   where it comes from
     * @param labels  the states it is taken for, in output order
     * @param leading how many of the labels were known before the states that move alike were
     *                looked for: the state itself, or the composite state and maybe its member
     * @param moves   the one move of the arm, or the moves of the members of a composite state
     */
    public record MoveArm(ArmShape shape, List<Integer> labels, int leading, List<Move> moves) {
    }

    /**
     * How one NFA state moves on the current character.
     *
     * @param shape  how it is laid out
     * @param guard  what it tests before it does anything
     * @param accept the kind it accepts, or null when it accepts none
     * @param elseIf whether it continues an if-else chain of the members of a composite state
     * @param next   the states it moves into, or null when there are none
     */
    public record Move(MoveShape shape, Guard guard, Accept accept, boolean elseIf,
                       NextStates next) {
    }

    /**
     * How a move is laid out. It used to be read back out of the move while it was printed, from
     * three sentinels at once -- an all-ones mask, a {@code oneChar} of -1 and a kind of
     * {@link Integer#MAX_VALUE} -- in four emitter routines that each spelled the same case
     * analysis differently (ADR-0029).
     */
    public enum MoveShape {
        /** Accepts a kind under its guard, and leads to no state. */
        ACCEPT,
        /** Leaves the arm unless its guard holds, accepts a kind, and moves into its states. */
        MATCH,
        /** Moves into its states under its guard, and accepts no kind. */
        ADVANCE
    }

    /** What a move tests before it does anything. */
    public sealed interface Guard {

        /** No test: the move is taken for every character of its group. */
        record Always() implements Guard {
        }

        /** The character is {@code c}, the only one of the group the move is taken for. */
        record OneChar(int c) implements Guard {
        }

        /** The character is one of the group of 64 whose bits {@code mask} sets. */
        record Mask(long mask) implements Guard {
        }

        /** The {@code jjCanMove} function of index {@code method} accepts the character. */
        record NonAscii(int method) implements Guard {
        }
    }

    /**
     * The kind a move accepts.
     *
     * @param kind  the kind
     * @param raise whether it yields to a lower kind the round has already matched. Only a move
     *              that is the one mover on every character of its guard may take the kind
     *              outright; beyond ASCII and inside a composite state no move ever is.
     */
    public record Accept(int kind, boolean raise) {
    }

    /**
     * The states a move adds to the state set.
     *
     * @param form    how they are added
     * @param first   the first state, or the first index into {@code jjnextStates}
     * @param second  the second state, or the last index into {@code jjnextStates}
     * @param isRange for {@link NextForm#CHECK_ADD_STATES}, whether the indices are more than two
     */
    public record NextStates(NextForm form, int first, int second, boolean isRange) {
    }

    /** How a move adds the states it leads to. */
    public enum NextForm {
        /** One state, without checking. */
        ADD,
        /** One state, checking it is not there yet. */
        CHECK_ADD,
        /** Two states, checking each. */
        CHECK_ADD_TWO,
        /** A range of {@code jjnextStates}, without checking. */
        ADD_STATES,
        /** A range of {@code jjnextStates}, checking each. */
        CHECK_ADD_STATES
    }

    /**
     * One position of {@code jjStopStringLiteralDfa}: the string-literal DFA gave up after it.
     *
     * @param pos   the position
     * @param cases the tests, in the order they are made
     */
    public record StopPos(int pos, List<StopCase> cases) {
    }

    /**
     * One test of {@code jjStopStringLiteralDfa}: if one of the literals of {@code guard} is still
     * active, record what was matched and resume the NFA in {@code resume}.
     *
     * @param guard      the words of the active vectors that are tested, with their masks
     * @param match      how the match is recorded
     * @param kind       the kind matched
     * @param matchedPos the position it was matched at
     * @param resume     the state the NFA resumes in, or -1 when there is none
     */
    public record StopCase(List<ActiveMask> guard, StopMatch match, int kind, int matchedPos,
                           int resume) {
    }

    /** A mask over word {@code word} of the active vectors. */
    public record ActiveMask(int word, long mask) {
    }

    /** How {@code jjStopStringLiteralDfa} records the literal matched so far. */
    public enum StopMatch {
        /** None was matched. */
        NONE,
        /** At the first position: the kind. */
        FIRST,
        /** At the first position, in a state that matches the empty string: kind and position. */
        FIRST_AFTER_EMPTY,
        /** At this position: kind and position. */
        HERE,
        /** At this position, unless a literal was matched here already. */
        HERE_UNLESS_MATCHED,
        /** At an earlier position, unless a longer one was matched. */
        EARLIER,
        /** At the first position, while nothing longer was matched. */
        EARLIER_AT_FIRST
    }

    /**
     * The characters {@code getNextToken} skips before it starts to match: plain SKIP tokens of a
     * single ASCII character.
     *
     * @param range   which half of ASCII they lie in
     * @param lower   the characters below 64, a bit each
     * @param upper   the characters from 64 to 127, a bit each
     * @param maxChar the highest of them, when they all lie in one half
     */
    public record SkipSingles(SkipRange range, long lower, long upper, int maxChar) {
    }

    /** Which half of ASCII the skipped characters lie in. */
    public enum SkipRange {
        LOWER, UPPER, BOTH
    }

    /**
     * What {@code getNextToken} does around the lexical states.
     *
     * @param eofActions       whether the end of input runs the token actions
     * @param imageInit        whether the image buffer is reset first: there are lexical actions
     * @param moreLoop         whether there is a MORE, and so the loop it goes round
     * @param switchOnLexState whether there is more than one lexical state to dispatch on
     * @param dispatch         what happens with a match; null when there is no lexical state
     */
    public record TokenLoop(boolean eofActions, boolean imageInit, boolean moreLoop,
                            boolean switchOnLexState, Dispatch dispatch) {
    }

    /**
     * How a matched kind is dispatched.
     *
     * @param tokenTest    whether a kind needs a test to be a TOKEN: there are other kinds
     * @param skipBranch   whether there is a SKIP or a SPECIAL_TOKEN branch
     * @param moreBranch   whether there is a MORE branch
     * @param special      whether there are special tokens
     * @param tokenActions whether the TOKEN productions have lexical actions
     * @param skipActions  whether the SKIP productions have lexical actions
     * @param moreActions  whether the MORE productions have lexical actions
     * @param moreImageLen whether a MORE without actions of its own still records the image length
     * @param newLexState  whether a kind can switch the lexical state
     */
    public record Dispatch(boolean tokenTest, boolean skipBranch, boolean moreBranch,
                           boolean special, boolean tokenActions, boolean skipActions,
                           boolean moreActions, boolean moreImageLen, boolean newLexState) {
    }

    /** The lexical actions of the TOKEN, MORE and SKIP productions, one case per kind. */
    public record Actions(List<ActionCase> token, List<ActionCase> more, List<ActionCase> skip) {
    }

    /**
     * The lexical action of one kind.
     *
     * @param kind      the kind
     * @param lexState  the lexical state it is matched in
     * @param loopCheck whether it can match the empty string over and over, and so needs a check
     * @param code      the action's code, empty when the case is only there for the check
     * @param image     where the image the action sees comes from
     */
    public record ActionCase(int kind, int lexState, boolean loopCheck, CodeText code,
                             ImageSource image) {
    }

    /** Where the image of a lexical action comes from. */
    public enum ImageSource {
        /** There is none: the end of input. */
        RESET,
        /** The literal of the kind. */
        LITERAL,
        /** The text that was matched. */
        MATCH
    }

    /**
     * What a token is called when the parser reports it.
     *
     * @param form    which of the names it has
     * @param ordinal its kind
     * @param label   its label, empty when it has none
     * @param image   its literal, or null when it is not a string literal
     */
    public record TokenName(NameForm form, int ordinal, String label, String image) {
    }

    /** Which name a token is reported by. */
    public enum NameForm {
        /** The end of input. */
        EOF,
        /** A string literal, by its image. */
        LITERAL,
        /** By its label. */
        LABEL,
        /** Neither literal nor label: by its kind. */
        KIND
    }

    /**
     * The tables the token manager reads at run time.
     *
     * @param newLexState    per kind, the lexical state it switches to, or -1; empty when there is
     *                       only one lexical state and so no table
     * @param kindTables     the kind sets that are written, in the order they are written
     * @param byteMasks      the distinct 256-bit masks over a byte that are not all ones
     * @param nextStates     the state sets the NFA moves into, one after the other in the order
     *                       they were registered ({@code jjnextStates})
     * @param nfa            whether any lexical state has an NFA; without one, there are no NFA
     *                       tables at all
     * @param kindsForState  per lexical state, the kind each NFA state accepts; empty when the
     *                       state has no NFA
     * @param statesForState per lexical state, the states each NFA state stands for; empty when
     *                       the state has no NFA. A state that stands for no composite set
     *                       stands for itself: its row is its own number.
     */
    public record Tables(List<Integer> newLexState, List<KindTable> kindTables,
                         List<ByteMask> byteMasks, List<Integer> nextStates, boolean nfa,
                         List<List<Integer>> kindsForState,
                         List<List<List<Integer>>> statesForState) {
    }

    /** The kind sets the token manager tests a matched kind against. */
    public enum KindSet {
        TOKEN, SKIP, SPECIAL, MORE
    }

    /** One kind set, 64 kinds per word. */
    public record KindTable(KindSet set, List<Long> words) {
    }

    /** A 256-bit mask over the low or the high byte of a character: four words. */
    public record ByteMask(int index, List<Long> words) {
    }

    /**
     * {@code jjCanMove_<method>}: whether a character beyond ASCII is in a state's character set,
     * which is stored as a mask over the low byte per high byte.
     *
     * @param method the number of the function
     * @param cases  the high bytes that have a mask of their own
     * @param arms   the pairs of masks for all other high bytes, in the order they are tested
     */
    public record CanMove(int method, List<CanMoveCase> cases, List<CanMoveArm> arms) {
    }

    /**
     * @param hiByte the high byte
     * @param mask   the mask over the low byte
     * @param any    whether the mask has every bit set, so no test is needed
     */
    public record CanMoveCase(int hiByte, int mask, boolean any) {
    }

    /**
     * @param hiMask the mask the high byte is tested against
     * @param loMask the mask the low byte is tested against
     * @param testHi whether the high byte needs a test: its mask is not all ones
     * @param testLo whether the low byte needs a test
     */
    public record CanMoveArm(int hiMask, int loMask, boolean testHi, boolean testLo) {
    }
}
