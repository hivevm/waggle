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

import java.util.ArrayList;
import java.util.List;
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

    /** Whether the production being written returns nothing, so that its tail yields Ok(()). */
    private boolean returnsUnit;

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

        for (var production : data.getProductions()) {
            var name = RustParserSyntax.toSnakeCase(production.getLhs());
            if (RustParserGenerator.PARSER_METHODS.contains(name) || name.startsWith("jj_")) {
                throw new GenerationException("The production '" + production.getLhs()
                        + "' would be the Rust method '" + name + "', which the parser has already.");
            }
        }

        options.add(ParserGenerator.TOKEN_MASKS + "_LA1", ((data.getTokenCount() - 1) / 32) + 1)
                .set("TOKEN_MASKS_LA1_INDEX", this::getStringIndex)
                .set("TOKEN_MASKS_LA1_VALUE", i -> (i == 0) ? "" : (32 * i) + " + ");

        RustTemplate.PARSER.render(options);
    }

    @Override
    protected String getStringIndex(int i) {
        return "_" + i;
    }

    /**
     * {@code pub fn name(&mut self, depth: i32) -> Result<T, ParseError>}: the grammar writes a
     * parameter as type then name, Rust as name then type (ADR-0030).
     */
    @Override
    protected String generate_phase1_head(NormalProduction p, LinePrinter printer, ParserData data) {
        Token t = p.getFirstToken();
        setup_token(t);
        printLeadingComments(printer, t);
        printer.println();
        printer.print("pub fn ");
        printTrailingComments(printer, t);
        printer.print(RustIdentifier.of(RustParserSyntax.toSnakeCase(p.getLhs())) + "(&mut self");
        for (var parameter : RustParserGenerator.parameters(p.getParameterListTokens())) {
            printer.print(", " + parameter);
        }

        this.returnsUnit = p.getReturnTypeToken() == null;
        var type = this.returnsUnit ? "()" : p.getReturnTypeToken().image;
        printer.print(") -> Result<" + type + ", ParseError> {");
        return null;
    }

    /**
     * The parameters of a production as Rust writes them. The tokens are "type name" pairs between
     * commas; a type may itself hold commas inside angle brackets.
     */
    static List<String> parameters(List<Token> tokens) {
        var parameters = new ArrayList<String>();
        var type = new ArrayList<Token>();
        int depth = 0;
        for (var token : tokens) {
            if (token.image.equals(",") && (depth == 0)) {
                parameters.add(RustParserGenerator.parameter(type));
                type.clear();
                continue;
            }
            if (token.image.equals("<")) {
                depth++;
            } else if (token.image.equals(">")) {
                depth--;
            }
            type.add(token);
        }
        if (!type.isEmpty()) {
            parameters.add(RustParserGenerator.parameter(type));
        }
        return parameters;
    }

    /** One parameter: the last token names it, the tokens before are its type, spaced as written. */
    private static String parameter(List<Token> tokens) {
        var name = tokens.getLast().image;
        var type = new StringBuilder();
        for (int i = 0; i < tokens.size() - 1; i++) {
            var token = tokens.get(i);
            if ((i > 0) && ((token.beginLine != tokens.get(i - 1).endLine)
                    || (token.beginColumn > tokens.get(i - 1).endColumn + 1))) {
                type.append(' ');
            }
            type.append(token.image);
        }
        return RustIdentifier.of(name) + ": " + type;
    }

    @Override
    protected final void generate_phase1_tail(LinePrinter printer) {
        if (this.returnsUnit) {
            printer.println();
            printer.print("Ok(())");
        }
        super.generate_phase1_tail(printer);
    }

    @Override
    protected void generate_phase1_body(NormalProduction p, LinePrinter printer, ParserData data, String returnType, Consumer<LinePrinter> consumer) {
        // DEPTH_LIMIT and DEBUG_PARSER are rejected up front in generate(); the Rust back end emits
        // neither guard code nor a trace wrapper.
        consumer.accept(printer);
    }

    /**
     * {@code jj_2}: runs a lookahead. Java unwinds a decided lookahead with LookaheadSuccess; here
     * the routines return with {@code jj_ls} set. A lexical error met on the way is the result.
     */
    @Override
    protected void generate_phase2(Expansion e, LinePrinter printer, ParserData data) {
        var name = internal_name_as_snake_case(e);
        printer.println("fn jj_2" + name + "(&mut self, xla: i32) -> Result<bool, ParseError> {");
        printer.println("    self.jj_la = xla;");
        printer.println("    self.jj_lastpos = self.token;");
        printer.println("    self.jj_scanpos = self.token;");
        printer.println("    self.jj_ls = false;");
        printer.println("    let result = !self.jj_3" + name + "() || self.jj_ls;");
        printer.println("    self.jj_ls = false;");
        if (data.getErrorReporting()) {
            printer.println("    self.jj_save(" + (Integer.parseInt(name.substring(1)) - 1) + ", xla);");
        }
        printer.println("    if let Some(error) = &self.jj_lexical_error {");
        printer.println("        return Err(error.clone().into());");
        printer.println("    }");
        printer.println("    Ok(result)");
        printer.println("}");
        printer.println();
    }

    @Override
    protected void generate_phase3_routine(ParserData data, Expansion e, int count, LinePrinter printer) {
        if (internalName(e).startsWith("jj_scan_token"))
            return;

        printer.println("fn jj_3" + internal_name_as_snake_case(e) + "(&mut self) -> bool {");

        // DEPTH_LIMIT and DEBUG_LOOKAHEAD are rejected up front in generate(); the Rust back end
        // emits neither guard code nor a trace call, so no expansion is ever traced.
        Expansion jj3_expansion = null;

        printer.indent();
        phase3().emit(data, jj3_expansion, e, count, printer);
        printer.println(genReturn(jj3_expansion, false, data));
        printer.outdent();
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

    @Override
    protected String lookaheadRoutineName(Expansion e) {
        return internal_name_as_snake_case(e);
    }

    private String internal_name_as_snake_case(Expansion e) {
        return RustParserSyntax.toSnakeCase(internalName(e));
    }
}
