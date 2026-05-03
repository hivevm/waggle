// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.GeneratorName;
import org.hivevm.cc.generator.GeneratorProvider;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.NodeGenerator;
import org.hivevm.cc.generator.ParserGenerator;
import org.hivevm.cc.generator.TreeGenerator;

/**
 * The {@link CppGenerator} class.
 */
@GeneratorName("Cpp")
public class CppGenerator extends GeneratorProvider {

    @Override
    protected final LexerGenerator newLexerGenerator() {
        return new CppLexerGenerator();
    }

    @Override
    protected final ParserGenerator newParserGenerator() {
        return new CppParserGenerator();
    }

    @Override
    protected final TreeGenerator newTreeGenerator() {
        return new CppTreeGenerator();
    }

    @Override
    protected final NodeGenerator newNodeGenerator() {
        return new CppNodeGenerator();
    }

    @Override
    protected final FileGenerator newFileGenerator() {
        return context -> {
            CppSources.JAVACC.render(context.options());

            CppSources.TOKEN.render(context.options());
            CppSources.TOKEN_H.render(context.options());
            CppSources.TOKENMANAGER.render(context.options());
            CppSources.TOKENNANAGERERROR.render(context.options());
            CppSources.TOKENNANAGERERROR_H.render(context.options());
            CppSources.TOKENNANAGERHANDLER.render(context.options());
            CppSources.TOKENNANAGERHANDLER_H.render(context.options());

            CppSources.READER.render(context.options());
            CppSources.STRINGREADER.render(context.options());
            CppSources.STRINGREADER_H.render(context.options());

            CppSources.PARSEEXCEPTION.render(context.options());
            CppSources.PARSEEXCEPTION_H.render(context.options());
            CppSources.PARSERHANDLER.render(context.options());
            CppSources.PARSERHANDLER_H.render(context.options());
        };
    }
}
