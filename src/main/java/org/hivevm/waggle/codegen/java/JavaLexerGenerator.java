// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.java;

import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.codegen.LexerGenerator;
import org.hivevm.waggle.codegen.TargetSyntax;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.source.Context;
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
    protected final void generate(LexerData data, Context options) {
        options.set("STATES_FOR_STATE", () -> getStatesForState(data));
        options.set("KIND_FOR_STATE", () -> getNextToken().getKindForState(data));
        options.set("DUMP_LITERAL_IMAGES", p -> dump_literal_images(data, p));

        JavaTemplate.LEXER.render(options);
    }

    protected SourceProvider getConstantsTemplate() {
        return JavaTemplate.PARSER_CONSTANTS;
    }

    private static void dump_literal_images(LexerData data, LinePrinter printer) {
        if (data.getImageCount() <= 0) {
            return;
        }

        String image;
        int i;
        int charCnt = 0;

        for (i = 0; i < data.getImageCount(); i++) {
            if ((image = data.getImage(i)) == null) {
                if ((charCnt += 6) > 80) {
                    printer.println();
                    charCnt = 0;
                }

                printer.print("null, ");
                continue;
            }

            String toPrint = "\"";
            for (int j = 0; j < image.length(); j++) {
                if (image.charAt(j) <= 0xff) {
                    toPrint += ("\\" + Integer.toOctalString(image.charAt(j)));
                } else {
                    String hexVal = Integer.toHexString(image.charAt(j));
                    if (hexVal.length() == 3) {
                        hexVal = "0" + hexVal;
                    }
                    toPrint += ("\\u" + hexVal);
                }
            }

            toPrint += ("\", ");

            if ((charCnt += toPrint.length()) >= 80) {
                printer.println();
                charCnt = 0;
            }

            printer.print(toPrint);
        }

        while (++i < data.maxOrdinal()) {
            if ((charCnt += 6) > 80) {
                printer.println();
                charCnt = 0;
            }

            printer.print("null, ");
        }
    }

}
