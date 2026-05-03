// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.util.Hashtable;
import java.util.Locale;

import org.hivevm.cc.generator.NfaStateData.KindInfo;
import org.hivevm.cc.model.RChoice;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.parser.JavaCCErrors;

/**
 * Handles string literal DFA construction and lexer validation.
 */
class StringLiteralAnalyzer {

    private StringLiteralAnalyzer() {}

    /**
     * Builds the charPosKind table for a string literal (used for top-level string literals).
     */
    static void generateDfa(NfaStateData data, RStringLiteral rstring) {
        String s;
        Hashtable<String, NfaStateData.KindInfo> temp;
        NfaStateData.KindInfo info;
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
            }
            else {
                s = "" + (c = rstring.getImage().charAt(i));
            }

            if (i >= data.charPosKind.size()) { // Kludge, but OK
                data.charPosKind.add(temp = new Hashtable<>());
            }
            else { // Kludge, but OK
                temp = data.charPosKind.get(i);
            }

            if ((info = temp.get(s)) == null) {
                temp.put(s, info = new KindInfo(data.global.maxOrdinal));
            }

            if ((i + 1) == len) {
                info.InsertFinalKind(rstring.getOrdinal());
            }
            else {
                info.InsertValidKind(rstring.getOrdinal());
            }

            if (!data.ignoreCase() && data.global.ignoreCase[rstring.getOrdinal()] && (c
                    != Character.toLowerCase(c))) {
                s = ("" + rstring.getImage().charAt(i)).toLowerCase(Locale.ENGLISH);

                if (i >= data.charPosKind.size()) { // Kludge, but OK
                    data.charPosKind.add(temp = new Hashtable<>());
                }
                else { // Kludge, but OK
                    temp = data.charPosKind.get(i);
                }

                if ((info = temp.get(s)) == null) {
                    temp.put(s, info = new KindInfo(data.global.maxOrdinal));
                }

                if ((i + 1) == len) {
                    info.InsertFinalKind(rstring.getOrdinal());
                }
                else {
                    info.InsertValidKind(rstring.getOrdinal());
                }
            }

            if (!data.ignoreCase() && data.global.ignoreCase[rstring.getOrdinal()] && (c
                    != Character.toUpperCase(c))) {
                s = ("" + rstring.getImage().charAt(i)).toUpperCase();

                if (i >= data.charPosKind.size()) { // Kludge, but OK
                    data.charPosKind.add(temp = new Hashtable<>());
                }
                else { // Kludge, but OK
                    temp = data.charPosKind.get(i);
                }

                if ((info = temp.get(s)) == null) {
                    temp.put(s, info = new KindInfo(data.global.maxOrdinal));
                }

                if ((i + 1) == len) {
                    info.InsertFinalKind(rstring.getOrdinal());
                }
                else {
                    info.InsertValidKind(rstring.getOrdinal());
                }
            }
        }

        data.maxLenForActive[rstring.getOrdinal() / 64] =
                Math.max(data.maxLenForActive[rstring.getOrdinal() / 64], len - 1);
        data.global.allImages[rstring.getOrdinal()] = rstring.getImage();
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
                if ((j != i) && (data.global.getState(j) == data.getStateIndex()) && (
                        (data.global.getImage(j)) != null)) {
                    if (data.global.getImage(j).indexOf(image) == 0) {
                        data.subString[i] = true;
                        data.subStringAtPos[image.length() - 1] = true;
                        break;
                    }
                    else if (data.ignoreCase() && startsWithIgnoreCase(data.global.getImage(j), image)) {
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
                    JavaCCErrors.warning(choice,
                            "Regular Expression choice : " + regexp.getLabel()
                                    + " can never be matched as : " + choice.getLabel());
                }
                else {
                    JavaCCErrors.warning(choice,
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
                JavaCCErrors.warning(data.rexprs[data.initMatch[i]],
                        "Regular expression"
                                + ((data.rexprs[data.initMatch[i]].getLabel().equals("")) ? ""
                                : (" for " + data.rexprs[data.initMatch[i]].getLabel()))
                                + " can be matched by the empty string (\"\") in lexical state "
                                + data.getStateName(i)
                                + ". This can result in an endless loop of " + "empty string matches.");
            }
            else {
                JavaCCErrors.warning(data.rexprs[data.initMatch[i]],
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
}
