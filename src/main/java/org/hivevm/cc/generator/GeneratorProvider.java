// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.Language;
import org.hivevm.cc.ParserRequest;
import org.hivevm.cc.lexer.LexerBuilder;
import org.hivevm.cc.parser.JavaCCErrors;

import java.text.ParseException;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * The {@link GeneratorProvider} class.
 */
public abstract class GeneratorProvider implements Generator {

    protected abstract FileGenerator newFileGenerator();

    /** Lets a back end fill in defaults that only it can know. */
    protected void prepare(ParserRequest request) {
    }

    /**
     * The names this back end writes runtime classes under. A generated parser or AST node that
     * carries one of these names overwrites the runtime class, and the output stops compiling — the
     * C++ back end did exactly that for a grammar named {@code Tree}.
     */
    protected Set<String> reservedNames() {
        return Set.of();
    }

    /**
     * Whether the grammar's name becomes a file name. It does in C++ ({@code Tree.h}); Java writes
     * fixed names and Rust puts everything into a directory named after the grammar, so neither can
     * collide this way.
     */
    protected boolean parserNameIsFileName() {
        return false;
    }

    /**
     * Generates the parser files.
     */
    @Override
    public final void generate(ParserRequest request) throws ParseException {
        prepare(request);

        var dataLexer = new LexerBuilder().build(request);
        var dataParser = new ParserBuilder().build(request);
        var dataNode = dataParser.getNodeData();

        dataParser.getProductions().forEach(e -> dataNode.parseExpansion(e, request.options()));

        checkNamesAreFree(request.getParserName(), dataNode);
        if (!dataNode.getNodesToGenerate().isEmpty() || dataParser.options().getNodeScopeHook()) {
            newNodeGenerator().generate(request.options(), dataNode);
        }

        newFileGenerator().generate(dataLexer);
        if (!JavaCCErrors.hasError()) {
            newLexerGenerator().generate(dataLexer);
            newParserGenerator().generate(dataParser);
        }
    }

    /** Refuses to generate anything that would overwrite one of the runtime classes. */
    private void checkNamesAreFree(String parserName, NodeData nodes)
            throws ParseException {
        var reserved = reservedNames();

        if (parserNameIsFileName() && reserved.contains(parserName)) {
            throw new ParseException("The grammar may not be named '" + parserName
                    + "': the generated parser would overwrite the runtime class of the same name."
                    + " Reserved: " + reserved.stream().sorted().toList(), 0);
        }

        for (var node : nodes.getNodesToGenerate()) {
            if (reserved.contains(node)) {
                throw new ParseException("The AST node '" + node
                        + "' would overwrite the runtime class of the same name."
                        + " Reserved: " + reserved.stream().sorted().toList(), 0);
            }
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
