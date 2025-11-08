// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import org.hivevm.cc.generator.LexerBuilder;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.parser.JavaCCData;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.JavaCCParser;
import org.hivevm.cc.parser.JavaCCParserDefault;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.StringProvider;
import org.hivevm.cc.semantic.Semanticize;

public class ParserInterpreter {

    private final HiveCCOptions options;


    /**
     *
     */
    public ParserInterpreter(HiveCCOptions options) {
        this.options = options;
    }

    public void runTokenizer(String grammar, String input) {
        JavaCCErrors.reInit();
        try {
            JavaCCData request = new JavaCCData(false, this.options);

            JavaCCParser parser = new JavaCCParserDefault(new StringProvider(grammar), this.options);
            parser.initialize(request);
            parser.javacc_input();

            Semanticize.semanticize(request, this.options);

            if (JavaCCErrors.get_error_count() == 0) {
                LexerData data = new LexerBuilder().build(request);
                ParserInterpreter.tokenize(data, input, this.options);
            }
        }
        catch (Exception e) {
            System.out.println(e);
            System.out.println("Detected " + (JavaCCErrors.get_error_count() + 1) + " errors and "
                    + JavaCCErrors.get_warning_count() + " warnings.");
            System.exit(1);
        }
    }

    public static void tokenize(LexerData data, String input, Options options) {
        // First match the string literals.
        final int input_size = input.length();
        int curPos = 0;
        int curLexState = data.defaultLexState();
        while (curPos < input_size) {
            char c = input.charAt(curPos);
            if (options.getIgnoreCase())
                c = Character.toLowerCase(c);
            int key = curLexState << 16 | (int) c;
        }
        System.err.println("Matched EOF");
    }
}
