// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.java;

import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.codegen.GeneratorName;
import org.hivevm.waggle.codegen.GeneratorProvider;
import org.hivevm.waggle.codegen.LexerGenerator;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.codegen.ParserGenerator;

import java.util.Optional;
import java.util.Set;

/**
 * The {@link JavaGenerator} class.
 */
@GeneratorName("Java")
public class JavaGenerator extends GeneratorProvider {

    @Override
    public final Optional<TreeEmitter> treeSupport() {
        return Optional.of(new JavaTreeEmitter());
    }

    @Override
    protected final LexerGenerator newLexerGenerator() {
        return new JavaLexerGenerator();
    }

    @Override
    protected final ParserGenerator newParserGenerator() {
        return new JavaParserGenerator();
    }

    @Override
    protected final void emitRuntime(Options options) {
        JavaTemplate.PROVIDER.render(options);
        JavaTemplate.STRING_PROVIDER.render(options);
        JavaTemplate.STREAM_PROVIDER.render(options);
        JavaTemplate.CHAR_STREAM.render(options);

        JavaTemplate.TOKEN.render(options);
        JavaTemplate.TOKEN_EXCEPTION.render(options);
        JavaTemplate.PARSER_EXCEPTION.render(options);
    }

    @Override
    protected final Set<String> reservedNames() {
        return JavaTemplate.SET.reservedNames();
    }
}
