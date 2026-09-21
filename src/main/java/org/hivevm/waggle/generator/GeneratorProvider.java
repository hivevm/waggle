// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.generator;

import org.hivevm.waggle.Language;
import org.hivevm.waggle.ParserRequest;
import org.hivevm.waggle.lexer.LexerBuilder;
import org.hivevm.waggle.parser.Options;

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
     * Whether the tree runtime — the node base, the node constants and the tree state — is written.
     * Any node needs it, not only NODE_MULTI's node classes: a grammar with "#Name" but without
     * NODE_MULTI used to reference Node, NodeType and NodeState without generating them.
     */
    protected boolean generatesTreeRuntime(NodeData nodes, Options options) {
        return nodes.usesTree() || options.getNodeScopeHook();
    }

    /**
     * Generates the parser files.
     */
    @Override
    public final void generate(ParserRequest request) throws ParseException {
        prepare(request);

        var dataLexer = new LexerBuilder().build(request);
        var dataParser = new ParserPlanner().build(request);
        var dataNode = dataParser.getNodeData();

        dataParser.getProductions().forEach(e -> dataNode.parseExpansion(e, request.options()));

        checkNamesAreFree(request.getParserName(), dataNode);
        if (generatesTreeRuntime(dataNode, dataParser.options())) {
            newNodeGenerator().generate(request.options(), dataNode);
        }

        newFileGenerator().generate(dataLexer);
        if (!request.diagnostics().hasError()) {
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
        return provider.findFirst().orElseThrow(() -> new IllegalStateException(
                "No code generator is registered for " + language)).get();
    }
}
