// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.hivevm.cc.ParserRequest;
import org.hivevm.cc.model.RChoice;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.TokenProduction;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.RegExprSpec;

/**
 * The {@link LexerBuilder} class.
 */
public class LexerBuilder {

    public LexerData build(ParserRequest request) {
        if (JavaCCErrors.hasError()) {
            return null;
        }

        Hashtable<String, List<TokenProduction>> allTpsForState = new Hashtable<>();
        LexerData data = buildLexStatesTable(request, allTpsForState);

        List<RExpression> choices = new ArrayList<>();
        NfaBuilder.buildLexer(data, allTpsForState, choices);

        choices.forEach(c -> StringLiteralAnalyzer.checkUnmatchability((RChoice) c, data));
        StringLiteralAnalyzer.checkEmptyStringMatch(data);

        for (String key : data.getStateNames()) {
            NfaStateData stateData = data.getStateData(key);
            if (stateData.hasNFA && !stateData.isMixedState()) {
                NfaBuilder.calcNfaStartStatesCode(stateData, stateData.statesForPos);
            }
        }

        for (String stateName : data.getStateNames()) {
            NfaStateData stateData = data.getStateData(stateName);
            if (stateData.hasNFA) {
                for (int i = 0; i < stateData.getAllStateCount(); i++) {
                    NfaBuilder.getNonAsciiMoves(data, stateData.getAllState(i));
                }
            }

            DfaBuilder.getDfaCode(stateData);
            if (stateData.hasNFA) {
                DfaBuilder.getMoveNfa(stateData);
            }
        }
        return data;
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
