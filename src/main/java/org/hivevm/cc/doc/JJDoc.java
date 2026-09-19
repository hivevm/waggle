// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.doc;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.HiveCCOptions;
import org.hivevm.cc.model.*;
import org.hivevm.cc.parser.JavaCCData;
import org.hivevm.cc.model.RegExprSpec;

import java.util.Iterator;

/**
 * The main entry point for JJDoc.
 */
class JJDoc extends JJDocGlobals {

    static void start(JavaCCData javacc) {
        JJDocGlobals.generator = JJDocGlobals.getGenerator((HiveCCOptions) javacc.options());
        JJDocGlobals.generator.documentStart();
        JJDoc.emitTokenProductions(JJDocGlobals.generator, javacc.getTokenProductions());
        JJDoc.emitNormalProductions(JJDocGlobals.generator, javacc.getNormalProductions());
        JJDocGlobals.generator.documentEnd();
    }


    private static void emitTokenProductions(Generator gen, Iterable<TokenProduction> prods) {
        gen.tokensStart();
        for (TokenProduction tp : prods) {
            gen.handleTokenProduction(tp);
        }
        gen.tokensEnd();
    }

    static String getStandardTokenProductionText(TokenProduction tp) {
        var token = new StringBuilder();
        if (tp.isExplicit()) {
            if (tp.getLexStates() == null) {
                token.append("<*> ");
            } else {
                token.append("<");
                for (int i = 0; i < tp.getLexStates().length; ++i) {
                    token.append(tp.getLexStates()[i]);
                    if (i < (tp.getLexStates().length - 1)) {
                        token.append(",");
                    }
                }
                token.append("> ");
            }
            token.append(tp.getKind().name());
            if (tp.isIgnoreCase()) {
                token.append(" [IGNORE_CASE]");
            }
            token.append(" : {\n");
            for (Iterator<RegExprSpec> it2 = tp.getRespecs().iterator(); it2.hasNext(); ) {
                RegExprSpec res = it2.next();

                token.append(JJDoc.emitRE(res.rexp));

                if (res.nsTok != null) {
                    token.append(" : ").append(res.nsTok.image);
                }

                token.append("\n");
                if (it2.hasNext()) {
                    token.append("| ");
                }
            }
            token.append("}\n\n");
        }
        return token.toString();
    }

    private static void emitNormalProductions(Generator gen, Iterable<NormalProduction> prods) {
        gen.nonterminalsStart();
        for (NormalProduction np : prods) {
            if (np instanceof BNFProduction) {
                gen.productionStart(np);
                if (np.getExpansion() instanceof Choice c) {
                    boolean first = true;
                    for (Expansion element : c.getChoices()) {
                        gen.expansionStart(element, first);
                        JJDoc.emitExpansionTree(element, gen);
                        gen.expansionEnd(element, first);
                        first = false;
                    }
                } else {
                    gen.expansionStart(np.getExpansion(), true);
                    JJDoc.emitExpansionTree(np.getExpansion(), gen);
                    gen.expansionEnd(np.getExpansion(), true);
                }
                gen.productionEnd(np);
            }
        }
        gen.nonterminalsEnd();
    }

    private static void emitExpansionTree(Expansion exp, Generator gen) {
        // gen.text("[->" + exp.getClass().getName() + "]");
        switch (exp) {
            case Action action -> JJDoc.emitExpansionAction();
            case Choice choice -> JJDoc.emitExpansionChoice(choice, gen);
            case Lookahead lookahead -> JJDoc.emitExpansionLookahead();
            case NonTerminal nonTerminal -> JJDoc.emitExpansionNonTerminal(nonTerminal, gen);
            case OneOrMore oneOrMore -> JJDoc.emitExpansionOneOrMore(oneOrMore, gen);
            case RegularExpression regularExpression ->
                    JJDoc.emitExpansionRegularExpression((RExpression) regularExpression, gen);
            case Sequence sequence -> JJDoc.emitExpansionSequence(sequence, gen);
            case ZeroOrMore zeroOrMore -> JJDoc.emitExpansionZeroOrMore(zeroOrMore, gen);
            case ZeroOrOne zeroOrOne -> JJDoc.emitExpansionZeroOrOne(zeroOrOne, gen);
            case null, default -> JJDocGlobals.error("Oops: Unknown expansion type.");
        }
        // gen.text("[<-" + exp.getClass().getName() + "]");
    }

    private static void emitExpansionAction() {
    }

    private static void emitExpansionChoice(Choice c, Generator gen) {
        for (Iterator<Expansion> it = c.getChoices().iterator(); it.hasNext(); ) {
            Expansion e = it.next();
            JJDoc.emitExpansionTree(e, gen);
            if (it.hasNext()) {
                gen.text(" | ");
            }
        }
    }

    private static void emitExpansionLookahead() {
    }

    private static void emitExpansionNonTerminal(NonTerminal nt, Generator gen) {
        gen.nonTerminalStart(nt);
        gen.text(nt.getName());
        gen.nonTerminalEnd(nt);
    }

    private static void emitExpansionOneOrMore(OneOrMore o, Generator gen) {
        gen.text("( ");
        JJDoc.emitExpansionTree(o.getExpansion(), gen);
        gen.text(" )+");
    }

    private static void emitExpansionRegularExpression(RExpression r, Generator gen) {
        String reRendered = JJDoc.emitRE(r);
        if (!reRendered.isEmpty()) {
            gen.reStart(r);
            gen.text(reRendered);
            gen.reEnd(r);
        }
    }

    private static void emitExpansionSequence(Sequence s, Generator gen) {
        boolean firstUnit = true;
        for (Expansion e : s.getUnits()) {
            if ((e instanceof Lookahead) || (e instanceof Action)) {
                continue;
            }
            if (!firstUnit) {
                gen.text(" ");
            }
            boolean needParens = (e instanceof Choice) || (e instanceof Sequence);
            if (needParens) {
                gen.text("( ");
            }
            JJDoc.emitExpansionTree(e, gen);
            if (needParens) {
                gen.text(" )");
            }
            firstUnit = false;
        }
    }

    private static void emitExpansionZeroOrMore(ZeroOrMore z, Generator gen) {
        gen.text("( ");
        JJDoc.emitExpansionTree(z.getExpansion(), gen);
        gen.text(" )*");
    }

    private static void emitExpansionZeroOrOne(ZeroOrOne z, Generator gen) {
        gen.text("( ");
        JJDoc.emitExpansionTree(z.getExpansion(), gen);
        gen.text(" )?");
    }

    private static String emitRE(RExpression re) {
        StringBuilder returnString = new StringBuilder();
        boolean hasLabel = !re.getLabel().isEmpty();
        boolean justName = re instanceof RJustName;
        boolean eof = re instanceof REndOfFile;
        boolean isString = re instanceof RStringLiteral;
        boolean toplevelRE = (re.getTokenKind() != null);
        boolean needBrackets = justName || eof || hasLabel || (!isString && toplevelRE);
        if (needBrackets) {
            returnString.append("<");
            if (!justName) {
                if (re.isPrivateExp()) {
                    returnString.append("#");
                }
                if (hasLabel) {
                    returnString.append(re.getLabel());
                    returnString.append(": ");
                }
            }
        }
        switch (re) {
            case RCharacterList cl -> {
                if (cl.isNegated_list()) {
                    returnString.append("~");
                }
                returnString.append("[");
                for (Iterator<Object> it = cl.getDescriptors().iterator(); it.hasNext(); ) {
                    Object o = it.next();
                    if (o instanceof SingleCharacter c) {
                        returnString.append("\"");
                        char[] s = {c.getChar()};
                        returnString.append(Encoding.escape(new String(s)));
                        returnString.append("\"");
                    } else if (o instanceof CharacterRange range) {
                        returnString.append("\"");
                        char[] s = {range.getLeft()};
                        returnString.append(Encoding.escape(new String(s)));
                        returnString.append("\"-\"");
                        s[0] = range.getRight();
                        returnString.append(Encoding.escape(new String(s)));
                        returnString.append("\"");
                    } else {
                        JJDocGlobals.error("Oops: unknown character list element type.");
                    }
                    if (it.hasNext())
                        returnString.append(",");
                }
                returnString.append("]");
            }
            case RChoice c -> {
                for (Iterator<RExpression> it = c.getChoices().iterator(); it.hasNext(); ) {
                    RExpression sub = it.next();
                    returnString.append(JJDoc.emitRE(sub));
                    if (it.hasNext())
                        returnString.append(" | ");
                }
            }
            case REndOfFile rEndOfFile -> returnString.append("EOF");
            case RJustName jn -> returnString.append(jn.getLabel());
            case ROneOrMore om -> {
                returnString.append("(");
                returnString.append(JJDoc.emitRE(om.getRegexpr()));
                returnString.append(")+");
            }
            case RSequence s -> {
                for (Iterator<RExpression> it = s.getUnits().iterator(); it.hasNext(); ) {
                    RExpression sub = it.next();
                    boolean needParens = sub instanceof RChoice;
                    if (needParens) {
                        returnString.append("(");
                    }
                    returnString.append(JJDoc.emitRE(sub));
                    if (needParens) {
                        returnString.append(")");
                    }
                    if (it.hasNext()) {
                        returnString.append(" ");
                    }
                }
            }
            case RStringLiteral sl -> returnString.append("\"").append(Encoding.escape(sl.getImage())).append("\"");
            case RZeroOrMore zm -> {
                returnString.append("(");
                returnString.append(JJDoc.emitRE(zm.getRegexpr()));
                returnString.append(")*");
            }
            case RZeroOrOne zo -> {
                returnString.append("(");
                returnString.append(JJDoc.emitRE(zo.getRegexpr()));
                returnString.append(")?");
            }
            case RRepetitionRange zo -> {
                returnString.append("(");
                returnString.append(JJDoc.emitRE(zo.getRegexpr()));
                returnString.append(")");
                returnString.append("{");
                if (zo.hasMax()) {
                    returnString.append(zo.getMin());
                    returnString.append(",");
                    returnString.append(zo.getMax());
                } else {
                    returnString.append(zo.getMin());
                }
                returnString.append("}");
            }
            default -> JJDocGlobals.error("Oops: Unknown regular expression type.");
        }
        if (needBrackets) {
            returnString.append(">");
        }
        return returnString.toString();
    }
}
