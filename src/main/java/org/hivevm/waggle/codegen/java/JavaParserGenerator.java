// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.codegen.java;

import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.analysis.ParserPlan;
import org.hivevm.waggle.codegen.ParserGenerator;

import java.util.List;
import org.hivevm.waggle.codegen.ListModel;


/**
 * Implements the {@link ParserGenerator} for the JAVA language.
 */
class JavaParserGenerator extends ParserGenerator {

    public JavaParserGenerator() {
        super(Language.JAVA);
    }

    @Override
    protected final void generate(ParserPlan data, OptionsContext options) {
        // WaggleOptions keeps the imports as the list of names it split them into.
        var imports = (List<?>) data.options().get(Waggle.JAVA_IMPORTS);
        options.set(Waggle.JAVA_IMPORTS,
                imports.stream().map(i -> new ListModel.Name(i.toString())).toList());

        JavaTemplate.PARSER.render(options);
    }
}
