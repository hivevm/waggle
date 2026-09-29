// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java, org/javacc/parser/LexGen.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.api.Encoding;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * How one target language spells the pieces a token manager is made of: a comparison, a branch, a
 * switch arm, a break, a call into the state-set helpers.
 *
 * <p>These used to be overridable methods on the 3064-line {@code LexerGenerator}, so a back end
 * altered the emission by extending the algorithm that called them. They are a vocabulary, not
 * behaviour, and an emitter takes one of these rather than inheriting it (ADR-0017). The default
 * answers are Java's, which is why the Java back end overrides almost nothing.
 */
public interface TargetSyntax {

    /** "the current character equals c" */
    default String charEquals(int c) {
        return "curChar == " + c;
    }

    /** "the current character does not equal c" */
    default String charNotEquals(int c) {
        return "curChar != " + c;
    }

    /** "the bit for the current character is set in mask" */
    default String bitIsSet(long mask) {
        return "(" + toHexString(mask) + " & l) != 0L";
    }

    /** "the bit for the current character is not set in mask" */
    default String bitIsClear(long mask) {
        return "(" + toHexString(mask) + " & l) == 0L";
    }

    /** The call that tests whether a non-ASCII character can move out of "state". */
    default String canMove(int method) {
        return "jjCanMove_" + method + "(hiByte, i1, i2, l1, l2)";
    }

    /** Renders one entry of the token-image table. */
    default String tokenImage(String image) {
        return "\"" + image + "\"";
    }

    /**
     * Renders a string literal's entry. Only C++ has two tables — the label and the raw image — and
     * so only C++ looks at {@code isImage}; the other back ends have no {@code REXPRESSION_IMAGE}
     * placeholder in their templates.
     */
    default String stringLiteralImage(String image, String label, boolean isImage) {
        return "\"\\\"" + Encoding.escape(Encoding.escape(image)) + "\\\"\"";
    }

    /** Separates two entries of the table. C++ emits one array per entry and needs none. */
    default String imageSeparator(int i, int last) {
        // every entry but the last, and always <EOF>
        return ((i == 0) || (i < last)) ? "," : "";
    }

    /** {@code long active0, long active1, …} — the active vectors as parameters. */
    default String activeParameters(int maxKindsReqd) {
        return IntStream.range(0, maxKindsReqd).mapToObj(i -> longType() + " active" + i)
                .collect(Collectors.joining(", "));
    }

    /** The name {@code jjMoveNfa} is called under. */
    default String moveNfaName(LexState lex) {
        return "jjMoveNfa" + lex.suffix();
    }

    /** The name {@code jjStopStringLiteralDfa} is called under. */
    default String stopStringLiteralDfaName(LexState lex) {
        return "jjStopStringLiteralDfa" + lex.suffix();
    }

    /** The value {@code jjStopStringLiteralDfa} returns when no NFA state is left to go to. */
    default String noState() {
        return "-1";
    }

    /** The 64-bit integer type of the target. */
    default String longType() {
        return "long";
    }

    /** The zero of a 64-bit literal. */
    default String longZero() {
        return "0L";
    }

    /** What an absent table looks like. */
    default String noStateSet() {
        return "null";
    }

    /** How a table of state sets is wrapped. */
    default String stateSet(CharSequence body) {
        return "{" + body + "}";
    }

    /** How one row of such a table opens. Rust writes a slice where the others write a brace. */
    default String rowOpen() {
        return "{";
    }

    default String rowClose() {
        return "}";
    }

    default String toHexString(long value) {
        return "0x" + Long.toHexString(value) + "L";
    }
}
