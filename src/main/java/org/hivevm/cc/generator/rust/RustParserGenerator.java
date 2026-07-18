// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.GenerationException;
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
import org.hivevm.cc.parser.Token;
import org.hivevm.source.Context;
import org.hivevm.source.LinePrinter;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Implements the {@link ParserGenerator} for the RUST language.
 */
class RustParserGenerator extends ParserGenerator {

    public RustParserGenerator() {
        super(Language.RUST);
    }

    @Override
    protected final void generate(ParserData data, Context options) {
        if (data.getDepthLimit() > 0) {
            // The Rust back end has no depth-limit support yet: the template lacks the
            // jj_depth_error flag and jj_depth is a u32 (so the -1 sentinel would not compile).
            // Fail honestly instead of emitting Rust that cannot compile (SPECIFICATION.md §3:
            // target feature gaps are tracked, not silently produced).
            throw new GenerationException(
                    "DEPTH_LIMIT is not supported for the Rust target.");
        }

        options.add(ParserGenerator.TOKEN_MASKS + "_LA1", ((data.getTokenCount() - 1) / 32) + 1)
                .set("LA1_SUFFIX", i -> "_" + i)
                .set("LA1_MASK", i -> (i == 0) ? "" : (32 * i) + " + ");

        RustTemplate.PARSER.render(options);
    }

    protected String getStringIndex(int i) {
        return "_" + i;
    }

    @Override
    protected String generate_phase1_head(NormalProduction p, LinePrinter printer, ParserData data) {
        Token t = p.getFirstToken();
        setup_token(t);
        printLeadingComments(printer, t);
        printer.println();
        printer.print("pub fn ");
        printTrailingComments(printer, t);
        printer.print(normal_production_as_snake_case(p) + "(");
        printer.print("&mut self");
        if (!p.getParameterListTokens().isEmpty()) {
            setup_token((p.getParameterListTokens().getFirst()));
            for (Token token : p.getParameterListTokens()) {
                printToken(token, printer);
            }
            printTrailingComments(printer, p.getParameterListTokens().getLast());
        }
        printer.print(") -> Result<(), std::io::Error> /* throws ParseException */");

        for (List<Token> name : p.getThrowsList()) {
            printer.print(", ");
            for (Token token : name) {
                t = token;
                printer.print(t.image);
            }
        }

        printer.println(" {");
        printer.print("    let mut try_catch: Result<(), std::io::Error> = Ok(());");
        return null;
    }

    protected final void generate_phase1_tail(LinePrinter printer) {
        printer.println("    try_catch");
        super.generate_phase1_tail(printer);
    }

    /**
     * The phase 1 routines generates their output into String's and dumps these String's once for
     * each method. These String's contain the special characters '\u0001' to indicate a positive
     * indent, and '\u0002' to indicate a negative indent. '\n' is used to indicate a line
     * terminator.
     */
    @Override
    protected void generate_phase1_body(NormalProduction p, LinePrinter printer, ParserData data, String returnType, Consumer<LinePrinter> consumer) {
        // DEPTH_LIMIT is rejected up front in generate(); the Rust back end never emits guard code.
        if (data.getDebugParser()) {
            printer.println();
            printer.println("    trace_call(\"" + Encoding.escapeUnicode(normal_production_as_snake_case(p), Language.JAVA) + "\");");
            printer.println("    try {");
        }

        consumer.accept(printer);

        if (data.getDebugParser()) {
            printer.println("    } finally {");
            printer.println("        trace_return(\"" + Encoding.escapeUnicode(
                    normal_production_as_snake_case(p), Language.JAVA) + "\");");
            printer.println("    }");
        }
    }

    @Override
    protected void generate_phase1_regexp(LinePrinter printer) {
        printer.print("try_catch = self.jj_consume_token(");
    }

    @Override
    protected void generate_phase1_regexp_end(RExpression re, LinePrinter printer) {
        printer.print(re.getRhsToken() == null ? ");" : ")." + re.getRhsToken().image + ";");
    }

    @Override
    protected final void generate_phase1_choice(LinePrinter printer) {
        printer.print("""
                
                    let _ = self.jj_consume_token(u32::MAX);
                    return Err(std::io::Error::new(
                        std::io::ErrorKind::Other,
                        "ParseException",
                    ));
                """);
    }


    @Override
    protected final void generate_phase1_nonterminal(NonTerminal non, LinePrinter printer) {
        printer.println("if try_catch.is_ok() {");
        printer.print("    try_catch = self." + to_snake_case(non.getName()) + "(");
    }

    @Override
    protected final void generate_phase1_nonterminal_end(LinePrinter printer) {
        printer.println(");");
        printer.print("}");
    }

    @Override
    protected final void generate_phase1_more(int labelIndex, LinePrinter printer) {
        printer.print("'label_" + labelIndex + ": loop {");
        printer.indent();
    }

    @Override
    protected final void print_phase1_more_end(int labelIndex, LinePrinter printer, int offset) {
        if (offset == 0) {
            printer.print("\n;");
        } else {
            printer.print("\nbreak 'label_" + labelIndex + ";");
        }
    }

    @Override
    protected final void print_lookahead_amount0(LinePrinter printer, LookaheadState state, Consumer<LinePrinter> action, Lookahead la, NodeScope scope, int index) {
        switch (state) {
            case NOOPENSTM:
                printer.print("\nif ");
                break;
            case OPENIF:
                printer.outdent();
                printer.print("\n} else if (");
                break;
            case OPENSWITCH:
                printer.outdent();
                printer.print("\n_ => {");
                printer.indent();
                if (index >= 0) {
                    printer.print("\nself.jj_la1[" + index + "] = self.jj_gen;");
                }
                printer.print("\nif ");
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
                printer.outdent();
                printer.print("\n} else {");
                printer.indent();
                //$FALL-THROUGH$ Control flows through to next case.
            case NOOPENSTM:
                printer.print("\nlet kind = if self.jj_nt.is_none() {");
                printer.print("\n    u32::MAX");
                printer.print("\n} else {");
                printer.print("\n    self.jj_nt.clone().unwrap().borrow().kind");
                printer.print("\n};");
                printer.print("\nmatch ");
                if (cache_tokens) {
                    printer.print("kind");
                    printer.print(" {");
                    printer.indent();
                } else {
                    printer.print("(jj_ntk==-1)?jj_ntk_f():jj_ntk) {");
                    printer.indent();
                }
                // Don't need to do anything if state is OPENSWITCH.
            default:
        }

        printer.outdent();
        printer.print("\n");
        printer.print(String.join(" | ", cases));
        printer.print(" =>");
        printer.indent();
        printer.print(" {");
        action.accept(printer);
        printer.print("\n}");
    }

    @Override
    protected final void print_lookahead(LinePrinter printer, LookaheadState state, Consumer<LinePrinter> action, Lookahead la, NodeScope scope, int index) {
        switch (state) {
            case NOOPENSTM:
                printer.print("\nif ");
                break;
            case OPENIF:
                printer.outdent();
                printer.print("\n} else if ");
                break;
            case OPENSWITCH:
                printer.outdent();
                printer.print("\n_ => {");
                printer.indent();
                if (index >= 0) {
                    printer.print("\nself.jj_la1[" + index + "] = self.jj_gen;");
                }
                printer.print("\nif ");
        }

        String amount = Integer.toString(la.getAmount());
        printer.print("self.jj_2" + internal_name_as_snake_case(la.getLaExpansion()) + "(" + amount + ")");
        if (!la.getActionTokens().isEmpty()) {
            // In addition, there is also a semantic lookahead. So concatenate
            // the semantic check with the syntactic one.
            printer.print(" && (");
            setup_token(la.getActionTokens().getFirst());
            la.getActionTokens().forEach(t -> printToken(t, scope, printer));
            printTrailingComments(printer, la.getActionTokens().getLast());
            printer.print(")");
        }
        printer.print(" {");
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
                printer.outdent();
                printer.print("\n} else {");
                printer.indent();
                action.accept(printer);
                break;
            case OPENSWITCH:
                printer.outdent();
                printer.print("\n_ => {");
                printer.indent();
                if (index >= 0) {
                    printer.print("\nself.jj_la1[" + index + "] = self.jj_gen;");
                }
                action.accept(printer);
        }

        for (int i = 0; i < indents; i++) {
            printer.outdent();
            printer.print("\n}");
        }
    }

    protected void generate_phase2(Expansion e, LinePrinter printer, ParserData data) {
        printer.println("  fn jj_2" + internal_name_as_snake_case(e) + "(&mut self, xla: u32) -> bool {");
        printer.println("    self.jj_la = xla;");
        printer.println("    self.jj_lastpos = Some(self.token.clone());");
        printer.println("    self.jj_scanpos = Some(self.token.clone());");

        // DEPTH_LIMIT is rejected up front in generate(), so there is no jj_depth_error to test.
        printer.println("//    try {");
        printer.println("      let result = !self.jj_3" + internal_name_as_snake_case(e) + "();");
        printer.println("//    } catch (LookaheadSuccess ls) {");
        printer.println("//      true");
        if (data.getErrorReporting()) {
            printer.println("//    } finally {");
            printer.println("      self.jj_save(" + (Integer.parseInt(internal_name_as_snake_case(e).substring(1)) - 1) + ", xla);");
        }
        printer.println("//    }");
        printer.println("  result");
        printer.println("  }");
        printer.println();
    }

    protected void generate_phase3_routine(ParserData data, Expansion e, int count, LinePrinter printer) {
        if (e.internalName().startsWith("jj_scan_token"))
            return;

        printer.println("fn jj_3" + internal_name_as_snake_case(e) + "(&mut self) -> bool {");

        // DEPTH_LIMIT is rejected up front in generate(); the Rust back end never emits guard code.
        boolean xsp_declared = false;
        Expansion jj3_expansion = null;
        if (data.getDebugLookahead() && (e.parent() instanceof NormalProduction np)) {
            printer.print("    ");
            if (data.getErrorReporting()) {
                printer.print("if (!jj_rescan) ");
            }
            printer.println("trace_call(\"" + Encoding.escapeUnicode(normal_production_as_snake_case(np), Language.JAVA)
                    + "(LOOKING AHEAD...)\");");
            jj3_expansion = e;
        }

        buildPhase3RoutineRecursive(data, jj3_expansion, xsp_declared, e, count, printer);

        printer.println("    " + genReturn(jj3_expansion, false, data));
        if (data.getDepthLimit() > 0) {
            printer.println("} finally {");
            printer.println("    self.jj_depth -= 1;");
            printer.println("}");
        }
        printer.println("}");
        printer.println();
    }

    private boolean buildPhase3RoutineRecursive(ParserData data, Expansion jj3_expansion,
                                                boolean xsp_declared, Expansion e, int count, LinePrinter printer) {
        if (e.internalName().startsWith("jj_scan_token")) {
            return xsp_declared;
        }

        switch (e) {
            case RExpression e_nrw -> {
                printer.print("    if self.jj_scan_token(");
                if (e_nrw.getLabel().isEmpty()) {
                    Object label = data.getNameOfToken(e_nrw.getOrdinal());
                    printer.print((label == null) ? "" + e_nrw.getOrdinal() : "" + label);
                } else {
                    printer.print(e_nrw.getLabel());
                }
                printer.println(") {");
                printer.println("        return " + genReturn(jj3_expansion, true, data) + ";");
                printer.println("    }");
            }
            case NonTerminal e_nrw -> {
                // All expansions of non-terminals have the "name" fields set. So
                // there's no need to check it below for "e_nrw" and "ntexp". In
                // fact, we rely here on the fact that the "name" fields of both these
                // variables are the same.
                NormalProduction ntprod = data.getProduction(e_nrw.getName());
                Expansion ntexp = ntprod.getExpansion();
                printer.println("    if self." + genjj_3Call(ntexp) + " {");
                printer.println("        return " + genReturn(jj3_expansion, true, data) + ";");
                printer.println("    }");
            }
            case Choice e_nrw -> {
                Sequence nested_seq;
                if (e_nrw.getChoices().size() != 1) {
                    if (!xsp_declared) {
                        xsp_declared = true;
                        printer.println("    let mut xsp: Rc<RefCell<Token>>;");
                    }
                    printer.println("    xsp = self.jj_scanpos.as_mut().unwrap().clone();");
                }

                for (int i = 0; i < e_nrw.getChoices().size(); i++) {
                    nested_seq = (Sequence) e_nrw.getChoices().get(i);
                    Lookahead la = (Lookahead) nested_seq.getUnits().getFirst();
                    if (!la.getActionTokens().isEmpty()) {
                        printer.println("    self.jj_lookingAhead = true;");
                        printer.print("    self.jj_semLA = ");
                        setup_token((la.getActionTokens().getFirst()));
                        for (Token token : la.getActionTokens()) {
                            printToken(token, printer);
                        }
                        printTrailingComments(printer, la.getActionTokens().getLast());
                        printer.println(";");
                        printer.println("    self.jj_lookingAhead = false;");
                    }
                    printer.print("    if ");
                    if (!la.getActionTokens().isEmpty()) {
                        printer.print("!self.jj_semLA || ");
                    }

                    printer.println("self." + genjj_3Call(nested_seq) + " {");
                    if (i != (e_nrw.getChoices().size() - 1))
                        printer.println("    self.jj_scanpos = Some(xsp.clone());");
                    else {
                        printer.println("    return " + genReturn(jj3_expansion, true, data) + ";");
                        printer.println("}");
                    }
                }
                for (int i = 1; i < e_nrw.getChoices().size(); i++) {
                    printer.println("}");
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
                    printer.println("    let mut xsp: Rc<RefCell<Token>>;");
                }
                Expansion nested_e = e_nrw.getExpansion();
                printer.print(String.format("""
                                    if self.%s {
                                        return %s;
                                    }
                                    loop {
                                        xsp = self.jj_scanpos.as_mut().unwrap().clone();
                                        if self.%s {
                                            self.jj_scanpos = Some(xsp.clone());
                                            break;
                                        }
                                    }
                                """, genjj_3Call(nested_e), genReturn(jj3_expansion, true, data),
                        genjj_3Call(nested_e)));
            }
            case ZeroOrMore e_nrw -> {
                if (!xsp_declared) {
                    xsp_declared = true;
                    printer.println("    let mut xsp: Rc<RefCell<Token>>;");
                }
                Expansion nested_e = e_nrw.getExpansion();
                printer.println("    loop {");
                printer.println("        xsp = self.jj_scanpos.as_mut().unwrap().clone();");
                printer.println("        if self." + genjj_3Call(nested_e) + " {");
                printer.println("            self.jj_scanpos = Some(xsp.clone());");
                printer.println("            break;");
                printer.println("        }");
                printer.println("    }");
            }
            case ZeroOrOne e_nrw -> {
                if (!xsp_declared) {
                    xsp_declared = true;
                    printer.println("    let mut xsp: Rc<RefCell<Token>>;");
                }
                Expansion nested_e = e_nrw.getExpansion();
                printer.println("    xsp = self.jj_scanpos.as_mut().unwrap().clone();");
                printer.println("    if self." + genjj_3Call(nested_e) + " {");
                printer.println("        self.jj_scanpos = Some(xsp.clone());");
                printer.println("    }");
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
                            normal_production_as_snake_case((NormalProduction) expansion.parent()), Language.JAVA)
                            + "(LOOKAHEAD " + (value ? "FAILED" : "SUCCEEDED") + ")\");";
            if (data.getErrorReporting()) {
                tracecode = "if (!jj_rescan) " + tracecode;
            }
            return "{ " + tracecode + " return " + retval + "; }";
        } else {
            return retval;
        }
    }

    private String genjj_3Call(Expansion e) {
        var name = e.internalName();
        return name.startsWith("jj_scan_token") ? name : "jj_3" + internal_name_as_snake_case(e) + "()";
    }

    private static String internal_name_as_snake_case(Expansion e) {
        return to_snake_case(e.internalName());
    }

    private static String normal_production_as_snake_case(NormalProduction p) {
        return to_snake_case(p.getLhs());
    }

    private static String to_snake_case(String name) {
        return name.replaceAll("([A-Z])(?=[A-Z])", "$1_")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }

    @Override
    public final void insertOpenNodeCode(NodeScope ns, String nodeClass, LinePrinter printer, Options options) {
        printer.print("let " + ns.getNodeVariable() + " = ");
        if (options.getNodeFactory().equals("*")) {
            // Old-style multiple-implementations.
            printer.println("(" + nodeClass + ")" + nodeClass + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        } else if (!options.getNodeFactory().isEmpty()) {
            printer.println("(" + nodeClass + ")"
                    + options.getNodeFactory() + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        } else {
            printer.println("new_node(&TreeConstants::" + ns.getNodeDescriptor().getNodeId() + ");");
        }

        printer.println("let mut " + ns.getClosedVariable() + " = true;");

        printer.println("self.jjtree.open_node_scope(&" + ns.getNodeVariable() + ");");
        if (options.getNodeScopeHook())
            printer.println("self.jjtree_open_node_scope(" + ns.getNodeVariable() + ".as_ref());");

        if (options.getTrackTokens()) {
            printer.println(ns.getNodeVariable() + ".jjtSetFirstToken(getToken(1));");
        }
        printer.print("// TRY_CATCH");
    }

    @Override
    public final void insertCloseNodeCode(NodeScope ns, LinePrinter printer, Options options, boolean isFinal) {
        printer.println("self.jjtree.close_node_scope_bool(&" + ns.getNodeVariable() + ", true);");
        if (!isFinal) {
            printer.println(ns.getClosedVariable() + " = false;");
        }
        if (options.getNodeScopeHook()) {
            printer.println("if self.jjtree.is_node_created() {");
            printer.println("  self.jjtree_close_node_scope(" + ns.getNodeVariable() + ".as_ref());");
            printer.println("}");
        }

        if (options.getTrackTokens()) {
            printer.println(ns.getNodeVariable() + ".jjtSetLastToken(getToken(0));");
        }
    }

    @Override
    public final void insertCatchBlocks(NodeScope ns, LinePrinter printer, Options options, Collection<String> thrown_names) {
        if (!thrown_names.isEmpty()) {
            printer.println("  if try_catch.is_err() {");
            printer.println("// CATCH " + ns.getExceptionVariable());
            printer.println("  if " + ns.getClosedVariable() + " {");
            printer.println("//    self.jjtree.clear_node_scope(" + ns.getNodeVariable() + ".clone());");
            printer.println("//    " + ns.getClosedVariable() + " = false;");
            printer.println("  } else {");
            printer.println("//    self.jjtree.pop_node();");
            printer.println("  }");
            // This is either an Error or an undeclared Exception. If it's an Error then the cast is good,
            // otherwise we want to force the user to declare it by crashing on the bad cast.
            printer.println("  }");
        }

        printer.println("    // FINALLY");
        printer.println("if " + ns.getClosedVariable() + " {");
        insertCloseNodeCode(ns, printer, options, true);
        printer.println("}");
        if (!thrown_names.isEmpty()) {
            printer.println("    if try_catch.is_err() {");
            printer.println("        return Err(std::io::Error::new(std::io::ErrorKind::Other, \""
                    + ns.getExceptionVariable() + "\"));");
            printer.println("    }");
        }
        printer.println("// END TRY_CATCH");
    }
}
