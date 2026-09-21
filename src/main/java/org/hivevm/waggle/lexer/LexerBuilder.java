// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.ParserRequest;
import org.hivevm.waggle.model.RChoice;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RStringLiteral;
import org.hivevm.waggle.model.TokenKind;
import org.hivevm.waggle.model.TokenProduction;
import org.hivevm.waggle.model.RegExprSpec;

import java.util.ArrayList;
import java.util.Hashtable;
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

        Hashtable<String, List<TokenProduction>> allTpsForState = new Hashtable<>();
        LexerData data = buildLexStatesTable(request, allTpsForState);

        List<RExpression> choices = new ArrayList<>();
        Nfa.buildLexer(data, allTpsForState, choices);

        choices.forEach(c -> StringLiteralAnalyzer.checkUnmatchability((RChoice) c, data));
        StringLiteralAnalyzer.checkEmptyStringMatch(data);

        // The stop-string-literal DFA registers its composite state sets lazily at emit time via
        // LexerGenerator#dumpNfaStartStatesCode; an earlier compute-time pre-registration pass here
        // was dead code (its guard was never satisfied) and has been removed.
        for (String stateName : data.getStateNames()) {
            NfaStateData stateData = data.getStateData(stateName);
            if (stateData.hasNFA) {
                for (int i = 0; i < stateData.getAllStateCount(); i++) {
                    Nfa.getNonAsciiMoves(data, stateData.getAllState(i));
                }
            }

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
        return data;
    }

    /**
     * An unlabelled TOKEN that is not a string literal can only be reported to the user as
     * "&lt;token of kind N&gt;". This used to be diagnosed by the back ends while they rendered the
     * token-image table — once per table, so C++ reported it twice.
     */
    private static void warnAboutUnlabelledTokens(ParserRequest request) {
        for (TokenProduction tp : request.getTokenProductions()) {
            if (tp.getRespecs() == null) {
                continue;
            }
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
            long bit = 1L << (i % 64);
            boolean isSkip = (data.toSkip[i / 64] & bit) != 0L;
            boolean isMore = (data.toMore[i / 64] & bit) != 0L;
            boolean isToken = (data.toToken[i / 64] & bit) != 0L;
            if ((image == null) || !isToken || isSkip || isMore
                    || data.canReachOnMore(data.getState(i))
                    || ((data.ignoreCase() || data.ignoreCase(i))
                    && (!image.equals(image.toLowerCase(Locale.ENGLISH))
                    || !image.equals(image.toUpperCase(Locale.ENGLISH))))) {
                data.allImages[i] = null;
            }
        }
    }

    private LexerData buildLexStatesTable(ParserRequest request,
                                          Hashtable<String, List<TokenProduction>> allTpsForState) {
        String[] tmpLexStateName = new String[request.getStateCount()];
        int maxOrdinal = 1;
        int maxLexStates = 0;
        for (TokenProduction tp : request.getTokenProductions()) {
            List<RegExprSpec> respecs = tp.getRespecs();
            List<TokenProduction> tps;

            for (String lexState : tp.getLexStates()) {
                if ((tps = allTpsForState.get(lexState)) == null) {
                    tmpLexStateName[maxLexStates++] = lexState;
                    allTpsForState.put(lexState, tps = new ArrayList<>());
                }
                tps.add(tp);
            }

            if ((respecs == null) || (respecs.isEmpty())) {
                continue;
            }

            RExpression re;
            for (RegExprSpec respec : respecs) {
                if (maxOrdinal <= (re = respec.rexp).getOrdinal()) {
                    maxOrdinal = re.getOrdinal() + 1;
                }
            }
        }

        LexerData data = new LexerData(request, maxOrdinal, maxLexStates);
        System.arraycopy(tmpLexStateName, 0, data.lexStateNames, 0, data.maxLexStates);
        return data;
    }
}
