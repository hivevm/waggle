// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.lexer.LexerPlan.CharCase;
import org.hivevm.waggle.lexer.LexerPlan.DfaPos;
import org.hivevm.waggle.lexer.LexerPlan.Exit;
import org.hivevm.waggle.lexer.LexerPlan.Final;
import org.hivevm.waggle.lexer.LexerPlan.FinalAction;
import org.hivevm.waggle.lexer.LexerPlan.Handoff;
import org.hivevm.waggle.lexer.LexerPlan.LexStatePlan;
import org.hivevm.waggle.lexer.LexerPlan.StopCase;
import org.hivevm.waggle.lexer.LexerPlan.StopMatch;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Builds the output model of the string-literal DFA: one {@code jjMoveStringLiteralDfa} per
 * position, plus the {@code jjStopStringLiteralDfa} that hands over to the NFA, which the
 * templates write (ADR-0031). What is left per target is the spelling of names and expressions.
 *
 * <p>These used to be methods of the 3064-line {@code LexerGenerator}, reachable only by
 * extending it; Rust reached its own version by overriding one of them (ADR-0017).
 */
public class StringLiteralDfaEmitter {

    /** How the target spells a value. Composed, not inherited (ADR-0017). */
    protected final TargetSyntax syntax;

    public StringLiteralDfaEmitter(TargetSyntax syntax) {
        this.syntax = syntax;
    }

    /** The string-literal DFA of {@code lex} as the templates write it (ADR-0031). */
    public DfaModel.DfaCode dfaCode(LexState lex) {
        LexStatePlan state = lex.plan();
        if (state.positions().isEmpty()) {
            return new DfaModel.DfaCode(lex.suffix(), true,
                    (state.handoff() != Handoff.NONE) ? moveNfaCall(lex, "0") : "1", false,
                    List.of(), null);
        }
        var positions = state.positions().stream().map(pos -> dfaFn(lex, pos)).toList();
        return new DfaModel.DfaCode(lex.suffix(), false, null, state.stopAtPos(), positions,
                state.startNfaWithStates()
                        ? new DfaModel.StartWithStates(lex.suffix(), lex.withLexState()) : null);
    }

    /** {@code jjMoveStringLiteralDfa<pos>}. */
    private DfaModel.DfaFn dfaFn(LexState lex, DfaPos pos) {
        int i = pos.pos();
        Handoff handoff = lex.plan().handoff();
        var masked = new StringJoiner(" | ");
        var test = new StringJoiner(" | ");
        for (int j : pos.params()) {
            masked.add("(active" + j + " &= old" + j + ")");
            test.add("active" + j);
        }
        String checkReturn = switch (handoff) {
            case START_NFA -> {
                var args = new StringJoiner(", ");
                for (int j = 0; j < pos.old().size(); j++) {
                    args.add(pos.old().get(j) ? "old" + j : this.syntax.longZero());
                }
                yield startNfaName(lex) + "(" + (i - 2) + ", " + args + ")";
            }
            case MOVE_NFA -> moveNfaCall(lex, String.valueOf(i - 1));
            case NONE -> String.valueOf(i);
        };
        var cases = pos.cases().stream().map(charCase -> dfaCase(lex, i, charCase)).toList();
        return new DfaModel.DfaFn(i, lex.suffix(), signatureParams(pos), i > 1,
                masked.toString(), pos.params().stream().map(DfaModel.ActiveLet::new).toList(),
                test.toString(), checkReturn,
                possibleMatches(pos),
                i != 0, handoff == Handoff.START_NFA,
                this.syntax.stopStringLiteralDfaName(lex) + "(" + (i - 1) + ", "
                        + liveVectors(pos.active()) + ")",
                (handoff == Handoff.MOVE_NFA) ? moveNfaCall(lex, String.valueOf(i - 1))
                        : String.valueOf(i),
                lex.withLexState(), cases, exit(lex, pos.otherwise(), i, null), pos.tail(),
                handoff == Handoff.START_NFA,
                startNfaName(lex) + "(" + (i - 1) + ", " + liveVectors(pos.active()) + ")",
                handoff == Handoff.MOVE_NFA, moveNfaCall(lex, String.valueOf(i)), i + 1);
    }

    /** One character that continues a literal at position {@code i}. */
    private DfaModel.DfaCase dfaCase(LexState lex, int i, CharCase charCase) {
        var finals = new java.util.ArrayList<DfaModel.DfaFinal>();
        for (Final kind : charCase.finals()) {
            var action = kind.action();
            finals.add(new DfaModel.DfaFinal(i != 0, !finals.isEmpty(),
                    "active" + kind.word() + " & " + this.syntax.toHexString(kind.bit()),
                    action == FinalAction.START_NFA_WITH_STATES,
                    startNfaWithStatesName(lex) + "(" + i + ", " + kind.kind() + ", "
                            + kind.stateSet() + ")",
                    action == FinalAction.STOP_AT_POS,
                    stopAtPosName() + "(" + i + ", " + kind.kind() + ")",
                    action == FinalAction.KIND_AND_POS, action == FinalAction.KIND, kind.kind(), i));
        }
        String callNext = null;
        if (charCase.next() == Exit.CALL) {
            // The vectors of the next position: the valid kinds, preceded by the vector they are
            // masked against from the second position on.
            var args = new StringJoiner(", ");
            for (var mask : charCase.masks()) {
                var validKinds = (mask.mask() != 0L)
                        ? this.syntax.toHexString(mask.mask()) : this.syntax.longZero();
                args.add((i == 0) ? this.syntax.toHexString(mask.mask())
                        : "active" + mask.word() + ", " + validKinds);
            }
            callNext = moveStringLiteralDfaName(lex, i + 1) + "(" + args + ")";
        }
        return new DfaModel.DfaCase(
                charCase.labels().stream().map(DfaModel.DfaLabel::new).toList(), charCase.c(),
                List.copyOf(finals), exit(lex, charCase.next(), i, callNext));
    }

    /** How a case of position {@code i} ends. */
    private DfaModel.DfaExit exit(LexState lex, Exit exit, int i, String callNext) {
        return new DfaModel.DfaExit(exit == Exit.CALL, callNext, exit == Exit.MOVE_NFA,
                moveNfaCall(lex, String.valueOf(i)), exit == Exit.BREAK, exit == Exit.RETURN,
                i + 1);
    }

    /** The parameters of {@code jjMoveStringLiteralDfa<pos>}, as the target lists them. */
    protected String signatureParams(DfaPos pos) {
        return StringLiteralDfaEmitter.parameterList(pos, this.syntax.longType());
    }

    /** The active vectors, or zero for those that hold no literal any more. */
    private String liveVectors(List<Boolean> live) {
        var args = new StringJoiner(", ");
        for (int k = 0; k < live.size(); k++) {
            args.add(live.get(k) ? "active" + k : this.syntax.longZero());
        }
        return args.toString();
    }

    /** The call that hands control to the NFA in its initial state at {@code position}. */
    private String moveNfaCall(LexState lex, String position) {
        return this.syntax.moveNfaName(lex) + "(" + lex.plan().initState() + ", " + position + ")";
    }

    /** The name {@code jjStartNfa<state>} is called by. */
    protected String startNfaName(LexState lex) {
        return "jjStartNfa" + lex.suffix();
    }

    /** The name {@code jjStartNfaWithStates<state>} is called by. */
    protected String startNfaWithStatesName(LexState lex) {
        return "jjStartNfaWithStates" + lex.suffix();
    }

    /** The name {@code jjStopAtPos} is called by. */
    protected String stopAtPosName() {
        return "jjStopAtPos";
    }

    /** The name {@code jjMoveStringLiteralDfa<i><state>} is called by. */
    protected String moveStringLiteralDfaName(LexState lex, int i) {
        return "jjMoveStringLiteralDfa" + i + lex.suffix();
    }

    /**
     * {@code jjStopStringLiteralDfa}, which reports how far the string-literal DFA got, and
     * {@code jjStartNfa}, which hands the result over to the NFA; null when there is no string
     * literal to stop on.
     */
    public DfaModel.StopDfa stopDfa(LexState lex) {
        LexStatePlan state = lex.plan();
        if (state.stopDfa() == null) {
            return null;
        }
        String noState = this.syntax.noState();
        var positions = state.stopDfa().stream().map(pos -> new DfaModel.StopPos(pos.pos(), noState,
                pos.cases().stream().map(stop -> stopCase(pos.pos(), stop, noState)).toList()))
                .toList();
        int maxKindsReqd = state.words();
        return new DfaModel.StopDfa(lex.suffix(), this.syntax.activeParameters(maxKindsReqd),
                activeArguments(maxKindsReqd), noState, positions);
    }

    /** One test of {@code jjStopStringLiteralDfa} at position {@code i}. */
    private DfaModel.StopCase stopCase(int i, StopCase stop, String noState) {
        var condition = stop.guard().stream()
                .map(guard -> "(active" + guard.word() + " & "
                        + this.syntax.toHexString(guard.mask()) + ") != " + this.syntax.longZero())
                .collect(Collectors.joining(" || "));
        var match = stop.match();
        return new DfaModel.StopCase(condition, match != StopMatch.NONE,
                match == StopMatch.FIRST, match == StopMatch.FIRST_AFTER_EMPTY,
                match == StopMatch.HERE, match == StopMatch.HERE_UNLESS_MATCHED,
                match == StopMatch.EARLIER, match == StopMatch.EARLIER_AT_FIRST, stop.kind(), i,
                stop.matchedPos(), (stop.resume() == -1) ? noState : String.valueOf(stop.resume()));
    }

    /** {@code active0, active1, …} — the arguments {@code jjStartNfa} passes on. */
    private static String activeArguments(int maxKindsReqd) {
        return IntStream.range(0, maxKindsReqd).mapToObj(i -> "active" + i)
                .collect(Collectors.joining(", "));
    }

    /**
     * The parameters of {@code jjMoveStringLiteralDfa<i>} in C-like syntax, for its definition
     * and, in C++, for its declaration in the header.
     */
    public static String parameterList(DfaPos pos, String longType) {
        var params = new StringJoiner(", ");
        for (int j : pos.params()) {
            params.add((pos.pos() == 1) ? longType + " active" + j
                    : longType + " old" + j + ", " + longType + " active" + j);
        }
        return params.toString();
    }

    /** The trace of the literals that can still match at {@code pos}. */
    private static DfaModel.PossibleMatches possibleMatches(DfaPos pos) {
        var formats = new ArrayList<DfaModel.KindsFormat>();
        var vectors = new ArrayList<DfaModel.KindsVector>();
        for (int i = 0; i < pos.active().size(); i++) {
            if (pos.active().get(i)) {
                formats.add(new DfaModel.KindsFormat(formats.isEmpty()));
                vectors.add(new DfaModel.KindsVector(vectors.isEmpty(), i));
            }
        }
        return new DfaModel.PossibleMatches(List.copyOf(formats), List.copyOf(vectors));
    }
}
