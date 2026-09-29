// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.analysis.PlanningProfile;
import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.api.ParserRequest;
import org.hivevm.waggle.codegen.GeneratorName;
import org.hivevm.waggle.codegen.GeneratorProvider;
import org.hivevm.waggle.codegen.LexerGenerator;
import org.hivevm.waggle.tree.TreeModel;
import org.hivevm.waggle.tree.TreeOptions;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.codegen.ParserGenerator;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The {@link RustGenerator} class.
 */
@GeneratorName("Rust")
public class RustGenerator extends GeneratorProvider {

    @Override
    public final Optional<TreeEmitter> treeSupport() {
        return Optional.of(new RustTreeEmitter());
    }

    @Override
    protected final LexerGenerator newLexerGenerator() {
        return new RustLexerGenerator();
    }

    @Override
    protected final ParserGenerator newParserGenerator() {
        return new RustParserGenerator();
    }

    /**
     * Every {@code use crate::…} in the templates goes through RUST_MODULE, and it defaulted to the
     * empty string — which rendered as {@code use crate::::charstream}, so nothing compiled unless
     * the option was set by hand. The generated files land in a directory named after the grammar,
     * so that directory is the module.
     */
    @Override
    protected final void prepare(ParserRequest request) {
        var options = request.options();
        if (options.stringValue(Waggle.RUST_MODULE).isEmpty()) {
            options.set(Waggle.RUST_MODULE,
                    RustIdentifier.of(request.getParserName().toLowerCase(Locale.ROOT)));
        }
    }

    /**
     * A Rust project that uses "#Name" nodes supplies node.rs, treestate.rs and treeconstants.rs
     * itself, and generation has always left them alone: they are written only for NODE_MULTI or
     * NODE_SCOPE_HOOK, as before. Writing them for every node would overwrite the project's own.
     */
    @Override
    protected final boolean generatesTreeRuntime(TreeModel tree, TreeOptions options) {
        return !tree.getNodesToGenerate().isEmpty() || options.scopeHook();
    }

    @Override
    protected final void emitRuntime(Options options) {
        RustTemplate.TOKEN.render(options);
        RustTemplate.CHAR_STREAM.render(options);
    }

    @Override
    protected final Set<String> reservedNames() {
        return RustTemplate.SET.reservedNames();
    }

    /**
     * The Rust parser has no DEPTH_LIMIT guard and no traces yet: ADR-0030 ports them separately,
     * and a parser without the guard the grammar asked for would not be honest.
     */
    @Override
    protected final PlanningProfile planningProfile() {
        return new PlanningProfile("Rust", true, true, false, false);
    }

    /** A production is a method of the parser, in snake case. */
    @Override
    protected final String methodName(String production) {
        return RustParserSyntax.toSnakeCase(production);
    }

    /** The methods the parser has already: its API, and every {@code jj_} helper. */
    @Override
    protected final boolean isRuntimeMethod(String name) {
        return RustGenerator.PARSER_METHODS.contains(name) || name.startsWith("jj_");
    }

    private static final Set<String> PARSER_METHODS = Set.of(
            "new", "from_lexer", "root_node", "get_next_token", "get_token", "lexer",
            "jjtree_open_node_scope", "jjtree_close_node_scope");
}
