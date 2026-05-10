// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.GeneratorName;
import org.hivevm.cc.generator.GeneratorProvider;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.NodeGenerator;
import org.hivevm.cc.generator.ParserGenerator;

/**
 * The {@link CppGenerator} class.
 */
@GeneratorName("Cpp")
public class CppGenerator extends GeneratorProvider {

    @Override
    public final NodeGenerator newNodeGenerator() {
        return new CppNodeGenerator();
    }

    @Override
    public final LexerGenerator newLexerGenerator() {
        return new CppLexerGenerator();
    }

    @Override
    public final ParserGenerator newParserGenerator() {
        return new CppParserGenerator();
    }

    @Override
    protected final FileGenerator newFileGenerator() {
        return context -> {
            CppTemplate.JAVACC.render(context.options());

            CppTemplate.TOKEN.render(context.options());
            CppTemplate.TOKEN_H.render(context.options());
            CppTemplate.TOKENMANAGER.render(context.options());
            CppTemplate.TOKENNANAGERERROR.render(context.options());
            CppTemplate.TOKENNANAGERERROR_H.render(context.options());
            CppTemplate.TOKENNANAGERHANDLER.render(context.options());
            CppTemplate.TOKENNANAGERHANDLER_H.render(context.options());

            CppTemplate.READER.render(context.options());
            CppTemplate.STRINGREADER.render(context.options());
            CppTemplate.STRINGREADER_H.render(context.options());

            CppTemplate.PARSEEXCEPTION.render(context.options());
            CppTemplate.PARSEEXCEPTION_H.render(context.options());
            CppTemplate.PARSERHANDLER.render(context.options());
            CppTemplate.PARSERHANDLER_H.render(context.options());
        };
    }
}
