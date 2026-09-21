// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.analysis.ParserPlanner;

import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.api.ParserRequest;
import org.hivevm.waggle.lexer.LexerBuilder;
import org.hivevm.waggle.tree.TreeAnalyzer;
import org.hivevm.waggle.tree.TreeModel;
import org.hivevm.waggle.tree.TreeOptions;

import java.text.ParseException;
import java.util.Optional;
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
     * Whether the tree runtime — the node base, the node constants and the tree state — is written
     * for a grammar that has a tree. Any node needs it, not only NODE_MULTI's node classes: a
     * grammar with "#Name" but without NODE_MULTI used to reference Node, NodeType and NodeState
     * without generating them. A back end may narrow this.
     */
    protected boolean generatesTreeRuntime(TreeModel tree, TreeOptions options) {
        return true;
    }

    /**
     * Generates the parser files.
     */
    @Override
    public final void generate(ParserRequest request) throws ParseException {
        prepare(request);

        var treeOptions = TreeOptions.from(request.options());
        var tree = TreeAnalyzer.analyze(request.getNormalProductions(), treeOptions);
        tree.ifPresent(t -> treeOptions.validate(request.diagnostics()));

        var dataLexer = new LexerBuilder().build(request);
        var dataParser = new ParserPlanner().build(request, tree);

        checkNamesAreFree(request.getParserName(), tree);

        var emitter = tree.isPresent() ? treeSupport() : Optional.<TreeEmitter>empty();
        if (tree.isPresent() && emitter.isEmpty()) {
            throw new ParseException("Tree building (#Node) is not supported for this target.", 0);
        }
        if (emitter.isPresent() && generatesTreeRuntime(tree.get(), treeOptions)) {
            emitter.get().emitRuntime(request.options(), tree.get());
        }

        newFileGenerator().generate(dataLexer);
        if (!request.diagnostics().hasError()) {
            newLexerGenerator().generate(dataLexer);

            var parserGenerator = newParserGenerator();
            parserGenerator.decorateWith(emitter
                    .<ExpansionDecorator>map(e -> new TreeDecorator(e, request.options()))
                    .orElse(ExpansionDecorator.NONE));
            parserGenerator.generate(dataParser);
        }
    }

    /** Refuses to generate anything that would overwrite one of the runtime classes. */
    private void checkNamesAreFree(String parserName, Optional<TreeModel> tree)
            throws ParseException {
        var reserved = reservedNames();

        if (parserNameIsFileName() && reserved.contains(parserName)) {
            throw new ParseException("The grammar may not be named '" + parserName
                    + "': the generated parser would overwrite the runtime class of the same name."
                    + " Reserved: " + reserved.stream().sorted().toList(), 0);
        }

        for (var node : tree.map(TreeModel::getNodesToGenerate).orElse(Set.of())) {
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
