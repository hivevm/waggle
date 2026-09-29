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
import org.hivevm.waggle.analysis.Jj2Routine;
import org.hivevm.waggle.analysis.Jj3Routine;
import org.hivevm.waggle.analysis.ParserPlan;
import org.hivevm.waggle.analysis.ProductionPlan.Signature;
import org.hivevm.waggle.codegen.ParserGenerator;
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
    protected final void generate(ParserPlan data, OptionsContext options) {
        options.add(Waggle.JAVA_IMPORTS, data.options().get(Waggle.JAVA_IMPORTS))
                .set(Waggle.JAVA_IMPORTS + "_VALUE", i -> i);

        JavaTemplate.PARSER.render(options);
    }

    @Override
    protected void generate_phase1_head(Signature p, LinePrinter printer, ParserPlan data) {
        Token t = p.head().first();
        var comments = cursorAt(t);
        comments.leadingComments(printer, t);
        printer.print("public final ");
        printer.print((p.returnType() == null) ? "void" : p.returnType());
        comments.trailingComments(printer, t);
        printer.print(" " + p.name() + "(");
        if (!p.parameters().isEmpty()) {
            printTokens(p.parameters(), null, printer);
        }
        printer.print(") throws ParseException");

        printer.print(" {");
    }

    /** Wraps the body of a production in the DEPTH_LIMIT guard and the DEBUG_PARSER trace. */
    @Override
    protected void generate_phase1_body(Signature p, LinePrinter printer, ParserPlan data, Consumer<LinePrinter> consumer) {
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
            printer.println("trace_call(\"" + Encoding.escapeUnicode(p.name(), Language.JAVA) + "\");");
            printer.println("try {");
            printer.indent();
        }

        consumer.accept(printer);

        if (data.getDebugParser()) {
            printer.outdent();
            printer.println("} finally {");
            printer.indent();
            printer.println("trace_return(\"" + Encoding.escapeUnicode(p.name(), Language.JAVA) + "\");");
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
    protected void generate_phase2(Jj2Routine routine, LinePrinter printer, ParserPlan data) {
        printer.println("private boolean jj_2" + routine.name() + "(int xla) {");
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
        printer.println("return (!jj_3" + routine.name() + "()" + ret_suffix + ");");
        printer.outdent();
        printer.println("} catch (LookaheadSuccess ls) {");
        printer.indent();
        printer.println("return true;");
        printer.outdent();
        if (data.recordsExpectedTokens()) {
            printer.println("} finally {");
            printer.indent();
            printer.println("jj_save(" + routine.saveSlot() + ", xla);");
            printer.outdent();
        }
        printer.println("}");
        printer.outdent();
        printer.println("}");
        printer.println();
    }

    @Override
    protected void generate_phase3_routine(ParserPlan data, Jj3Routine routine, LinePrinter printer) {
        printer.println("private boolean jj_3" + routine.name() + "() {");
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

        String traced = traceLookingAhead(data, routine, "", printer);

        phase3().emit(data, traced, routine, printer);

        printer.println(genReturn(traced, false, data));
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
