// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java, org/javacc/parser/LexGen.java

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.api.Encoding;
import org.hivevm.waggle.lexer.NfaStateData.KindInfo;
import org.hivevm.waggle.model.RChoice;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RStringLiteral;

import java.util.Arrays;
import java.util.TreeMap;

/**
 * Handles string literal DFA construction and lexer validation.
 */
class StringLiteralAnalyzer {

    /**
     * Builds the charPosKind table for a string literal (used for top-level string literals).
     */
    static void generateDfa(NfaStateData data, RStringLiteral rstring) {
        int len;

        if (data.maxStrKind <= rstring.getOrdinal()) {
            data.maxStrKind = rstring.getOrdinal() + 1;
        }

        if ((len = rstring.getImage().length()) > data.maxLen) {
            data.maxLen = len;
        }

        for (int i = 0; i < len; i++) {
            char c = rstring.getImage().charAt(i);
            insertKind(data, i, len, data.ignoreCase() ? Character.toLowerCase(c) : c,
                    rstring.getOrdinal());

            if (!data.ignoreCase() && data.global.ignoreCase[rstring.getOrdinal()]) {
                if (c != Character.toLowerCase(c)) {
                    insertKind(data, i, len, Character.toLowerCase(c), rstring.getOrdinal());
                }
                if (c != Character.toUpperCase(c)) {
                    insertKind(data, i, len, Character.toUpperCase(c), rstring.getOrdinal());
                }
            }
        }

        data.maxLenForActive[rstring.getOrdinal() / 64] =
                Math.max(data.maxLenForActive[rstring.getOrdinal() / 64], len - 1);
        data.global.allImages[rstring.getOrdinal()] = rstring.getImage();
    }

    /**
     * Records {@code ordinal} at position {@code i} of the charPosKind table under key {@code c},
     * as a final kind when it is the last character of the literal and a valid kind otherwise.
     */
    private static void insertKind(NfaStateData data, int i, int len, char c, int ordinal) {
        if (i >= data.charPosKind.size()) {
            data.charPosKind.add(new TreeMap<>());
        }
        KindInfo info = data.charPosKind.get(i)
                .computeIfAbsent(c, key -> new KindInfo(data.global.maxOrdinal));

        if ((i + 1) == len) {
            info.InsertFinalKind(ordinal);
        } else {
            info.InsertValidKind(ordinal);
        }
    }

    /**
     * Computes the subString and subStringAtPos arrays for a lexer state.
     */
    static void fillSubString(NfaStateData data) {
        String image;
        data.subString = new boolean[data.maxStrKind + 1];
        data.subStringAtPos = new boolean[data.maxLen];

        for (int i = 0; i < data.maxStrKind; i++) {
            if (((image = data.global.getImage(i)) == null) || (data.global.getState(i)
                    != data.getStateIndex())) {
                continue;
            }

            if (data.isMixedState()) {
                data.subString[i] = true;
                data.subStringAtPos[image.length() - 1] = true;
                continue;
            }

            for (int j = 0; j < data.maxStrKind; j++) {
                String imageJ;
                if ((j != i) && (data.global.getState(j) == data.getStateIndex())
                        && ((imageJ = data.global.getImage(j)) != null)) {
                    if (imageJ.startsWith(image)
                            || (data.ignoreCase() && startsWithIgnoreCase(imageJ, image))) {
                        data.subString[i] = true;
                        data.subStringAtPos[image.length() - 1] = true;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Returns true if s1 starts with s2 (ignoring case for each character).
     */
    private static boolean startsWithIgnoreCase(String s1, String s2) {
        if (s1.length() < s2.length()) {
            return false;
        }

        for (int i = 0; i < s2.length(); i++) {
            char c1 = s1.charAt(i), c2 = s2.charAt(i);
            if ((c1 != c2) && (Character.toLowerCase(c2) != c1) && (Character.toUpperCase(c2) != c1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Warns if a choice alternative can never be matched.
     */
    static void checkUnmatchability(RChoice choice, LexerData data) {
        for (RExpression regexp : choice.getChoices()) {
            if (!regexp.isPrivateExp() && (regexp.getOrdinal() > 0)
                    && (regexp.getOrdinal() < choice.getOrdinal())
                    && (data.getState(regexp.getOrdinal()) == data.getState(choice.getOrdinal()))) {
                if (!choice.getLabel().isEmpty()) { // the label is "" when there is none
                    data.diagnostics().warning(choice,
                            "Regular Expression choice : " + regexp.getLabel()
                                    + " can never be matched as : " + choice.getLabel());
                } else {
                    data.diagnostics().warning(choice,
                            "Regular Expression choice : " + regexp.getLabel()
                                    + " can never be matched as token of kind : " + choice.getOrdinal());
                }
            }
        }
    }

    /**
     * Warns about regular expressions that can match the empty string, causing infinite loops.
     */
    static void checkEmptyStringMatch(LexerData data) {
        int i, j, k, len;
        boolean[] seen = new boolean[data.maxLexStates];
        boolean[] done = new boolean[data.maxLexStates];
        StringBuilder cycle;
        StringBuilder reList;

        Outer:
        for (i = 0; i < data.maxLexStates; i++) {
            if (done[i] || (data.initMatch[i] == 0) || (data.initMatch[i] == Integer.MAX_VALUE)
                    || (data.canMatchAnyChar[i] != -1)) {
                continue;
            }

            done[i] = true;
            len = 0;
            cycle = new StringBuilder();
            reList = new StringBuilder();

            Arrays.fill(seen, false);

            j = i;
            seen[i] = true;
            cycle.append(data.getStateName(j)).append("-->");
            while (data.newLexState[data.initMatch[j]] != null) {
                cycle.append(data.newLexState[data.initMatch[j]]);
                if (seen[j = data.getStateIndex(data.newLexState[data.initMatch[j]])]) {
                    break;
                }

                cycle.append("-->");
                done[j] = true;
                seen[j] = true;
                if ((data.initMatch[j] == 0) || (data.initMatch[j] == Integer.MAX_VALUE) || (
                        data.canMatchAnyChar[j] != -1)) {
                    continue Outer;
                }
                if (len != 0) {
                    reList.append("; ");
                }
                reList.append("line ").append(data.rexprs[data.initMatch[j]].getLine())
                        .append(", column ").append(data.rexprs[data.initMatch[j]].getColumn());
                len++;
            }

            if (data.newLexState[data.initMatch[j]] == null) {
                cycle.append(data.getStateName(data.getState(data.initMatch[j])));
            }

            for (k = 0; k < data.maxLexStates; k++) {
                data.canLoop[k] |= seen[k];
            }

            data.hasLoop = true;
            RExpression re = data.rexprs[data.initMatch[i]];
            String warning = "Regular expression"
                    + (re.getLabel().isEmpty() ? "" : (" for " + re.getLabel()))
                    + " can be matched by the empty string (\"\") in lexical state "
                    + data.getStateName(i) + ". ";
            if (len == 0) {
                data.diagnostics().warning(re,
                        warning + "This can result in an endless loop of empty string matches.");
            } else {
                data.diagnostics().warning(re,
                        warning + "This regular expression along with the regular expressions at "
                                + reList + " forms the cycle \n   " + cycle
                                + "\ncontaining regular expressions with empty matches."
                                + " This can result in an endless loop of empty string matches.");
            }
        }
    }

    /**
     * Warns about every string literal the DFA reports as another token, because a shorter literal
     * or a catch-all token declared before it wins. The back ends used to diagnose this while they
     * rendered the DFA — after the literal images they quote had already been pruned.
     */
    static void checkShadowedLiterals(NfaStateData data) {
        for (int i = 0; i < data.getMaxLen(); i++) {
            for (var entry : data.getCharPosKind(i).entrySet()) {
                KindInfo info = entry.getValue();
                if (data.isPlainSkip(info, i, entry.getKey()) || !info.hasFinalKindCnt()) {
                    continue;
                }

                for (int kind : info.finalKindsAscending()) {
                    if (data.isShadowedByIntermediate(i, kind) || data.isShadowedByAnyChar(i, kind)) {
                        warnShadowed(data.global, kind, data.kindToPrint(i, kind));
                    }
                }
            }
        }
    }

    private static void warnShadowed(LexerData data, int kind, int matchedAs) {
        RExpression re = data.getRegExp(kind);
        data.diagnostics().warning(" \"" + Encoding.escape(data.getImage(kind))
                + "\" cannot be matched as a string literal token  at line " + re.getLine()
                + ", column " + re.getColumn() + ". It will be matched as "
                + StringLiteralAnalyzer.label(data, matchedAs) + ".");
    }

    private static String label(LexerData data, int kind) {
        RExpression re = data.getRegExp(kind);
        if (re instanceof RStringLiteral literal) {
            return " \"" + Encoding.escape(literal.getImage()) + "\"";
        } else if (!re.getLabel().isEmpty()) {
            return " <" + re.getLabel() + ">";
        } else {
            return " <token of kind " + kind + ">";
        }
    }
}
