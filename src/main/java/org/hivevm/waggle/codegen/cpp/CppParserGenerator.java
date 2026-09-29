// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.waggle.codegen.ParserSyntax;
import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Encoding;
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
 * Implements the {@link ParserGenerator} for the C++ language.
 */
class CppParserGenerator extends ParserGenerator {

    public CppParserGenerator() {
        super(Language.CPP);
    }

    @Override
    protected ParserSyntax newParserSyntax() {
        return new CppParserSyntax();
    }

    @Override
    protected final void generate(ParserPlan data, OptionsContext options) {
        options.set("DUMP_NORMALPRODUCTIONS_IMPL", w -> data.productionPlans().forEach(plan -> {
            var n = plan.signature();
            w.print(((n.returnType() == null) ? "void" : n.returnType()) + " " + n.name() + "(");
            if (!n.parameters().isEmpty()) {
                printTokens(n.parameters(), null, w);
            }
            w.println(");");
        }));

        CppTemplate.PARSER.render(options, data.getParserName());
        CppTemplate.PARSER_H.render(options, data.getParserName());
    }

    @Override
    protected void generate_phase1_head(Signature p, LinePrinter printer, ParserPlan data) {
        Token t = p.head().first();

        var comments = cursorAt(t);
        comments.leadingComments(printer, t);
        printer.print((p.returnType() == null) ? "void" : p.returnType());
        comments.trailingComments(printer, t);
        printer.print(" " + data.getParserName() + "::" + p.name() + "(");
        if (!p.parameters().isEmpty()) {
            printTokens(p.parameters(), null, printer);
        }
        printer.print(")");

        printer.print(" {");
    }

    /** Wraps the body of a production in the DEPTH_LIMIT guard and the DEBUG_PARSER trace. */
    @Override
    protected void generate_phase1_body(Signature p, LinePrinter printer, ParserPlan data, Consumer<LinePrinter> consumer) {
        boolean hasReturnErr = false;
        boolean voidReturn = (p.returnType() == null);
        if ((data.getDepthLimit() > 0) && !voidReturn) {
            String method_name = p.name();
            printer.println("\n#if !defined ERROR_RET_" + method_name);
            // The value returned on error: 0 converts to most basic types.
            printer.println("#define ERROR_RET_" + method_name + " 0");
            printer.println("#endif");
            printer.println("#define __ERROR_RET__ ERROR_RET_" + method_name);
            hasReturnErr = true;
        }

        if (data.getDebugParser()) {
            printer.println();
            printer.println("    JJEnter<std::function<void()>> jjenter([this]() {trace_call  (\""
                    + Encoding.escapeUnicode(p.name(), Language.CPP) + "\"); });");
            printer.println("    JJExit <std::function<void()>> jjexit ([this]() {trace_return(\""
                    + Encoding.escapeUnicode(p.name(), Language.CPP) + "\"); });");
        }

        consumer.accept(printer);

        if (!voidReturn) {
            printer.println("assert(false);");
        }

        if (hasReturnErr) {
            printer.println("\n#undef __ERROR_RET__");
        }
    }

    @Override
    protected void generate_phase2(Jj2Routine routine, LinePrinter printer, ParserPlan data) {
        printer.println("  inline bool jj_2" + routine.name() + "(int xla) {");
        printer.println("    jj_la = xla; jj_lastpos = jj_scanpos = token;");

        String ret_suffix = "";
        if (data.getDepthLimit() > 0) {
            ret_suffix = " && !jj_depth_error";
        }

        printer.println("    jj_done = false;");
        printer.println("    return (!jj_3" + routine.name() + "() || jj_done)" + ret_suffix + ";");
        printer.println("  }");
        printer.println();
    }

    @Override
    protected void generate_phase3_routine(ParserPlan data, Jj3Routine routine, LinePrinter printer) {
        printer.println(" inline bool jj_3" + routine.name() + "()");
        printer.println(" {\n");
        printer.println("    if (jj_done) return true;");
        if (data.getDepthLimit() > 0) {
            printer.println("#define __ERROR_RET__ true");
        }

        String traced = traceLookingAhead(data, routine, "    ", printer);

        phase3().emit(data, traced, routine, printer);

        printer.println("    " + genReturn(traced, false, data));
        if (data.getDepthLimit() > 0) {
            printer.println("#undef __ERROR_RET__");
        }
        printer.println("  }");
        printer.println();
    }

}
