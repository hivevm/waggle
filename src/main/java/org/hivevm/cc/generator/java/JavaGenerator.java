// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.GeneratorName;
import org.hivevm.cc.generator.GeneratorProvider;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.NodeGenerator;
import org.hivevm.cc.generator.ParserGenerator;

import java.util.Set;

/**
 * The {@link JavaGenerator} class.
 */
@GeneratorName("Java")
public class JavaGenerator extends GeneratorProvider {

    @Override
    public final NodeGenerator newNodeGenerator() {
        return new JavaNodeGenerator();
    }

    @Override
    public final LexerGenerator newLexerGenerator() {
        return new JavaLexerGenerator();
    }

    @Override
    public final ParserGenerator newParserGenerator() {
        return new JavaParserGenerator();
    }

    @Override
    protected final FileGenerator newFileGenerator() {
        return context -> {
            JavaTemplate.PROVIDER.render(context.options());
            JavaTemplate.STRING_PROVIDER.render(context.options());
            JavaTemplate.STREAM_PROVIDER.render(context.options());
            JavaTemplate.CHAR_STREAM.render(context.options());

            JavaTemplate.TOKEN.render(context.options());
            JavaTemplate.TOKEN_EXCEPTION.render(context.options());
            JavaTemplate.PARSER_EXCEPTION.render(context.options());
        };
    }

    @Override
    protected final Set<String> reservedNames() {
        return JavaTemplate.reservedNames();
    }
}
