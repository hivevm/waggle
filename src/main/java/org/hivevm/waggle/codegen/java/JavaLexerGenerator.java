// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java, org/javacc/parser/TableDrivenJavaCodeGenerator.java

package org.hivevm.waggle.codegen.java;

import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.codegen.LexerGenerator;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.source.LinePrinter;
import org.hivevm.source.SourceProvider;

/**
 * Generate lexer.
 */
class JavaLexerGenerator extends LexerGenerator {

    public JavaLexerGenerator() {
        super(Language.JAVA);
    }

    @Override
    protected final void generate(LexerData data, OptionsContext options) {
        options.set("STATES_FOR_STATE", () -> getStatesForState(data));
        options.set("KIND_FOR_STATE", () -> getNextToken().getKindForState(data));
        options.set("DUMP_LITERAL_IMAGES", p -> dump_literal_images(data, p));

        JavaTemplate.LEXER.render(options);
    }

    protected SourceProvider<Options> getConstantsTemplate() {
        return JavaTemplate.PARSER_CONSTANTS;
    }

    private static void dump_literal_images(LexerData data, LinePrinter printer) {
        LexerGenerator.printLiteralImages(data, printer, false, (kind, image) -> {
            if (image == null) {
                return "null, ";
            }

            var toPrint = new StringBuilder("\"");
            for (int j = 0; j < image.length(); j++) {
                if (image.charAt(j) <= 0xff) {
                    toPrint.append("\\").append(Integer.toOctalString(image.charAt(j)));
                } else {
                    String hexVal = Integer.toHexString(image.charAt(j));
                    if (hexVal.length() == 3) {
                        hexVal = "0" + hexVal;
                    }
                    toPrint.append("\\u").append(hexVal);
                }
            }
            return toPrint.append("\", ").toString();
        });
    }

}
