// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.codegen.java;

import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Encoding;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.analysis.ParserData;
import org.hivevm.waggle.codegen.ParserGenerator;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.grammar.Token;
import org.hivevm.source.LinePrinter;

import java.util.function.Consumer;

/**
 * Implements the {@link ParserGenerator} for the JAVA language.
 */
class JavaParserGenerator extends ParserGenerator {

    public JavaParserGenerator() {
        super(Language.JAVA);
    }

    @Override
    protected final void generate(ParserData data, OptionsContext options) {
        options.add(Waggle.JAVA_IMPORTS, data.options().get(Waggle.JAVA_IMPORTS))
                .set(Waggle.JAVA_IMPORTS + "_VALUE", i -> i);

        options.add(ParserGenerator.TOKEN_MASKS + "_LA1", ((data.getTokenCount() - 1) / 32) + 1)
                .set("TOKEN_MASKS_LA1_INDEX", i -> i)
                .set("TOKEN_MASKS_LA1_VALUE", i -> (i == 0) ? "" : (32 * i) + " + ");

        JavaTemplate.PARSER.render(options);
    }

    @Override
    protected String generate_phase1_head(NormalProduction p, LinePrinter printer, ParserData data) {
        Token t = p.getFirstToken();
        setup_token(t);
        printLeadingComments(printer, t);
        printer.print("public final ");
        if (p.getReturnTypeToken() != null) {
            printer.print(p.getReturnTypeToken().image);
        } else {
            printer.print("void");
        }
        printTrailingComments(printer, t);
        printer.print(" " + p.getLhs() + "(");
        if (!p.getParameterListTokens().isEmpty()) {
            printTokens(p.getParameterListTokens(), null, printer);
        }
        printer.print(") throws ParseException");

        printer.print(" {");
        return null;
    }

    /** Wraps the body of a production in the DEPTH_LIMIT guard and the DEBUG_PARSER trace. */
    @Override
    protected void generate_phase1_body(NormalProduction p, LinePrinter printer, ParserData data, String returnType, Consumer<LinePrinter> consumer) {
        if (data.getDepthLimit() > 0) {
            printer.println("if(++jj_depth > " + data.getDepthLimit() + ") {");
            printer.indent();
            printer.println("jj_consume_token(-1);");
            printer.println("throw new ParseException();");
            printer.outdent();
            printer.println("}");
            printer.println("try {");
            printer.indent();
        }

        if (data.getDebugParser()) {
            printer.println();
            printer.println("trace_call(\"" + Encoding.escapeUnicode(p.getLhs(), Language.JAVA) + "\");");
            printer.println("try {");
            printer.indent();
        }

        consumer.accept(printer);

        if (data.getDebugParser()) {
            printer.outdent();
            printer.println("} finally {");
            printer.indent();
            printer.println("trace_return(\"" + Encoding.escapeUnicode(p.getLhs(), Language.JAVA) + "\");");
            printer.outdent();
            printer.println("}");
        }
        if (data.getDepthLimit() > 0) {
            printer.outdent();
            printer.println("} finally {");
            printer.indent();
            printer.println("--jj_depth;");
            printer.outdent();
            printer.println("}");
        }
    }

    @Override
    protected void generate_phase2(Expansion e, LinePrinter printer, ParserData data) {
        printer.println("private boolean jj_2" + internalName(e) + "(int xla) {");
        printer.indent();
        printer.println("jj_la = xla;");
        printer.println("jj_lastpos = jj_scanpos = token;");

        String ret_suffix = "";
        if (data.getDepthLimit() > 0) {
            printer.println("jj_depth_error = false;");
            ret_suffix = " && !jj_depth_error";
        }
        printer.println("try {");
        printer.indent();
        printer.println("return (!jj_3" + internalName(e) + "()" + ret_suffix + ");");
        printer.outdent();
        printer.println("} catch (LookaheadSuccess ls) {");
        printer.indent();
        printer.println("return true;");
        printer.outdent();
        if (data.getErrorReporting()) {
            printer.println("} finally {");
            printer.indent();
            printer.println("jj_save(" + (Integer.parseInt(internalName(e).substring(1)) - 1) + ", xla);");
            printer.outdent();
        }
        printer.println("}");
        printer.outdent();
        printer.println("}");
        printer.println();
    }

    @Override
    protected void generate_phase3_routine(ParserData data, Expansion e, int count, LinePrinter printer) {
        if (internalName(e).startsWith("jj_scan_token"))
            return;

        printer.println("private boolean jj_3" + internalName(e) + "() {");
        printer.indent();

        // Too deep a lookahead fails it: jj_2 tests the flag. A ParseException, as a production
        // throws, cannot leave a jj_3 routine, which declares none.
        if (data.getDepthLimit() > 0) {
            printer.println("if(++jj_depth > " + data.getDepthLimit() + ") {");
            printer.indent();
            printer.println("--jj_depth;");
            printer.println("jj_depth_error = true;");
            printer.println("return true;");
            printer.outdent();
            printer.println("}");
            printer.println("try {");
            printer.indent();
        }

        Expansion jj3_expansion = null;
        if (data.getDebugLookahead() && (e.parent() instanceof NormalProduction np)) {
            if (data.getErrorReporting()) {
                printer.print("if (!jj_rescan) ");
            }
            printer.println("trace_call(\"" + Encoding.escapeUnicode(np.getLhs(), Language.JAVA)
                    + "(LOOKING AHEAD...)\");");
            jj3_expansion = e;
        }

        phase3().emit(data, jj3_expansion, e, count, printer);

        printer.println(genReturn(jj3_expansion, false, data));
        if (data.getDepthLimit() > 0) {
            printer.outdent();
            printer.println("} finally {");
            printer.indent();
            printer.println("--jj_depth;");
            printer.outdent();
            printer.println("}");
        }

        printer.outdent();
        printer.println("}");
        printer.println();
    }

}
