// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/LexGen.java, org/javacc/parser/LexGenCPP.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.lexer.LexerPlan;
import org.hivevm.waggle.lexer.LexerPlan.Dispatch;
import org.hivevm.waggle.lexer.LexerPlan.SkipSingles;
import org.hivevm.waggle.lexer.LexerPlan.TokenLoop;
import org.hivevm.waggle.lexer.LexerPlan.TokenName;

import java.util.List;

/**
 * Builds the output model of {@code getNextToken} -- the lexical-state switch, the skip, more and
 * token branches -- and the entries of the token-image table; the templates write them
 * (ADR-0031). What is left per target is the spelling of the skip test.
 *
 * <p>These used to be methods of the 3064-line {@code LexerGenerator}, reachable only by extending
 * it (ADR-0017).
 */
public class GetNextTokenEmitter {

    /** How the target spells a value. Composed, not inherited (ADR-0017). */
    protected final TargetSyntax syntax;

    public GetNextTokenEmitter(TargetSyntax syntax) {
        this.syntax = syntax;
    }

    /**
     * Whether the current character is one of those that can only ever be skipped: a test against
     * the lower, the upper or both halves of the ASCII bit vector. Java and C++ share it.
     */
    protected final String skipSinglesCondition(SkipSingles skip) {
        return switch (skip.range()) {
            case BOTH -> "(curChar < 64 && (" + this.syntax.toHexString(skip.lower())
                    + " & (" + longOne() + " << curChar)) != 0L) || \n"
                    + "          (curChar >> 6) == 1 && (" + this.syntax.toHexString(skip.upper())
                    + " & (" + longOne() + " << (curChar & 077))) != 0L";
            case LOWER -> "curChar <= " + skip.maxChar()
                    + " && (" + this.syntax.toHexString(skip.lower()) + " & (" + longOne() + " << curChar)) != 0L";
            case UPPER -> "curChar > 63 && curChar <= "
                    + skip.maxChar() + " && (" + this.syntax.toHexString(skip.upper())
                    + " & (" + longOne() + " << (curChar & 077))) != 0L";
        };
    }

    /** A 64-bit one, to shift into a bit mask. */
    protected String longOne() {
        return "1L";
    }

    /** {@code getNextToken} as the templates write it (ADR-0031). */
    public TokenLoopModel.GetNextToken model(LexerPlan plan) {
        TokenLoop loop = plan.tokenLoop();
        boolean withLexState = loop.switchOnLexState();
        var states = plan.states().stream().map(state -> new TokenLoopModel.LexStateCase(
                withLexState, state.index(), new TokenLoopModel.StateBody(state.index(),
                        withLexState, state.skip() != null,
                        (state.skip() == null) ? null
                                : new TokenLoopModel.SkipLoop(skipCondition(state.skip()),
                                        withLexState),
                        state.matchesEmpty(), state.emptyMatch(), state.anyChar() != -1,
                        state.anyChar()))).toList();
        Dispatch d = loop.dispatch();
        TokenLoopModel.MatchDispatch dispatch = null;
        if (d != null) {
            dispatch = new TokenLoopModel.MatchDispatch(d.tokenTest(), d.moreBranch(),
                    new TokenLoopModel.TokenBranch(d.special(), d.tokenActions(), d.newLexState()),
                    (d.tokenTest() && d.skipBranch())
                            ? new TokenLoopModel.SkipBranch(d.moreBranch(), d.special(),
                                    d.skipActions(), d.newLexState())
                            : null,
                    (d.tokenTest() && d.moreBranch())
                            ? new TokenLoopModel.MoreBranch(d.moreActions(), d.moreImageLen(),
                                    d.newLexState(), withLexState)
                            : null);
        }
        var body = new TokenLoopModel.LoopBody(withLexState,
                !withLexState && (d == null), states, dispatch);
        return new TokenLoopModel.GetNextToken(loop.eofActions(), loop.imageInit(),
                loop.moreLoop(), body);
    }

    /** Whether the current character is one of those that can only ever be skipped. */
    protected String skipCondition(SkipSingles skip) {
        return skipSinglesCondition(skip);
    }

    /** One entry of the table the parser reports an unexpected token with. */
    protected String getRegExp(int i, List<TokenName> names, boolean isImage) {
        var name = names.get(i);
        var entry = switch (name.form()) {
            case EOF -> this.syntax.tokenImage("<EOF>");
            case LITERAL -> this.syntax.stringLiteralImage(name.image(), name.label(), isImage);
            case LABEL -> this.syntax.tokenImage("<" + name.label() + ">");
            // A Java or C++ initializer with an empty element does not even compile.
            case KIND -> this.syntax.tokenImage("<token of kind " + name.ordinal() + ">");
        };
        return entry + this.syntax.imageSeparator(i, names.size() - 1);
    }

}
