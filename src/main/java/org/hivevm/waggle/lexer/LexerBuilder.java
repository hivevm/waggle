// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// Copyright 2012 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java, org/javacc/parser/LexGen.java

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.api.ParserRequest;
import org.hivevm.waggle.model.RChoice;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RStringLiteral;
import org.hivevm.waggle.model.TokenKind;
import org.hivevm.waggle.model.TokenProduction;
import org.hivevm.waggle.model.RegExprSpec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@link LexerBuilder} class.
 */
public class LexerBuilder {

    public LexerData build(ParserRequest request) {
        if (request.diagnostics().hasError()) {
            return null;
        }

        Map<String, List<TokenProduction>> allTpsForState = new LinkedHashMap<>();
        LexerData data = buildLexStatesTable(request, allTpsForState);

        List<RChoice> choices = new ArrayList<>();
        Nfa.buildLexer(data, allTpsForState, choices);

        choices.forEach(c -> StringLiteralAnalyzer.checkUnmatchability(c, data));
        StringLiteralAnalyzer.checkEmptyStringMatch(data);

        for (String stateName : data.getStateNames()) {
            NfaStateData stateData = data.getStateData(stateName);
            if (stateData.hasNFA) {
                for (NfaState state : stateData.getAllStates()) {
                    Nfa.getNonAsciiMoves(data, state);
                }
            }

            DfaBuilder.registerStopStateSets(stateData);
            DfaBuilder.getDfaCode(stateData);
            if (stateData.hasNFA) {
                DfaBuilder.getMoveNfa(stateData);
            }
        }

        for (String stateName : data.getStateNames()) {
            StringLiteralAnalyzer.checkShadowedLiterals(data.getStateData(stateName));
        }
        warnAboutUnlabelledTokens(request);
        pruneLiteralImages(data);
        data.plan = LexerPlanner.plan(data);
        return data;
    }

    /**
     * An unlabelled TOKEN that is not a string literal can only be reported to the user as
     * "&lt;token of kind N&gt;". This used to be diagnosed by the back ends while they rendered the
     * token-image table — once per table, so C++ reported it twice.
     */
    private static void warnAboutUnlabelledTokens(ParserRequest request) {
        for (TokenProduction tp : request.getTokenProductions()) {
            for (RegExprSpec respec : tp.getRespecs()) {
                RExpression re = respec.rexp;
                if (!(re instanceof RStringLiteral) && re.getLabel().isEmpty()
                        && (re.getTokenKind() == TokenKind.TOKEN)) {
                    request.diagnostics().warning(re,
                            "Consider giving this non-string token a label for better error reporting.");
                }
            }
        }
    }

    /**
     * Keeps a string literal's image only where the token manager may use it verbatim: for a TOKEN
     * that is not reached through MORE and whose case does not vary. Every other image is dropped,
     * so that the image table and the lexical actions fall back to the matched text. The three back
     * ends used to do this themselves, while rendering the image table — so whether an action saw
     * the pruned table depended on where its template happened to place that table.
     */
    private static void pruneLiteralImages(LexerData data) {
        if (data.getImageCount() <= 0) {
            return;
        }

        data.allImages[0] = "";
        for (int i = 0; i < data.getImageCount(); i++) {
            String image = data.allImages[i];
            if ((image == null) || !data.isToken(i) || data.isSkip(i) || data.isMore(i)
                    || data.canReachOnMore(data.getState(i))
                    || ((data.ignoreCase() || data.ignoreCase(i))
                    && (!image.equals(image.toLowerCase(Locale.ENGLISH))
                    || !image.equals(image.toUpperCase(Locale.ENGLISH))))) {
                data.allImages[i] = null;
            }
        }
    }

    private LexerData buildLexStatesTable(ParserRequest request,
                                          Map<String, List<TokenProduction>> allTpsForState) {
        int maxOrdinal = 1;
        for (TokenProduction tp : request.getTokenProductions()) {
            for (String lexState : tp.getLexStates()) {
                allTpsForState.computeIfAbsent(lexState, k -> new ArrayList<>()).add(tp);
            }
            for (RegExprSpec respec : tp.getRespecs()) {
                maxOrdinal = Math.max(maxOrdinal, respec.rexp.getOrdinal() + 1);
            }
        }

        LexerData data = new LexerData(request, maxOrdinal, allTpsForState.size());
        allTpsForState.keySet().toArray(data.lexStateNames);
        return data;
    }
}
