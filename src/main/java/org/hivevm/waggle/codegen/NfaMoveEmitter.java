// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.lexer.LexerPlan.Accept;
import org.hivevm.waggle.lexer.LexerPlan.Guard;
import org.hivevm.waggle.lexer.LexerPlan.Move;
import org.hivevm.waggle.lexer.LexerPlan.MoveArm;
import org.hivevm.waggle.lexer.LexerPlan.MoveShape;
import org.hivevm.waggle.lexer.LexerPlan.NextForm;
import org.hivevm.waggle.lexer.LexerPlan.NextStates;

import java.util.List;

/**
 * Builds the output model of the NFA move arms: the ASCII and non-ASCII moves of single and
 * composite states, which the templates write (ADR-0031). What is left per target is the
 * spelling of a move's test.
 *
 * <p>These used to be methods of the 3064-line {@code LexerGenerator}, reachable only by
 * extending it; Rust reached its own version by overriding one of them (ADR-0017).
 */
public class NfaMoveEmitter {

    /** How the target spells a move's test. Composed, not inherited (ADR-0017). */
    protected final TargetSyntax syntax;

    /** The indentation of the labels of a composite state of which one member moves. */
    private static final String COMPOSITE_INDENT = "               ";

    public NfaMoveEmitter(TargetSyntax syntax) {
        this.syntax = syntax;
    }

    /** The arms of the NFA switch for one group of characters (ADR-0031); -1 is beyond ASCII. */
    public NfaModel.NfaSection section(List<MoveArm> arms, int byteNum) {
        var models = new java.util.ArrayList<NfaModel.NfaArm>();
        for (MoveArm arm : arms) {
            var joined = arm.labels().stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(" | "));
            boolean hasLabels = !arm.labels().isEmpty();
            if (byteNum >= 0) {
                switch (arm.shape()) {
                    case COMPOSITE -> models.add(new NfaModel.AsciiComposite(
                            arm.labels().getFirst(), joined,
                            arm.moves().stream().map(this::compositeMove).toList()));
                    case COMPOSITE_ONE -> models.add(new NfaModel.AsciiArm(
                            labels(arm, NfaMoveEmitter.COMPOSITE_INDENT), hasLabels,
                            NfaMoveEmitter.COMPOSITE_INDENT, joined,
                            asciiMove(arm.moves().getFirst())));
                    case SINGLE -> models.add(new NfaModel.AsciiArm(labels(arm, ""), hasLabels,
                            "", joined, asciiMove(arm.moves().getFirst())));
                }
            } else {
                switch (arm.shape()) {
                    case COMPOSITE -> models.add(new NfaModel.WideComposite(
                            arm.labels().getFirst(), hasLabels, joined,
                            arm.moves().stream().map(this::wideCompositeMove).toList()));
                    case COMPOSITE_ONE -> models.add(new NfaModel.WideOne(labels(arm, ""),
                            hasLabels, joined, wideMove(arm.moves().getFirst())));
                    case SINGLE -> models.add(new NfaModel.WideSingle(arm.labels().getFirst(),
                            arm.labels().subList(1, arm.labels().size()).stream()
                                    .map(c -> new NfaModel.NfaLabel("", c)).toList(),
                            hasLabels, joined, wideMove(arm.moves().getFirst())));
                }
            }
        }
        return new NfaModel.NfaSection(byteNum == 0, byteNum == 1, byteNum < 0,
                List.copyOf(models));
    }

    /** The labels of an arm: the leading ones at {@code leadIndent}, the merged ones without. */
    private static List<NfaModel.NfaLabel> labels(MoveArm arm, String leadIndent) {
        var labels = new java.util.ArrayList<NfaModel.NfaLabel>();
        for (int i = 0; i < arm.labels().size(); i++) {
            labels.add(new NfaModel.NfaLabel((i < arm.leading()) ? leadIndent : "",
                    arm.labels().get(i)));
        }
        return List.copyOf(labels);
    }

    private NfaModel.AsciiMove asciiMove(Move move) {
        boolean guarded = !(move.guard() instanceof Guard.Always);
        var accept = move.accept();
        return new NfaModel.AsciiMove(move.shape() == MoveShape.ACCEPT,
                move.shape() == MoveShape.MATCH, move.shape() == MoveShape.ADVANCE, guarded,
                guarded ? condition(move.guard()) : "",
                (guarded && (accept != null)) ? condition(move.guard()) + raises(accept) : "",
                guarded ? negated(move.guard()) : "", (accept != null) && accept.raise(),
                (accept == null) ? 0 : accept.kind(), stateAdd(move.next()));
    }

    private NfaModel.CompositeMove compositeMove(Move move) {
        boolean guarded = !(move.guard() instanceof Guard.Always);
        var accept = move.accept();
        return new NfaModel.CompositeMove(guarded, move.elseIf(),
                guarded ? condition(move.guard()) : "", accept != null,
                (accept != null) && accept.raise(), (accept == null) ? 0 : accept.kind(),
                stateAdd(move.next()));
    }

    private NfaModel.WideCompositeMove wideCompositeMove(Move move) {
        var accept = move.accept();
        return new NfaModel.WideCompositeMove(condition(move.guard()), accept != null,
                (accept != null) && accept.raise(), (accept == null) ? 0 : accept.kind(),
                stateAdd(move.next()));
    }

    private NfaModel.WideMove wideMove(Move move) {
        var accept = move.accept();
        return new NfaModel.WideMove(move.shape() == MoveShape.ACCEPT,
                move.shape() == MoveShape.MATCH, move.shape() == MoveShape.ADVANCE,
                condition(move.guard()),
                (accept != null) ? condition(move.guard()) + raises(accept) : "",
                negated(move.guard()), (accept == null) ? 0 : accept.kind(),
                stateAdd(move.next()));
    }

    private static NfaModel.StateAdd stateAdd(NextStates next) {
        if (next == null) {
            return null;
        }
        var form = next.form();
        return new NfaModel.StateAdd(form == NextForm.ADD, form == NextForm.CHECK_ADD,
                form == NextForm.CHECK_ADD_TWO, form == NextForm.ADD_STATES,
                form == NextForm.CHECK_ADD_STATES, next.first(), next.second(), next.isRange());
    }

    /** The test a move is taken under. */
    private String condition(Guard guard) {
        return switch (guard) {
            case Guard.OneChar g -> this.syntax.charEquals(g.c());
            case Guard.Mask g -> this.syntax.bitIsSet(g.mask());
            case Guard.NonAscii g -> this.syntax.canMove(g.method());
            // A move that is taken for every character of its group is written without a test;
            // reaching this would mean the plan and the shape disagree.
            case Guard.Always g -> throw new IllegalStateException("an unguarded move has no test");
        };
    }

    /** The same test the other way round, for a move that leaves the arm when it does not hold. */
    private String negated(Guard guard) {
        return switch (guard) {
            case Guard.OneChar g -> this.syntax.charNotEquals(g.c());
            case Guard.Mask g -> this.syntax.bitIsClear(g.mask());
            case Guard.NonAscii g -> "!" + this.syntax.canMove(g.method());
            case Guard.Always g -> throw new IllegalStateException("an unguarded move has no test");
        };
    }

    /** What a move that only accepts adds to its condition to yield to a lower kind. */
    private static String raises(Accept accept) {
        return accept.raise() ? " && kind > " + accept.kind() : "";
    }

}
