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
import org.hivevm.waggle.codegen.ParserGenerator;
import org.hivevm.waggle.codegen.ProductionModel;


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
        options.set("PROTOTYPES", data.productionPlans().stream().map(plan -> {
            var s = signature(plan.signature());
            return new ProductionModel.Prototype(s.returnType(), s.name(), s.parameters());
        }).toList());

        CppTemplate.PARSER.render(options, data.getParserName());
        CppTemplate.PARSER_H.render(options, data.getParserName());
    }
}
