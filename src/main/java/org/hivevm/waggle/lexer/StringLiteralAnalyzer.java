// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.Encoding;
import org.hivevm.waggle.lexer.NfaStateData.KindInfo;
import org.hivevm.waggle.model.RChoice;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RStringLiteral;

import java.util.Hashtable;
import java.util.Locale;

/**
 * Handles string literal DFA construction and lexer validation.
 */
class StringLiteralAnalyzer {

    /**
     * Builds the charPosKind table for a string literal (used for top-level string literals).
     */
    static void generateDfa(NfaStateData data, RStringLiteral rstring) {
        String s;
        int len;

        if (data.maxStrKind <= rstring.getOrdinal()) {
            data.maxStrKind = rstring.getOrdinal() + 1;
        }

        if ((len = rstring.getImage().length()) > data.maxLen) {
            data.maxLen = len;
        }

        char c;
        for (int i = 0; i < len; i++) {
            if (data.ignoreCase()) {
                s = ("" + (c = rstring.getImage().charAt(i))).toLowerCase(Locale.ENGLISH);
            } else {
                s = "" + (c = rstring.getImage().charAt(i));
            }

            insertKind(data, i, len, s, rstring.getOrdinal());

            if (!data.ignoreCase() && data.global.ignoreCase[rstring.getOrdinal()] && (c
                    != Character.toLowerCase(c))) {
                s = ("" + rstring.getImage().charAt(i)).toLowerCase(Locale.ENGLISH);
                insertKind(data, i, len, s, rstring.getOrdinal());
            }

            if (!data.ignoreCase() && data.global.ignoreCase[rstring.getOrdinal()] && (c
                    != Character.toUpperCase(c))) {
                s = ("" + rstring.getImage().charAt(i)).toUpperCase(Locale.ENGLISH);
                insertKind(data, i, len, s, rstring.getOrdinal());
            }
        }

        data.maxLenForActive[rstring.getOrdinal() / 64] =
                Math.max(data.maxLenForActive[rstring.getOrdinal() / 64], len - 1);
        data.global.allImages[rstring.getOrdinal()] = rstring.getImage();
    }

    /**
     * Records {@code ordinal} at position {@code i} of the charPosKind table under key {@code s},
     * as a final kind when it is the last character of the literal and a valid kind otherwise.
     */
    private static void insertKind(NfaStateData data, int i, int len, String s, int ordinal) {
        Hashtable<String, NfaStateData.KindInfo> temp;
        NfaStateData.KindInfo info;
        if (i >= data.charPosKind.size()) { // Kludge, but OK
            data.charPosKind.add(temp = new Hashtable<>());
        } else { // Kludge, but OK
            temp = data.charPosKind.get(i);
        }

        if ((info = temp.get(s)) == null) {
            temp.put(s, info = new KindInfo(data.global.maxOrdinal));
        }

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
            data.subString[i] = false;

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
                    if (imageJ.indexOf(image) == 0) {
                        data.subString[i] = true;
                        data.subStringAtPos[image.length() - 1] = true;
                        break;
                    } else if (data.ignoreCase() && startsWithIgnoreCase(imageJ, image)) {
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
                if (choice.getLabel() != null) {
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

            for (k = 0; k < data.maxLexStates; k++) {
                seen[k] = false;
            }

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
            if (len == 0) {
                data.diagnostics().warning(data.rexprs[data.initMatch[i]],
                        "Regular expression"
                                + ((data.rexprs[data.initMatch[i]].getLabel().equals("")) ? ""
                                : (" for " + data.rexprs[data.initMatch[i]].getLabel()))
                                + " can be matched by the empty string (\"\") in lexical state "
                                + data.getStateName(i)
                                + ". This can result in an endless loop of " + "empty string matches.");
            } else {
                data.diagnostics().warning(data.rexprs[data.initMatch[i]],
                        "Regular expression"
                                + ((data.rexprs[data.initMatch[i]].getLabel().equals("")) ? ""
                                : (" for " + data.rexprs[data.initMatch[i]].getLabel()))
                                + " can be matched by the empty string (\"\") in lexical state "
                                + data.getStateName(i)
                                + ". This regular expression along with the " + "regular expressions at "
                                + reList
                                + " forms the cycle \n   " + cycle
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
        int maxLongsReqd = (data.getMaxStrKind() / 64) + 1;
        for (int i = 0; i < data.getMaxLen(); i++) {
            Hashtable<String, KindInfo> tab = data.getCharPosKind(i);
            for (String key : NfaStateData.reArrange(tab)) {
                KindInfo info = tab.get(key);
                if (data.isPlainSkip(info, i, key.charAt(0)) || !info.hasFinalKindCnt()) {
                    continue;
                }

                for (int j = 0; j < maxLongsReqd; j++) {
                    for (int k = 0; k < 64; k++) {
                        int kind = (j * 64) + k;
                        if ((info.finalKinds[j] & (1L << k)) == 0L) {
                            continue;
                        }
                        if (data.isShadowedByIntermediate(i, kind)
                                || data.isShadowedByAnyChar(i, kind)) {
                            warnShadowed(data.global, kind, data.kindToPrint(i, kind));
                        }
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
