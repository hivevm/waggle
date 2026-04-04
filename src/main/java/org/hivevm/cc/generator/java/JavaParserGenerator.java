// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.HiveCC;
import org.hivevm.cc.Language;
import org.hivevm.cc.generator.ParserData;
import org.hivevm.cc.generator.ParserGenerator;
import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.BNFProduction;
import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.Lookahead;
import org.hivevm.cc.model.NonTerminal;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.ZeroOrMore;
import org.hivevm.cc.model.ZeroOrOne;
import org.hivevm.cc.parser.Token;
import org.hivevm.cc.semantic.Semanticize;
import org.hivevm.source.SourceWriter;
import org.hivevm.source.Template;

/**
 * Implements the {@link ParserGenerator} for the JAVA language.
 */
class JavaParserGenerator extends ParserGenerator {

    @Override
    public void generate(ParserData data) {
        var options = Template.newContext(data.options());
        options.set(ParserGenerator.JJPARSER_USE_AST, data.isGenerated());
        options.set(ParserGenerator.LOOKAHEAD_NEEDED, data.isLookAheadNeeded());
        options.set(ParserGenerator.JJ2_INDEX, data.jj2Index());
        options.set(ParserGenerator.MASK_INDEX, data.maskIndex());
        options.set(ParserGenerator.TOKEN_COUNT, data.getTokenCount());
        options.add(HiveCC.JJPARSER_JAVA_IMPORTS, data.options().get(HiveCC.JJPARSER_JAVA_IMPORTS))
            .set(HiveCC.JJPARSER_JAVA_IMPORTS + "_VALUE", i -> i);

        options.add(ParserGenerator.JJ2_OFFSET, data.jj2Index())
            .set("JJ2_OFFSET_INDEX", i -> i)
            .set("JJ2_OFFSET_VALUE", i -> (i + 1));
        options.add(ParserGenerator.TOKEN_MASKS, ((data.getTokenCount() - 1) / 32) + 1)
            .set("TOKEN_MASKS_INDEX", i -> i)
            .set("TOKEN_MASKS_MASK",i -> data.maskVals().stream().map(v -> 
                    "0x" + Integer.toHexString(v[i])).collect(Collectors.joining(", ")));
        options.add(ParserGenerator.TOKEN_MASKS + "_LA1", ((data.getTokenCount() - 1) / 32) + 1)
            .set("TOKEN_MASKS_LA1_INDEX",i -> i)
            .set("TOKEN_MASKS_LA1_VALUE",i -> (i == 0) ? "" : (32 * i) + " + ");

        options.add("NORMALPRODUCTIONS", data.getProductions())
            .set("NORMALPRODUCTIONS_PHASE", (n, p) -> generatePhase1((BNFProduction) n, 
                generatePhase1Expansion(data, n.getExpansion()), p, data));
        options.add("LOOKAHEADS", data.getLoakaheads())
            .set("LOOKAHEADS_PHASE", (e, p) -> generatePhase2(e.getLaExpansion(), p, data));
        options.add("EXPANSIONS", data.getExpansions())
            .set("EXPANSIONS_PHASE", (e, p) -> generatePhase3Routine(data, e, data.getCount(e), p));

        JavaSources.PARSER.render(options);
    }

    @Override
    protected final Language getLanguage() {
        return Language.JAVA;
    }

    /**
     * The phase 1 routines generates their output into String's and dumps these String's once for
     * each method. These String's contain the special characters '\u0001' to indicate a positive
     * indent, and '\u0002' to indicate a negative indent. '\n' is used to indicate a line terminator.
     * The characters '\u0003' and '\u0004' are used to delineate portions of text where '\n's should
     * not be followed by an indentation.
     */
    private void generatePhase1(BNFProduction p, String code, SourceWriter writer, ParserData data) {
        var t = p.getReturnTypeToken();

        genHeaderMethod(p, writer);

        writer.append(" {");

        if (data.getDepthLimit() > 0) {
            writer.append("if(++jj_depth > " + data.getDepthLimit() + ") {").new_line();
            writer.append("  jj_consume_token(-1);").new_line();
            writer.append("  throw new ParseException();").new_line();
            writer.append("}").new_line();
            writer.append("try {").new_line();
        }

        int indentamt = 4;
        if (data.getDebugParser()) {
            writer.new_line();
            writer.append("    trace_call(\"" + Encoding.escapeUnicode(p.getLhs(), Language.JAVA) + "\");").new_line();
            writer.append("    try {").new_line();
            indentamt = 6;
        }

        if (!p.getDeclarationTokens().isEmpty()) {
            genTokenSetup((p.getDeclarationTokens().getFirst()));
            for (Token token : p.getDeclarationTokens()) {
                t = token;
                writer.append(getStringToPrint(t));
            }
            writer.append(getTrailingComments(t));
        }

        char ch = ' ';
        char prevChar;
        boolean indentOn = true;
        for (int i = 0; i < code.length(); i++) {
            prevChar = ch;
            ch = code.charAt(i);
            if ((ch == '\n') && (prevChar == '\r')) {
                // do nothing - we've already printed a new line for the '\r'
                // during the previous iteration.
            }
            else if ((ch == '\n') || (ch == '\r')) {
                if (indentOn) {
                    writer.new_line();
                    for (int i0 = 0; i0 < indentamt; i0++) {
                        writer.append(" ");
                    }
                }
                else {
                    writer.new_line();
                }
            }
            else if (ch == '\u0001') {
                indentamt += 2;
            }
            else if (ch == '\u0002') {
                indentamt -= 2;
            }
            else if (ch == '\u0003') {
                indentOn = false;
            }
            else if (ch == '\u0004') {
                indentOn = true;
            }
            else {
                writer.append("" + ch);
            }
        }
        writer.new_line();

        if (!p.getDeclarationEndTokens().isEmpty()) {
            genTokenSetup((p.getDeclarationEndTokens().getFirst()));
            for (Token token : p.getDeclarationEndTokens()) {
                t = token;
                writer.append(getStringToPrint(t));
            }
            writer.new_line();
        }

        if (data.getDebugParser()) {
            writer.append("    } finally {").new_line();
            writer.append("      trace_return(\"" + Encoding.escapeUnicode(p.getLhs(), Language.JAVA) + "\");").new_line();
            writer.append("    }").new_line();
        }
        if (data.getDepthLimit() > 0) {
            writer.append(" } finally {").new_line();
            writer.append("   --jj_depth;").new_line();
            writer.append(" }").new_line();
        }
        writer.append("}").new_line();
        writer.new_line();
    }

    private String generatePhase1Expansion(ParserData data, Expansion e) {
        String retval = "";
        Token t = null;
        if (e instanceof RExpression re) {
            retval += "\n";
            if (!re.getLhsTokens().isEmpty()) {
                genTokenSetup(re.getLhsTokens().getFirst());
                for (Token token : re.getLhsTokens()) {
                    t = token;
                    retval += getStringToPrint(t);
                }
                retval += getTrailingComments(t);
                retval += " = ";
            }
            String tail = re.getRhsToken() == null ? ");" : ")." + re.getRhsToken().image + ";";
            if (re.getLabel().isEmpty()) {
                String label = data.getNameOfToken(re.getOrdinal());
                retval += "jj_consume_token(" + Objects.requireNonNullElseGet(label, () -> re.getOrdinal())
                        + tail;
            }
            else {
                retval += "jj_consume_token(" + re.getLabel() + tail;
            }

        }
        else if (e instanceof NonTerminal e_nrw) {
            retval += "\n";
            if (!e_nrw.getLhsTokens().isEmpty()) {
                genTokenSetup((e_nrw.getLhsTokens().getFirst()));
                for (Token token : e_nrw.getLhsTokens()) {
                    t = token;
                    retval += getStringToPrint(t);
                }
                retval += getTrailingComments(t);
                retval += " = ";
            }
            retval += e_nrw.getName() + "(";
            if (!e_nrw.getArgumentTokens().isEmpty()) {
                genTokenSetup((e_nrw.getArgumentTokens().getFirst()));
                for (Token token : e_nrw.getArgumentTokens()) {
                    t = token;
                    retval += getStringToPrint(t);
                }
                retval += getTrailingComments(t);
            }
            retval += ");";
        }
        else if (e instanceof Action e_nrw) {
            retval += "\u0003\n";
            if (!e_nrw.getActionTokens().isEmpty()) {
                genTokenSetup((e_nrw.getActionTokens().getFirst()));
                for (Token token : e_nrw.getActionTokens()) {
                    t = token;
                    retval += getStringToPrint(t);
                }
                retval += getTrailingComments(t);
            }
            retval += "\u0004";
        }
        else if (e instanceof Choice e_nrw) {
            Lookahead[] conds = data.getLoakaheads(e);
            String[] actions = new String[e_nrw.getChoices().size() + 1];
            actions[e_nrw.getChoices().size()] = """
                    
                    jj_consume_token(-1);
                    throw new ParseException();""";

            // In previous line, the "throw" never throws an exception since the
            // evaluation of jj_consume_token(-1) causes ParseException to be
            // thrown first.
            Sequence nestedSeq;
            for (int i = 0; i < e_nrw.getChoices().size(); i++) {
                nestedSeq = (Sequence) (e_nrw.getChoices().get(i));
                actions[i] = generatePhase1Expansion(data, nestedSeq);
            }
            retval = genLookaheadChecker(data, conds, actions);
        }
        else if (e instanceof Sequence e_nrw) {
            // We skip the first element in the following iteration since it is the
            // Lookahead object.
            for (var exp : e_nrw.getUnits()) {
                // For C++, since we are not using exceptions, we will protect all the
                // expansion choices with if (!error)
                boolean wrap_in_block = false;
                retval += generatePhase1Expansion(data, (Expansion)exp);
                if (wrap_in_block) {
                    retval += "\n}";
                }
            }
        }
        else if (e instanceof OneOrMore e_nrw) {
            Expansion nested_e = e_nrw.getExpansion();
            retval += "\n";
            int labelIndex = nextLabelIndex();
            retval += "label_" + labelIndex + ":\n";
            retval += "while (true) {\u0001";
            retval += generatePhase1Expansion(data, nested_e);
            Lookahead[] conds = data.getLoakaheads(e);
            String[] actions = {"\n;", "\nbreak label_" + labelIndex + ";"};
            retval += genLookaheadChecker(data, conds, actions);
            retval += "\u0002\n" + "}";
        }
        else if (e instanceof ZeroOrMore e_nrw) {
            Expansion nested_e = e_nrw.getExpansion();
            retval += "\n";
            int labelIndex = nextLabelIndex();
            retval += "label_" + labelIndex + ":\n";
            retval += "while (true) {\u0001";
            Lookahead[] conds = data.getLoakaheads(e);
            String[] actions = {"\n;", "\nbreak label_" + labelIndex + ";"};
            retval += genLookaheadChecker(data, conds, actions);
            retval += generatePhase1Expansion(data, nested_e);
            retval += "\u0002\n" + "}";
        }
        else if (e instanceof ZeroOrOne e_nrw) {
            Expansion nested_e = e_nrw.getExpansion();
            Lookahead[] conds = data.getLoakaheads(e);
            String[] actions = {generatePhase1Expansion(data, nested_e), "\n;"};
            retval += genLookaheadChecker(data, conds, actions);
        }
        return retval;
    }

    /**
     * This method takes two parameters - an array of Lookahead's "conds", and an array of String's
     * "actions". "actions" contains exactly one element more than "conds". "actions" are Java source
     * code, and "conds" translate to conditions - so lets say "f(conds[i])" is true if the lookahead
     * required by "conds[i]" is indeed the case. This method returns a string corresponding to the
     * Java code for:
     * <p>
     * if (f(conds[0]) actions[0] else if (f(conds[1]) actions[1] . . . else actions[action.length-1]
     * <p>
     * A particular action entry ("actions[i]") can be null, in which case, a noop is generated for
     * that action.
     */
    private String genLookaheadChecker(ParserData data, Lookahead[] conds, String[] actions) {
        // The state variables.
        LookaheadState state = LookaheadState.NOOPENSTM;
        int indentAmt = 0;
        boolean[] casedValues = new boolean[data.getTokenCount()];
        String retval = "";
        Lookahead la = null;
        Token t = null;

        // Iterate over all the conditions.
        int index = 0;
        boolean jj2LA;
        while (index < conds.length) {

            la = conds[index];
            jj2LA = false;

            if ((la.getAmount() == 0) || Semanticize.emptyExpansionExists(la.getLaExpansion())) {
                // This handles the following cases:
                // . If syntactic lookahead is not wanted (and hence explicitly specified
                // as 0).
                // . If it is possible for the lookahead expansion to recognize the empty
                // string - in which case the lookahead trivially passes.
                // . If the lookahead expansion has a JAVACODE production that it directly
                // expands to - in which case the lookahead trivially passes.
                if (la.getActionTokens().isEmpty()) {
                    // In addition, if there is no semantic lookahead, then the
                    // lookahead trivially succeeds. So break the main loop and
                    // treat this case as the default last action.
                    break;
                }
                else {
                    // This case is when there is only semantic lookahead
                    // (without any preceding syntactic lookahead). In this
                    // case, an "if" statement is generated.
                    switch (state) {
                        case NOOPENSTM:
                            retval += "\n" + "if (";
                            indentAmt++;
                            break;
                        case OPENIF:
                            retval += "\u0002\n" + "} else if (";
                            break;
                        case OPENSWITCH:
                            retval += "\u0002\n" + "default:" + "\u0001";
                            if (data.getErrorReporting()) {
                                retval += "\njj_la1[" + data.getIndex(la) + "] = jj_gen;";
                            }
                            retval += "\n" + "if (";
                            indentAmt++;
                    }
                    genTokenSetup((la.getActionTokens().getFirst()));
                    for (Token token : la.getActionTokens()) {
                        t = token;
                        retval += getStringToPrint(t);
                    }
                    retval += getTrailingComments(t);
                    retval += ") {\u0001" + actions[index];
                    state = LookaheadState.OPENIF;
                }

            }
            else if ((la.getAmount() == 1) && (la.getActionTokens().isEmpty())) {
                // Special optimal processing when the lookahead is exactly 1, and there
                // is no semantic lookahead.
                boolean[] firstSet = new boolean[data.getTokenCount()];
                for (int i = 0; i < data.getTokenCount(); i++) {
                    firstSet[i] = false;
                }

                // jj2LA is set to false at the beginning of the containing "if" statement.
                // It is checked immediately after the end of the same statement to determine
                // if lookaheads are to be performed using calls to the jj2 methods.
                jj2LA = data.genFirstSet(la.getLaExpansion(), firstSet, jj2LA);
                // genFirstSet may find that semantic attributes are appropriate for the next
                // token. In which case, it sets jj2LA to true.
                if (!jj2LA) {
                    // This case is if there is no applicable semantic lookahead and the lookahead
                    // is one (excluding the earlier cases such as JAVACODE, etc.).
                    switch (state) {
                        case OPENIF:
                            retval += "\u0002\n" + "} else {\u0001";
                            //$FALL-THROUGH$ Control flows through to next case.
                        case NOOPENSTM:
                            retval += "\n" + "switch (";
                            if (data.getCacheTokens()) {
                                retval += "jj_nt.kind";
                                retval += ") {\u0001";
                            }
                            else {
                                retval += "(jj_ntk==-1)?jj_ntk_f():jj_ntk) {\u0001";
                            }
                            for (int i = 0; i < data.getTokenCount(); i++) {
                                casedValues[i] = false;
                            }
                            indentAmt++;
                            // Don't need to do anything if state is OPENSWITCH.
                        default:
                    }
                    for (int i = 0; i < data.getTokenCount(); i++) {
                        if (firstSet[i] && !casedValues[i]) {
                            casedValues[i] = true;
                            retval += "\u0002\ncase ";
                            String s = data.getNameOfToken(i);
                            if (s == null) {
                                retval += i;
                            }
                            else {
                                retval += s;
                            }
                            retval += ":\u0001";
                        }
                    }
                    retval += "{";
                    retval += actions[index];
                    retval += "\nbreak;\n}";
                    state = LookaheadState.OPENSWITCH;

                }

            }
            else {
                // This is the case when lookahead is determined through calls to
                // jj2 methods. The other case is when lookahead is 1, but semantic
                // attributes need to be evaluated. Hence this crazy control structure.

                jj2LA = true;
            }

            if (jj2LA) {
                // In this case lookahead is determined by the jj2 methods.

                switch (state) {
                    case NOOPENSTM:
                        retval += "\n" + "if (";
                        indentAmt++;
                        break;
                    case OPENIF:
                        retval += "\u0002\n" + "} else if (";
                        break;
                    case OPENSWITCH:
                        retval += "\u0002\n" + "default:" + "\u0001";
                        if (data.getErrorReporting()) {
                            retval += "\njj_la1[" + data.getIndex(la) + "] = jj_gen;";
                        }
                        retval += "\n" + "if (";
                        indentAmt++;
                }

                String amount = Integer.toString(la.getAmount());
                retval += "jj_2" + la.getLaExpansion().internalName() + "(" + amount + ")";
                if (!la.getActionTokens().isEmpty()) {
                    // In addition, there is also a semantic lookahead. So concatenate
                    // the semantic check with the syntactic one.
                    retval += " && (";
                    genTokenSetup((la.getActionTokens().getFirst()));
                    for (Token token : la.getActionTokens()) {
                        t = token;
                        retval += getStringToPrint(t);
                    }
                    retval += getTrailingComments(t);
                    retval += ")";
                }
                retval += ") {\u0001" + actions[index];
                state = LookaheadState.OPENIF;
            }

            index++;
        }

        // Generate code for the default case. Note this may not
        // be the last entry of "actions" if any condition can be
        // statically determined to be always "true".

        switch (state) {
            case NOOPENSTM:
                retval += actions[index];
                break;
            case OPENIF:
                retval += "\u0002\n" + "} else {\u0001" + actions[index];
                break;
            case OPENSWITCH:
                retval += "\u0002\n" + "default:" + "\u0001";
                if (data.getErrorReporting()) {
                    retval += "\njj_la1[" + data.getIndex(la) + "] = jj_gen;";
                }
                retval += actions[index];
        }
        for (int i = 0; i < indentAmt; i++) {
            retval += "\u0002\n}";
        }
        return retval;
    }

    private void genHeaderMethod(BNFProduction p, SourceWriter writer) {
        Token t = p.getFirstToken();
        genTokenSetup(t);
        writer.append(getLeadingComments(t));
        writer.append("  public final ");
        if (p.getReturnTypeToken() != null) {
            writer.append(getStringForTokenOnly(p.getReturnTypeToken()));
        } else {
            writer.append("void");
        }
        writer.append(getTrailingComments(t));
        writer.append(" " + p.getLhs() + "(");
        if (!p.getParameterListTokens().isEmpty()) {
            genTokenSetup((p.getParameterListTokens().getFirst()));
            for (Token token : p.getParameterListTokens()) {
                t = token;
                writer.append(getStringToPrint(t));
            }
            writer.append(getTrailingComments(t));
        }
        writer.append(") throws ParseException");

        for (List<Token> name : p.getThrowsList()) {
            writer.append(", ");
            for (Token token : name) {
                t = token;
                writer.append(t.image);
            }
        }
    }

    private void generatePhase2(Expansion e, SourceWriter writer, ParserData data) {
        writer.append("  private boolean jj_2" + e.internalName() + "(int xla) {").new_line();
        writer.append("    jj_la = xla;").new_line();
        writer.append("    jj_lastpos = jj_scanpos = token;").new_line();

        String ret_suffix = (data.getDepthLimit() > 0) ? " && !jj_depth_error" : "";
        writer.append("    try {").new_line();
        writer.append("      return (!jj_3" + e.internalName() + "()" + ret_suffix + ");").new_line();
        writer.append("    } catch (LookaheadSuccess ls) {").new_line();
        writer.append("      return true;").new_line();
        if (data.getErrorReporting()) {
            writer.append("    } finally {").new_line();
            writer.append("      jj_save(" + (Integer.parseInt(e.internalName().substring(1)) - 1) + ", xla);").new_line();
        }
        writer.append("    }").new_line();
        writer.append("  }").new_line();
        writer.new_line();
    }

    private void generatePhase3Routine(ParserData data, Expansion e, int count, SourceWriter writer) {
        if (e.internalName().startsWith("jj_scan_token"))
            return;

        writer.append("  private boolean jj_3" + e.internalName() + "() {").new_line();

        if (data.getDepthLimit() > 0) {
            writer.append("if(++jj_depth > " + data.getDepthLimit() + ") {").new_line();
            writer.append("  jj_consume_token(-1);").new_line();
            writer.append("  throw new ParseException();").new_line();
            writer.append("}").new_line();
            writer.append("try {").new_line();
        }

        boolean xsp_declared = false;
        Expansion jj3_expansion = null;
        if (data.getDebugLookahead() && (e.parent() instanceof NormalProduction np)) {
            writer.append("    ");
            if (data.getErrorReporting()) {
                writer.append("if (!jj_rescan) ");
            }
            writer.append("trace_call(\"" + Encoding.escapeUnicode(np.getLhs(), Language.JAVA) + "(LOOKING AHEAD...)\");").new_line();
            jj3_expansion = e;
        }

        buildPhase3RoutineRecursive(data, jj3_expansion, xsp_declared, e, count, writer);

        writer.append("    " + genReturn(jj3_expansion, false, data)).new_line();
        if (data.getDepthLimit() > 0) {
            writer.append(" } finally {").new_line();
            writer.append("   --jj_depth;").new_line();
            writer.append(" }").new_line();
        }
        writer.append("  }").new_line();
        writer.new_line();
    }

    private boolean buildPhase3RoutineRecursive(ParserData data, Expansion jj3_expansion,
                                                boolean xsp_declared,
                                                Expansion e, int count, SourceWriter writer) {
        if (e.internalName().startsWith("jj_scan_token")) {
            return xsp_declared;
        }

        switch (e) {
            case RExpression e_nrw -> {
                writer.append("    if (jj_scan_token(");
                if (e_nrw.getLabel().isEmpty()) {
                    Object label = data.getNameOfToken(e_nrw.getOrdinal());
                    writer.append((label == null)
                        ? "" + e_nrw.getOrdinal()
                        : "ParserConstants." + label);
                }
                else {
                    writer.append("ParserConstants." + e_nrw.getLabel());
                }
                writer.append("))").new_line();
                writer.append("      " + genReturn(jj3_expansion, true, data)).new_line();
            }
            case NonTerminal e_nrw -> {
                // All expansions of non-terminals have the "name" fields set. So
                // there's no need to check it below for "e_nrw" and "ntexp". In
                // fact, we rely here on the fact that the "name" fields of both these
                // variables are the same.
                NormalProduction ntprod = data.getProduction(e_nrw.getName());
                Expansion ntexp = ntprod.getExpansion();
                writer.append("    if (" + genjj_3Call(ntexp) + ")").new_line();
                writer.append("      " + genReturn(jj3_expansion, true, data)).new_line();
            }
            case Choice e_nrw -> {
                Sequence nested_seq;
                if (e_nrw.getChoices().size() != 1) {
                    if (!xsp_declared) {
                        xsp_declared = true;
                        writer.append("    Token xsp;").new_line();
                    }
                    writer.append("    xsp = jj_scanpos;").new_line();
                }

                Token t = null;
                for (int i = 0; i < e_nrw.getChoices().size(); i++) {
                    nested_seq = (Sequence) (e_nrw.getChoices().get(i));
                    Lookahead la = (Lookahead) (nested_seq.getUnits().getFirst());
                    if (!la.getActionTokens().isEmpty()) {
                        writer.append("    jj_lookingAhead = true;").new_line();
                        writer.append("    jj_semLA = ");
                        genTokenSetup((la.getActionTokens().getFirst()));
                        for (Token token : la.getActionTokens()) {
                            t = token;
                            writer.append(getStringToPrint(t));
                        }
                        writer.append(getTrailingComments(t));
                        writer.append(";").new_line();
                        writer.append("    jj_lookingAhead = false;").new_line();
                    }
                    writer.append("    if (");
                    if (!la.getActionTokens().isEmpty()) {
                        writer.append("!jj_semLA || ");
                    }
                    if (i != (e_nrw.getChoices().size() - 1)) {
                        writer.append(genjj_3Call(nested_seq) + ") {").new_line();
                        writer.append("      jj_scanpos = xsp;").new_line();
                    }
                    else {
                        writer.append(genjj_3Call(nested_seq) + ")").new_line();
                        writer.append("      " + genReturn(jj3_expansion, true, data)).new_line();
                    }
                }
                for (int i = 1; i < e_nrw.getChoices().size(); i++) {
                    writer.append("    }").new_line();
                }
            }
            case Sequence e_nrw -> {
                // We skip the first element in the following iteration since it is the
                // Lookahead object.
                int cnt = count;
                for (int i = 1; i < e_nrw.getUnits().size(); i++) {
                    Expansion eseq = (Expansion) (e_nrw.getUnits().get(i));
                    xsp_declared = buildPhase3RoutineRecursive(data, jj3_expansion, xsp_declared, eseq, cnt,
                            writer);
                    cnt -= data.minimumSize(eseq);
                    if (cnt <= 0) {
                        break;
                    }
                }
            }
            case OneOrMore e_nrw -> {
                if (!xsp_declared) {
                    xsp_declared = true;
                    writer.append("    Token xsp;").new_line();
                }
                Expansion nested_e = e_nrw.getExpansion();
                writer.append("    if (" + genjj_3Call(nested_e) + ") " + genReturn(jj3_expansion, true, data)).new_line();
                writer.append("    while (true) {").new_line();
                writer.append("      xsp = jj_scanpos;").new_line();
                writer.append("      if (" + genjj_3Call(nested_e) + ") { jj_scanpos = xsp; break; }").new_line();
                writer.append("    }").new_line();
            }
            case ZeroOrMore e_nrw -> {
                if (!xsp_declared) {
                    xsp_declared = true;
                    writer.append("    Token xsp;").new_line();
                }
                Expansion nested_e = e_nrw.getExpansion();
                writer.append("    while (true) {").new_line();
                writer.append("      xsp = jj_scanpos;").new_line();
                writer.append("      if (" + genjj_3Call(nested_e) + ") { jj_scanpos = xsp; break; }").new_line();
                writer.append("    }").new_line();
            }
            case ZeroOrOne e_nrw -> {
                if (!xsp_declared) {
                    xsp_declared = true;
                    writer.append("    Token xsp;").new_line();
                }
                Expansion nested_e = e_nrw.getExpansion();
                writer.append("    xsp = jj_scanpos;").new_line();
                writer.append("    if (" + genjj_3Call(nested_e) + ") jj_scanpos = xsp;").new_line();
            }
            default -> {
            }
        }
        return xsp_declared;
    }


    private String genReturn(Expansion expansion, boolean value, ParserData data) {
        String retval = value ? "true" : "false";
        if (data.getDebugLookahead() && (expansion != null)) {
            String tracecode =
                    "trace_return(\"" + Encoding.escapeUnicode(
                            ((NormalProduction) expansion.parent()).getLhs(), Language.JAVA)
                            + "(LOOKAHEAD " + (value ? "FAILED" : "SUCCEEDED") + ")\");";
            if (data.getErrorReporting()) {
                tracecode = "if (!jj_rescan) " + tracecode;
            }
            return "{ " + tracecode + " return " + retval + "; }";
        }
        else {
            return "return " + retval + ";";
        }
    }

    private String genjj_3Call(Expansion e) {
        var name = e.internalName();
        return name.startsWith("jj_scan_token") ? name : "jj_3" + name + "()";
    }
}
