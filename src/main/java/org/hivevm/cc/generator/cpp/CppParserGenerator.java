// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.Language;
import org.hivevm.cc.generator.ParserData;
import org.hivevm.cc.generator.ParserGenerator;
import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.Lookahead;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.model.NonTerminal;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.ZeroOrMore;
import org.hivevm.cc.model.ZeroOrOne;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.ParserConstants;
import org.hivevm.cc.parser.Token;
import org.hivevm.source.Context;
import org.hivevm.source.LinePrinter;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Implements the {@link ParserGenerator} for the C++ language.
 */
class CppParserGenerator extends ParserGenerator {

    public CppParserGenerator() {
        super(Language.CPP);
    }

    @Override
    protected final void generate(ParserData data, Context options) {
        options.set("DUMP_NORMALPRODUCTIONS_IMPL", w ->
                data.getProductions().forEach(n -> w.println("void " + n.getLhs() + "();")));

        CppTemplate.PARSER.render(options, data.getParserName());
        CppTemplate.PARSER_H.render(options, data.getParserName());
    }

    @Override
    protected String generate_phase1_head(NormalProduction p, LinePrinter printer, ParserData data) {
        Token t = p.getFirstToken();

        boolean void_ret = false;
        boolean ptr_ret = false;

        setup_token(t);
        printLeadingComments(printer, t);
        if (p.getReturnTypeToken() != null) {
            printer.print(t.image);
            if (t.kind == ParserConstants.STAR) {
                ptr_ret = true;
            }
        } else {
            printer.print("void");
            void_ret = true;
        }
        printTrailingComments(printer, t);
        printer.print(" " + data.getParserName() + "::" + p.getLhs() + "(");
        if (!p.getParameterListTokens().isEmpty()) {
            setup_token((p.getParameterListTokens().getFirst()));
            for (Token token : p.getParameterListTokens()) {
                printToken(token, printer);
            }
            printTrailingComments(printer, p.getParameterListTokens().getLast());
        }
        printer.print(")");

        // Generate a default value for error return.
        String default_return;
        if (ptr_ret) {
            default_return = "NULL";
        } else if (void_ret) {
            default_return = "";
        } else {
            default_return = "0"; // 0 converts to most (all?) basic types.
        }

        printer.print(" {");
        return default_return;
    }

    /**
     * The phase 1 routines generates their output into String's and dumps these String's once for
     * each method. These String's contain the special characters '\u0001' to indicate a positive
     * indent, and '\u0002' to indicate a negative indent. '\n' is used to indicate a line
     * terminator.
     */
    @Override
    protected void generate_phase1_body(NormalProduction p, LinePrinter printer, ParserData data, String default_return, Consumer<LinePrinter> consumer) {
        boolean hasReturnErr = false;
        boolean voidReturn = (p.getReturnTypeToken() == null);
        if ((data.getDepthLimit() > 0) && !voidReturn) {
            String method_name = p.getLhs();
            printer.println("\n#if !defined ERROR_RET_" + method_name);
            printer.println("#define ERROR_RET_" + method_name + " " + default_return);
            printer.println("#endif");
            printer.println("#define __ERROR_RET__ ERROR_RET_" + method_name);
            hasReturnErr = true;
        }

        if (data.getDebugParser()) {
            printer.println();
            printer.println("    JJEnter<std::function<void()>> jjenter([this]() {trace_call  (\""
                    + Encoding.escapeUnicode(p.getLhs(), Language.CPP) + "\"); });");
            printer.println("    JJExit <std::function<void()>> jjexit ([this]() {trace_return(\""
                    + Encoding.escapeUnicode(p.getLhs(), Language.CPP) + "\"); });");
            printer.println("    try {");
        }

        consumer.accept(printer);

        if (data.getDebugParser()) {
            printer.println();
            printer.outdent();
            printer.println("} catch(...) {}");
        }
        if (!voidReturn) {
            printer.println("assert(false);");
        }

        if (hasReturnErr) {
            printer.println("\n#undef __ERROR_RET__");
        }
    }

    @Override
    protected void generate_phase1_regexp(LinePrinter printer) {
        printer.print("jj_consume_token(");
    }

    @Override
    protected void generate_phase1_regexp_end(RExpression re, LinePrinter printer) {
        printer.print(re.getRhsToken() == null ? ");" : ")->" + re.getRhsToken().image + ";");
    }

    @Override
    protected final void generate_phase1_choice(LinePrinter printer) {
        printer.println("\njj_consume_token(-1);");
        printer.print("errorHandler->parseError(token, getToken(1), __FUNCTION__), hasError = true;");
    }


    @Override
    protected final void generate_phase1_nonterminal(NonTerminal non, LinePrinter printer) {
        printer.print(non.getName());
        printer.print("(");
    }

    @Override
    protected final void generate_phase1_nonterminal_end(LinePrinter printer) {
        printer.print(");");
    }

    @Override
    protected final void generate_phase1_more(int labelIndex, LinePrinter printer) {
        printer.print("while (!hasError) {");
        printer.indent();
    }

    @Override
    protected final void print_phase1_more_end(int labelIndex, LinePrinter printer, int offset) {
        if (offset == 0) {
            printer.print("\n;");
        } else {
            printer.print("\ngoto end_label_" + labelIndex + ";");
        }
    }

    @Override
    protected final void generate_phase1_more_end(int labelIndex, LinePrinter printer) {
        printer.print("\nend_label_" + labelIndex + ": ;");
    }

    @Override
    protected final void print_lookahead_amount0(LinePrinter printer, LookaheadState state, Consumer<LinePrinter> action, Lookahead la, NodeScope scope, int index) {
        switch (state) {
            case NOOPENSTM:
                printer.print("if (");
                break;
            case OPENIF:
                printer.println();
                printer.outdent();
                printer.print("} else if (");
                break;
            case OPENSWITCH:
                printer.println("default: {");
                printer.indent();
                if (index >= 0) {
                    printer.println("jj_la1[" + index + "] = jj_gen;");
                }
                printer.print("if (");
        }
        setup_token(la.getActionTokens().getFirst());
        la.getActionTokens().forEach(t -> printToken(t, scope, printer));
        printTrailingComments(printer, la.getActionTokens().getLast());
        printer.print(") {");
        printer.indent();
        action.accept(printer);
    }

    @Override
    protected final void print_lookahead_amount1(LinePrinter printer, LookaheadState state, Consumer<LinePrinter> action
            , boolean cache_tokens, List<String> cases
    ) {
        switch (state) {
            case OPENIF:
                printer.println();
                printer.outdent();
                printer.print("} else {");
                printer.indent();
                //$FALL-THROUGH$ Control flows through to next case.
            case NOOPENSTM:
                printer.println();
                printer.print("switch (");
                if (cache_tokens) {
                    printer.print("jj_nt->kind()");
                    printer.print(") {");
                    printer.indent();
                } else {
                    printer.print("(jj_ntk == -1) ? jj_ntk_f() : jj_ntk) {");
                    printer.indent();
                }
                // Don't need to do anything if state is OPENSWITCH.
            default:
        }

        for (var c : cases) {
            printer.println();
            printer.print("case ");
            printer.print(c);
            printer.print(":");
        }

        printer.print(" {");
        printer.indent();
        action.accept(printer);
        printer.println("break;");
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected final void print_lookahead(LinePrinter printer, LookaheadState state, Consumer<LinePrinter> action, Lookahead la, NodeScope scope, int index) {
        switch (state) {
            case NOOPENSTM:
                printer.println();
                printer.print("if (");
                break;
            case OPENIF:
                printer.println();
                printer.outdent();
                printer.print("} else if (");
                break;
            case OPENSWITCH:
                printer.println("default: {");
                printer.indent();
                if (index >= 0) {
                    printer.println("jj_la1[" + index + "] = jj_gen;");
                }
                printer.print("if (");
        }

        String amount = Integer.toString(la.getAmount());
        if (la.getAmount() == Integer.MAX_VALUE) {
            amount = "INT_MAX";
        }
        printer.print("jj_2" + la.getLaExpansion().internalName() + "(" + amount + ")");
        if (!la.getActionTokens().isEmpty()) {
            // In addition, there is also a semantic lookahead. So concatenate
            // the semantic check with the syntactic one.
            printer.print(" && (");
            setup_token(la.getActionTokens().getFirst());
            la.getActionTokens().forEach(t -> printToken(t, scope, printer));
            printTrailingComments(printer, la.getActionTokens().getLast());
            printer.print(")");
        }
        printer.println(") {");
        printer.indent();
        action.accept(printer);
    }

    @Override
    protected final void print_lookahead_tail(LinePrinter printer, LookaheadState state, Consumer<LinePrinter> action, int indents, int index) {
        // Generate code for the default case. Note this may not
        // be the last entry of "actions" if any condition can be
        // statically determined to be always "true".
        switch (state) {
            case NOOPENSTM:
                action.accept(printer);
                break;
            case OPENIF:
                printer.println();
                printer.outdent();
                printer.print("} else {");
                printer.indent();
                action.accept(printer);
                break;
            case OPENSWITCH:
                printer.println("default: {");
                printer.indent();
                if (index >= 0) {
                    printer.print("jj_la1[" + index + "] = jj_gen;");
                }
                action.accept(printer);
        }

        for (int i = 0; i < indents; i++) {
            printer.println();
            printer.outdent();
            printer.print("}");
        }
    }

    protected void generate_phase2(Expansion e, LinePrinter printer, ParserData data) {
        printer.println("  inline bool jj_2" + e.internalName() + "(int xla) {");
        printer.println("    jj_la = xla; jj_lastpos = jj_scanpos = token;");

        String ret_suffix = "";
        if (data.getDepthLimit() > 0) {
            ret_suffix = " && !jj_depth_error";
        }

        printer.println("    jj_done = false;");
        printer.println("    return (!jj_3" + e.internalName() + "() || jj_done)" + ret_suffix + ";");
        if (data.getErrorReporting()) {
            printer.println("    { jj_save(" + (Integer.parseInt(e.internalName().substring(1)) - 1) + ", xla); }");
        }
        printer.println("  }");
        printer.println();
    }

    protected void generate_phase3_routine(ParserData data, Expansion e, int count, LinePrinter printer) {
        if (e.internalName().startsWith("jj_scan_token"))
            return;

        printer.println(" inline bool jj_3" + e.internalName() + "()");
        printer.println(" {\n");
        printer.println("    if (jj_done) return true;");
        if (data.getDepthLimit() > 0) {
            printer.println("#define __ERROR_RET__ true");
        }

        boolean xsp_declared = false;
        Expansion jj3_expansion = null;
        if (data.getDebugLookahead() && (e.parent() instanceof NormalProduction np)) {
            String prefix = "    ";
            if (data.getErrorReporting())
                prefix += "if (!jj_rescan) ";
            printer.println(prefix + "trace_call(\"" + Encoding.escapeUnicode(np.getLhs(), Language.CPP)
                    + "(LOOKING AHEAD...)\");");
            jj3_expansion = e;
        }

        buildPhase3RoutineRecursive(data, jj3_expansion, xsp_declared, e, count, printer);

        printer.println("    " + genReturn(jj3_expansion, false, data));
        if (data.getDepthLimit() > 0) {
            printer.println("#undef __ERROR_RET__");
        }
        printer.println("  }");
        printer.println();
    }

    private boolean buildPhase3RoutineRecursive(ParserData data, Expansion jj3_expansion,
                                                boolean xsp_declared,
                                                Expansion e, int count, LinePrinter printer) {
        if (e.internalName().startsWith("jj_scan_token")) {
            return xsp_declared;
        }

        switch (e) {
            case RExpression e_nrw -> {
                if (e_nrw.getLabel().isEmpty()) {
                    String label = data.getNameOfToken(e_nrw.getOrdinal());
                    printer.println("    if (jj_scan_token(" + Objects.requireNonNullElseGet(label,
                            () -> e_nrw.getOrdinal()) + ")) " + genReturn(jj3_expansion, true, data));
                } else {
                    printer.println(
                            "    if (jj_scan_token(" + e_nrw.getLabel() + ")) " + genReturn(
                                    jj3_expansion, true,
                                    data));
                }
            }
            case NonTerminal e_nrw -> {
                // All expansions of non-terminals have the "name" fields set. So
                // there's no need to check it below for "e_nrw" and "ntexp". In
                // fact, we rely here on the fact that the "name" fields of both these
                // variables are the same.
                NormalProduction ntprod = data.getProduction(e_nrw.getName());
                Expansion ntexp = ntprod.getExpansion();
                printer.println("    if (" + genjj_3Call(ntexp) + ") " + genReturn(jj3_expansion, true, data));
            }
            case Choice e_nrw -> {
                Sequence nested_seq;
                if (e_nrw.getChoices().size() != 1) {
                    if (!xsp_declared) {
                        xsp_declared = true;
                        printer.println("    Token* xsp;");
                    }
                    printer.println("    xsp = jj_scanpos;");
                }

                for (int i = 0; i < e_nrw.getChoices().size(); i++) {
                    nested_seq = (Sequence) e_nrw.getChoices().get(i);
                    Lookahead la = (Lookahead) nested_seq.getUnits().getFirst();
                    if (!la.getActionTokens().isEmpty()) {
                        printer.println("    jj_lookingAhead = true;");
                        printer.print("    jj_semLA = ");
                        setup_token((la.getActionTokens().getFirst()));
                        for (Token token : la.getActionTokens()) {
                            printToken(token, printer);
                        }
                        printTrailingComments(printer, la.getActionTokens().getLast());
                        printer.println(";");
                        printer.println("    jj_lookingAhead = false;");
                    }
                    printer.print("    if (");
                    if (!la.getActionTokens().isEmpty()) {
                        printer.print("!jj_semLA || ");
                    }
                    printer.print(genjj_3Call(nested_seq) + ") ");
                    if (i != (e_nrw.getChoices().size() - 1)) {
                        printer.println("{\n    jj_scanpos = xsp;");
                    } else {
                        printer.println(genReturn(jj3_expansion, true, data));
                    }
                }
                for (int i = 1; i < e_nrw.getChoices().size(); i++) {
                    printer.println("    }");
                }
            }
            case Sequence e_nrw -> {
                // We skip the first element in the following iteration since it is the
                // Lookahead object.
                int cnt = count;
                for (int i = 1; i < e_nrw.getUnits().size(); i++) {
                    Expansion eseq = e_nrw.getUnits().get(i);
                    xsp_declared = buildPhase3RoutineRecursive(data, jj3_expansion, xsp_declared, eseq, cnt, printer);
                    cnt -= data.minimumSize(eseq);
                    if (cnt <= 0) {
                        break;
                    }
                }
            }
            case OneOrMore e_nrw -> {
                if (!xsp_declared) {
                    xsp_declared = true;
                    printer.println("    Token* xsp;");
                }
                Expansion nested_e = e_nrw.getExpansion();
                printer.println("    if (" + genjj_3Call(nested_e) + ") " + genReturn(jj3_expansion, true, data));
                printer.println("    while (true) {");
                printer.println("      xsp = jj_scanpos;");
                printer.println("      if (" + genjj_3Call(nested_e) + ") { jj_scanpos = xsp; break; }");
                printer.println("    }");
            }
            case ZeroOrMore e_nrw -> {
                if (!xsp_declared) {
                    xsp_declared = true;
                    printer.println("    Token* xsp;");
                }
                Expansion nested_e = e_nrw.getExpansion();
                printer.println("    while (true) {");
                printer.println("      xsp = jj_scanpos;");
                printer.println("      if (" + genjj_3Call(nested_e) + ") { jj_scanpos = xsp; break; }");
                printer.println("    }");
            }
            case ZeroOrOne e_nrw -> {
                if (!xsp_declared) {
                    xsp_declared = true;
                    printer.println("    Token* xsp;");
                }
                Expansion nested_e = e_nrw.getExpansion();
                printer.println("    xsp = jj_scanpos;");
                printer.println("    if (" + genjj_3Call(nested_e) + ") jj_scanpos = xsp;");
            }
            default -> {
            }
        }
        return xsp_declared;
    }


    private String genReturn(Expansion expansion, boolean value, ParserData data) {
        String retval = Boolean.toString(value);
        if (data.getDebugLookahead() && (expansion != null)) {
            String tracecode =
                    "trace_return(\"" + Encoding.escapeUnicode(
                            ((NormalProduction) expansion.parent()).getLhs(), Language.CPP)
                            + "(LOOKAHEAD " + (value ? "FAILED" : "SUCCEEDED") + ")\");";
            if (data.getErrorReporting()) {
                tracecode = "if (!jj_rescan) " + tracecode;
            }
            return "{ " + tracecode + " return " + retval + "; }";
        } else {
            return "return " + retval + ";";
        }
    }

    private String genjj_3Call(Expansion e) {
        var name = e.internalName();
        return name.startsWith("jj_scan_token") ? name : "jj_3" + name + "()";
    }

    @Override
    public final void insertOpenNodeCode(NodeScope ns, String nodeClass, LinePrinter printer, Options options) {
        printer.print(nodeClass + " *" + ns.getNodeVariable() + " = ");
        if (options.getNodeFactory().equals("*")) {
            // Old-style multiple-implementations.
            printer.println("(" + nodeClass + "*)" + nodeClass + "::jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        } else if (!options.getNodeFactory().isEmpty()) {
            printer.println("(" + nodeClass + "*)"
                    + options.getNodeFactory() + "->jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        } else {
            printer.println("new " + nodeClass + "(" + ns.getNodeDescriptor().getNodeId() + ");");
        }

        printer.println("bool " + ns.getClosedVariable() + " = true;");

        printer.println(ns.getNodeDescriptor().openNode(ns.getNodeVariable()));
        if (options.getNodeScopeHook())
            printer.println("jjtreeOpenNodeScope(" + ns.getNodeVariable() + ");");

        if (options.getTrackTokens()) {
            printer.println(ns.getNodeVariable() + "->jjtSetFirstToken(getToken(1));");
        }
        printer.print("try {");
    }

    @Override
    public final void insertCloseNodeCode(NodeScope ns, LinePrinter printer, Options options, boolean isFinal) {
        printer.println(ns.getNodeDescriptor().closeNode(ns.getNodeVariable()));
        if (!isFinal) {
            printer.println(ns.getClosedVariable() + " = false;");
        }
        if (options.getNodeScopeHook()) {
            printer.println("if (jjtree.nodeCreated()) {");
            printer.println(" jjtreeCloseNodeScope(" + ns.getNodeVariable() + ");");
            printer.println("}");
        }

        if (options.getTrackTokens()) {
            printer.println(ns.getNodeVariable() + "->jjtSetLastToken(getToken(0));");
        }
    }

    @Override
    public final void insertCatchBlocks(NodeScope ns, LinePrinter printer, Options options, Collection<String> thrown_names) {
        printer.println("} catch (...) {"); // " + ns.exceptionVar + ") {");
        printer.println("  if (" + ns.getClosedVariable() + ") {");
        printer.println("    jjtree.clearNodeScope(" + ns.getNodeVariable() + ");");
        printer.println("    " + ns.getClosedVariable() + " = false;");
        printer.println("  } else {");
        printer.println("    jjtree.popNode();");
        printer.println("  }");

        printer.println("} {");
        printer.println("  if (" + ns.getClosedVariable() + ") {");
        insertCloseNodeCode(ns, printer, options, true);
        printer.println("  }");
        printer.print("}");
    }
}
