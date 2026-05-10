// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.Language;
import org.hivevm.cc.ParserRequest;
import org.hivevm.cc.lexer.LexerBuilder;
import org.hivevm.cc.parser.JavaCCErrors;

import java.text.ParseException;
import java.util.ServiceLoader;

/**
 * The {@link GeneratorProvider} class.
 */
public abstract class GeneratorProvider implements Generator {

    protected abstract FileGenerator newFileGenerator();

    /**
     * Generates the parser files.
     */
    @Override
    public final void generate(ParserRequest request) throws ParseException {
        var dataLexer = new LexerBuilder().build(request);
        var dataParser = new ParserBuilder().build(request);
        var dataNode = dataParser.getNodeData();

        dataParser.getProductions().forEach(e -> dataNode.parseExpansion(e, request.options()));
        if (!dataNode.getNodesToGenerate().isEmpty() || dataParser.options().getNodeScopeHook()) {
            newNodeGenerator().generate(request.options(), dataNode);
        }

        newFileGenerator().generate(dataLexer);
        if (!JavaCCErrors.hasError()) {
            newLexerGenerator().generate(dataLexer);
            newParserGenerator().generate(dataParser);
        }
    }

    /**
     * Lookups for a {@link Generator} for the provided language.
     */
    public static Generator generatorFor(Language language) {
        var loader = ServiceLoader.load(Generator.class);
        var provider = loader.stream();
        provider = provider.filter(p -> p.type().isAnnotationPresent(GeneratorName.class)
                && p.type().getAnnotation(GeneratorName.class).value()
                .equalsIgnoreCase(language.name()));
        return provider.findFirst().orElseThrow().get();
    }
}
