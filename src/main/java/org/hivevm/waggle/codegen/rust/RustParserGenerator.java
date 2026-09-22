// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.codegen.ParserSyntax;
import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.GenerationException;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.analysis.ParserData;
import org.hivevm.waggle.codegen.ParserGenerator;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.grammar.Token;
import org.hivevm.source.LinePrinter;

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
    protected ParserSyntax newParserSyntax() {
        return new RustParserSyntax();
    }

    @Override
    protected final void generate(ParserData data, OptionsContext options) {
        if (data.getDepthLimit() > 0) {
            // The Rust back end has no depth-limit support yet: the template lacks the
            // jj_depth_error flag and jj_depth is a u32 (so the -1 sentinel would not compile).
            // Fail honestly instead of emitting Rust that cannot compile (SPECIFICATION.md §3:
            // target feature gaps are tracked, not silently produced).
            throw new GenerationException(
                    "DEPTH_LIMIT is not supported for the Rust target.");
        }
        if (data.getDebugParser() || data.getDebugLookahead()) {
            // The trace runtime of templates/rust/parser.rs is still the unported Java one --
            // trace_call, trace_return, trace_scan and the trace_enabled flag are Java methods on a
            // Java class. The generator matched it: it wrapped every production in try/finally,
            // which Rust does not have, and escaped the trace strings as Java. Fail honestly
            // instead of emitting Rust that cannot compile (SPECIFICATION.md §3: target feature
            // gaps are tracked, not silently produced).
            throw new GenerationException(
                    "DEBUG_PARSER and DEBUG_LOOKAHEAD are not supported for the Rust target.");
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

    @Override
    protected void generate_phase1_body(NormalProduction p, LinePrinter printer, ParserData data, String returnType, Consumer<LinePrinter> consumer) {
        // DEPTH_LIMIT and DEBUG_PARSER are rejected up front in generate(); the Rust back end emits
        // neither guard code nor a trace wrapper.
        consumer.accept(printer);
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
        if (internalName(e).startsWith("jj_scan_token"))
            return;

        printer.println("fn jj_3" + internal_name_as_snake_case(e) + "(&mut self) -> bool {");

        // DEPTH_LIMIT and DEBUG_LOOKAHEAD are rejected up front in generate(); the Rust back end
        // emits neither guard code nor a trace call, so no expansion is ever traced.
        boolean xsp_declared = false;
        Expansion jj3_expansion = null;

        phase3().emit(data, jj3_expansion, xsp_declared, e, count, printer);

        printer.println("    " + genReturn(jj3_expansion, false, data));
        if (data.getDepthLimit() > 0) {
            printer.println("} finally {");
            printer.println("    self.jj_depth -= 1;");
            printer.println("}");
        }
        printer.println("}");
        printer.println();
    }


    /**
     * A jj_3 routine ends in a bare {@code true}/{@code false} expression, where the base class
     * emits a {@code return} statement. DEBUG_LOOKAHEAD is rejected up front in generate(), so no
     * trace code is ever wrapped around it.
     */
    @Override
    protected String genReturn(Expansion expansion, boolean value, ParserData data) {
        return Boolean.toString(value);
    }

    @Override
    protected String genjj_3Call(Expansion e) {
        var name = internalName(e);
        return name.startsWith("jj_scan_token") ? name : "jj_3" + internal_name_as_snake_case(e) + "()";
    }

    private String internal_name_as_snake_case(Expansion e) {
        return RustParserSyntax.toSnakeCase(internalName(e));
    }

    private static String normal_production_as_snake_case(NormalProduction p) {
        return RustParserSyntax.toSnakeCase(p.getLhs());
    }

}
