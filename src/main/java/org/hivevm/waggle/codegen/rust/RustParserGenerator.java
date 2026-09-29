// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.codegen.ParserSyntax;
import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.GenerationException;
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
 * Implements the {@link ParserGenerator} for the RUST language, in the shape ADR-0030 gives the
 * generated parser: failure is a {@code Result}, the tokens are indices into one buffer, and a
 * production is a method.
 */
class RustParserGenerator extends ParserGenerator {

    /** The methods of the generated parser that a production must not be named as. */
    private static final java.util.Set<String> PARSER_METHODS = java.util.Set.of(
            "new", "from_lexer", "root_node", "get_next_token", "get_token", "lexer",
            "jjtree_open_node_scope", "jjtree_close_node_scope");

    public RustParserGenerator() {
        super(Language.RUST);
    }

    @Override
    protected ParserSyntax newParserSyntax() {
        return new RustParserSyntax();
    }

    /**
     * Refuses what the Rust parser cannot do yet, and a production whose method name the parser
     * already has.
     */
    @Override
    protected void validate(ParserPlan data) {
        if (data.getDepthLimit() > 0) {
            // ADR-0030 keeps DEPTH_LIMIT out of the Rust parser for now: it is ported separately.
            // Fail honestly instead of emitting a parser without the guard the grammar asked for
            // (SPECIFICATION.md §3: target feature gaps are tracked, not silently produced).
            throw new GenerationException(
                    "DEPTH_LIMIT is not supported for the Rust target.");
        }
        if (data.getDebugParser() || data.getDebugLookahead()) {
            // The parser traces are ported separately too (ADR-0030).
            throw new GenerationException(
                    "DEBUG_PARSER and DEBUG_LOOKAHEAD are not supported for the Rust target.");
        }

        for (var plan : data.productionPlans()) {
            var name = RustParserSyntax.toSnakeCase(plan.signature().name());
            if (RustParserGenerator.PARSER_METHODS.contains(name) || name.startsWith("jj_")) {
                throw new GenerationException("The production '" + plan.signature().name()
                        + "' would be the Rust method '" + name + "', which the parser has already.");
            }
        }
    }

    @Override
    protected final void generate(ParserPlan data, OptionsContext options) {
        RustTemplate.PARSER.render(options);
    }

    /**
     * {@code pub fn name(&mut self, depth: i32) -> Result<T, ParseError>}: the grammar writes a
     * parameter as type then name, Rust as name then type (ADR-0030).
     */
    @Override
    protected void generate_phase1_head(Signature p, LinePrinter printer, ParserPlan data) {
        Token t = p.head().first();
        var comments = cursorAt(t);
        comments.leadingComments(printer, t);
        printer.println();
        printer.print("pub fn ");
        comments.trailingComments(printer, t);
        printer.print(RustIdentifier.of(RustParserSyntax.toSnakeCase(p.name())) + "(&mut self");
        for (var parameter : p.split()) {
            printer.print(", " + RustIdentifier.of(parameter.name()) + ": " + parameter.type());
        }

        var type = (p.returnType() == null) ? "()" : p.returnType();
        printer.print(") -> Result<" + type + ", ParseError> {");
    }

    /** A production that returns nothing yields Ok(()) at its end. */
    @Override
    protected final void generate_phase1_tail(Signature p, LinePrinter printer) {
        if (p.returnType() == null) {
            printer.println();
            printer.print("Ok(())");
        }
        super.generate_phase1_tail(p, printer);
    }

    @Override
    protected void generate_phase1_body(Signature p, LinePrinter printer, ParserPlan data, Consumer<LinePrinter> consumer) {
        // DEPTH_LIMIT and DEBUG_PARSER are rejected up front in validate(); the Rust back end emits
        // neither guard code nor a trace wrapper.
        consumer.accept(printer);
    }

    /**
     * {@code jj_2}: runs a lookahead. Java unwinds a decided lookahead with LookaheadSuccess; here
     * the routines return with {@code jj_ls} set. A lexical error met on the way is the result.
     */
    @Override
    protected void generate_phase2(Jj2Routine routine, LinePrinter printer, ParserPlan data) {
        var name = lookaheadRoutineName(routine.name());
        printer.println("fn jj_2" + name + "(&mut self, xla: i32) -> Result<bool, ParseError> {");
        printer.println("    self.jj_la = xla;");
        printer.println("    self.jj_lastpos = self.token;");
        printer.println("    self.jj_scanpos = self.token;");
        printer.println("    self.jj_ls = false;");
        printer.println("    let result = !self.jj_3" + name + "() || self.jj_ls;");
        printer.println("    self.jj_ls = false;");
        if (data.recordsExpectedTokens()) {
            printer.println("    self.jj_save(" + routine.saveSlot() + ", xla);");
        }
        printer.println("    if let Some(error) = &self.jj_lexical_error {");
        printer.println("        return Err(error.clone().into());");
        printer.println("    }");
        printer.println("    Ok(result)");
        printer.println("}");
        printer.println();
    }

    @Override
    protected void generate_phase3_routine(ParserPlan data, Jj3Routine routine, LinePrinter printer) {
        printer.println("fn jj_3" + lookaheadRoutineName(routine.name()) + "(&mut self) -> bool {");

        // DEPTH_LIMIT and DEBUG_LOOKAHEAD are rejected up front in validate(); the Rust back end
        // emits neither guard code nor a trace call, so no expansion is ever traced.
        printer.indent();
        phase3().emit(data, null, routine, printer);
        printer.println(genReturn(null, false, data));
        printer.outdent();
        printer.println("}");
        printer.println();
    }

    /**
     * A jj_3 routine ends in a bare {@code true}/{@code false} expression, where the base class
     * emits a {@code return} statement. DEBUG_LOOKAHEAD is rejected up front in validate(), so no
     * trace code is ever wrapped around it.
     */
    @Override
    protected String genReturn(String traced, boolean value, ParserPlan data) {
        return Boolean.toString(value);
    }

    @Override
    protected String lookaheadRoutineName(String name) {
        return RustParserSyntax.toSnakeCase(name);
    }
}
