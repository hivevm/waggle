// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.waggle.analysis.PlanningProfile;
import org.hivevm.waggle.api.Options;
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
    protected final LexerGenerator newLexerGenerator() {
        return new CppLexerGenerator();
    }

    @Override
    protected final ParserGenerator newParserGenerator() {
        return new CppParserGenerator();
    }

    @Override
    protected final void emitRuntime(Options options) {
        CppTemplate.WAGGLE.render(options);

        CppTemplate.TOKEN.render(options);
        CppTemplate.TOKEN_H.render(options);
        CppTemplate.TOKENMANAGER.render(options);
        CppTemplate.TOKENNANAGERERROR.render(options);
        CppTemplate.TOKENNANAGERERROR_H.render(options);
        CppTemplate.TOKENNANAGERHANDLER.render(options);
        CppTemplate.TOKENNANAGERHANDLER_H.render(options);

        CppTemplate.READER.render(options);
        CppTemplate.STRINGREADER.render(options);
        CppTemplate.STRINGREADER_H.render(options);

        CppTemplate.PARSEEXCEPTION.render(options);
        CppTemplate.PARSEEXCEPTION_H.render(options);
        CppTemplate.PARSERHANDLER.render(options);
        CppTemplate.PARSERHANDLER_H.render(options);
    }

    /**
     * A C++ parser records no expected tokens: it reports an error through its
     * ParserErrorHandler, which names the token it found and not the ones it expected. Its
     * DEPTH_LIMIT guard returns a value on error, so a void production goes without one.
     */
    @Override
    protected final PlanningProfile planningProfile() {
        return new PlanningProfile("C++", false, false, true, true);
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
