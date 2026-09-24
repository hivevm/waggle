// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.waggle.codegen.FileGenerator;
import org.hivevm.waggle.codegen.GeneratorName;
import org.hivevm.waggle.codegen.GeneratorProvider;
import org.hivevm.waggle.codegen.LexerGenerator;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.codegen.ParserGenerator;

import java.util.Optional;
import java.util.Set;

/**
 * The {@link CppGenerator} class.
 */
@GeneratorName("Cpp")
public class CppGenerator extends GeneratorProvider {

    @Override
    public final Optional<TreeEmitter> treeSupport() {
        return Optional.of(new CppTreeEmitter());
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
            CppTemplate.WAGGLE.render(context.options());

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

    @Override
    protected final Set<String> reservedNames() {
        return CppTemplate.reservedNames();
    }

    @Override
    protected final boolean parserNameIsFileName() {
        return true;
    }
}
