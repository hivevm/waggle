// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.waggle.codegen.ParserSyntax;
import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.analysis.ParserPlan;
import org.hivevm.waggle.analysis.ProductionPlan.Signature;
import org.hivevm.waggle.codegen.ParserGenerator;
import org.hivevm.waggle.codegen.ProductionModel;
import org.hivevm.waggle.grammar.Token;


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
        options.add("PROTOTYPES", data.productionPlans().stream().map(plan -> {
            var n = plan.signature();
            return new ProductionModel.Prototype((n.returnType() == null) ? "void" : n.returnType(),
                    n.name(), n.parameters().isEmpty() ? "" : code(n.parameters(), null));
        }).toList());

        CppTemplate.PARSER.render(options, data.getParserName());
        CppTemplate.PARSER_H.render(options, data.getParserName());
    }

    @Override
    protected ProductionModel.Signature signature(Signature p) {
        Token t = p.head().first();
        var comments = cursorAt(t);
        var leading = comments.leadingComments(t);
        return new ProductionModel.Signature(leading,
                (p.returnType() == null) ? "void" : p.returnType(), comments.trailingComments(t),
                p.name(), p.parameters().isEmpty() ? "" : code(p.parameters(), null));
    }


}
