// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.text.ParseException;
import java.util.ServiceLoader;

import org.hivevm.cc.Language;
import org.hivevm.cc.ParserRequest;
import org.hivevm.cc.jjtree.ASTGrammar;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeVisitor;
import org.hivevm.cc.parser.Options;

/**
 * The {@link GeneratorProvider} class.
 */
public abstract class GeneratorProvider implements Generator {

    protected abstract TreeGenerator newTreeGenerator();

    protected abstract FileGenerator newFileGenerator();

    protected abstract NodeGenerator newNodeGenerator();

    protected abstract LexerGenerator newLexerGenerator();

    protected abstract ParserGenerator newParserGenerator();

    /**
     * Generates the parser files.
     */
    @Override
    public final void generate(ParserRequest request) throws ParseException {
        var dataLexer = new LexerBuilder().build(request);
        var dataParser = new ParserBuilder().build(request);
        var dataNode = dataParser.getNodeData();

        dataParser.getProductions().forEach(e -> dataNode.parseExpansion(e, request.options()));
        if (!dataNode.getNodesToGenerate().isEmpty()) {
            newNodeGenerator().generate(request.options(), dataNode);
        }

        newFileGenerator().generate(dataLexer);
        newLexerGenerator().generate(dataLexer);
        newParserGenerator().generate(dataParser);
    }

    /**
     * Generates the Abstract Syntax Tree.
     */
    @Override
    public final void generateAST(ASTGrammar node, ASTWriter writer, Options options) {
        var generator = newTreeGenerator();
        node.jjtAccept(new JJTreeVisitor(generator), writer);
        newNodeGenerator().generate(options, generator.getData());
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
