// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.codegen.ParserSyntax;
import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.analysis.ParserPlan;
import org.hivevm.waggle.analysis.ProductionPlan.Signature;
import org.hivevm.waggle.codegen.ParserGenerator;
import org.hivevm.waggle.codegen.ProductionModel;
import org.hivevm.waggle.grammar.Token;

/**
 * Implements the {@link ParserGenerator} for the RUST language, in the shape ADR-0030 gives the
 * generated parser: failure is a {@code Result}, the tokens are indices into one buffer, and a
 * production is a method.
 */
class RustParserGenerator extends ParserGenerator {

    /** The methods of the generated parser that a production must not be named as. */
    public RustParserGenerator() {
        super(Language.RUST);
    }

    @Override
    protected ParserSyntax newParserSyntax() {
        return new RustParserSyntax();
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
    protected ProductionModel.Signature signature(Signature p) {
        Token t = p.head().first();
        var comments = cursorAt(t);
        var leading = comments.leadingComments(t);
        var parameters = new StringBuilder();
        for (var parameter : p.split()) {
            parameters.append(", ").append(RustIdentifier.of(parameter.name())).append(": ")
                    .append(parameter.type());
        }
        return new ProductionModel.Signature(leading,
                (p.returnType() == null) ? "()" : p.returnType(), comments.trailingComments(t),
                RustIdentifier.of(RustParserSyntax.toSnakeCase(p.name())), parameters.toString());
    }

    @Override
    protected String lookaheadRoutineName(String name) {
        return RustParserSyntax.toSnakeCase(name);
    }
}
