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
import org.hivevm.waggle.analysis.ParserData;
import org.hivevm.waggle.codegen.ParserGenerator;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.grammar.ParserConstants;
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
    protected final void generate(ParserData data, OptionsContext options) {
        options.set("DUMP_NORMALPRODUCTIONS_IMPL", w -> data.getProductions().forEach(n -> {
            Token returnType = n.getReturnTypeToken();
            w.println((returnType == null ? "void" : returnType.image) + " " + n.getLhs() + "();");
        }));

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
        Token returnType = p.getReturnTypeToken();
        if (returnType != null) {
            printer.print(returnType.image);
            if (returnType.kind == ParserConstants.STAR) {
                ptr_ret = true;
            }
        } else {
            printer.print("void");
            void_ret = true;
        }
        printTrailingComments(printer, t);
        printer.print(" " + data.getParserName() + "::" + p.getLhs() + "(");
        if (!p.getParameterListTokens().isEmpty()) {
            printTokens(p.getParameterListTokens(), null, printer);
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

    /** Wraps the body of a production in the DEPTH_LIMIT guard and the DEBUG_PARSER trace. */
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













    protected void generate_phase2(Expansion e, LinePrinter printer, ParserData data) {
        printer.println("  inline bool jj_2" + internalName(e) + "(int xla) {");
        printer.println("    jj_la = xla; jj_lastpos = jj_scanpos = token;");

        String ret_suffix = "";
        if (data.getDepthLimit() > 0) {
            ret_suffix = " && !jj_depth_error";
        }

        printer.println("    jj_done = false;");
        printer.println("    return (!jj_3" + internalName(e) + "() || jj_done)" + ret_suffix + ";");
        if (data.getErrorReporting()) {
            printer.println("    { jj_save(" + (Integer.parseInt(internalName(e).substring(1)) - 1) + ", xla); }");
        }
        printer.println("  }");
        printer.println();
    }

    protected void generate_phase3_routine(ParserData data, Expansion e, int count, LinePrinter printer) {
        if (internalName(e).startsWith("jj_scan_token"))
            return;

        printer.println(" inline bool jj_3" + internalName(e) + "()");
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

        phase3().emit(data, jj3_expansion, xsp_declared, e, count, printer);

        printer.println("    " + genReturn(jj3_expansion, false, data));
        if (data.getDepthLimit() > 0) {
            printer.println("#undef __ERROR_RET__");
        }
        printer.println("  }");
        printer.println();
    }

}
