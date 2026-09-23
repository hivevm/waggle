// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.tree;

import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.model.Choice;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.OneOrMore;
import org.hivevm.waggle.model.Sequence;
import org.hivevm.waggle.model.ZeroOrMore;
import org.hivevm.waggle.model.ZeroOrOne;

import java.util.Optional;

/**
 * Decides whether a grammar builds a tree, and what the tree is.
 *
 * <p>This is a stage with an input and a result, which the walk over the productions used to lack:
 * it ran inside {@code GeneratorProvider.generate}, filling a model the back end happened to be
 * holding (ADR-0016).
 */
public interface TreeAnalyzer {

    /**
     * The tree of a grammar, or nothing.
     *
     * <p>Nothing means the grammar does not set {@code USE_AST}, or declares no {@code #Node} and
     * does not ask for the scope hooks — then no tree runtime, no node classes and no visitor are
     * written, and the parser has no tree state to thread through. Node descriptors in a grammar
     * without {@code USE_AST} are ignored with a warning (ADR-0028). The tree options are checked
     * only when a tree is built.
     */
    static Optional<TreeModel> analyze(Iterable<NormalProduction> productions, TreeOptions options,
                                       Diagnostics diagnostics) {
        var model = new TreeModel();
        productions.forEach(p -> TreeAnalyzer.collect(model, p, options));

        if (model.isEmpty() && !options.scopeHook()) {
            return Optional.empty();
        }
        if (!options.useAst()) {
            diagnostics.warning("Node descriptors and NODE_SCOPE_HOOK will be ignored since USE_AST"
                    + " is false; no tree is built");
            return Optional.empty();
        }
        options.validate(diagnostics);
        return Optional.of(model);
    }

    /**
     * Collects the nodes of a production. The order is the one in which the grammar parser meets the
     * node descriptors: a production's own node before its body, and an expansion's node after the
     * expansion it decorates ({@code ( a #X b ) #Y} yields X before Y).
     */
    private static void collect(TreeModel model, Expansion exp, TreeOptions options) {
        if (exp instanceof NormalProduction p) {
            TreeAnalyzer.add(model, p.getNodeScope(), options);
            TreeAnalyzer.collect(model, p.getExpansion(), options);
            return;
        }

        switch (exp) {
            case Choice p -> p.getChoices().forEach(e -> TreeAnalyzer.collect(model, e, options));
            case Sequence p -> p.getUnits().forEach(e -> TreeAnalyzer.collect(model, e, options));
            case OneOrMore p -> TreeAnalyzer.collect(model, p.getExpansion(), options);
            case ZeroOrMore p -> TreeAnalyzer.collect(model, p.getExpansion(), options);
            case ZeroOrOne p -> TreeAnalyzer.collect(model, p.getExpansion(), options);
            default -> {
            }
        }
        TreeAnalyzer.add(model, exp.getNodeScope(), options);
    }

    private static void add(TreeModel model, NodeScope scope, TreeOptions options) {
        if (scope == null) {
            return;
        }
        var descriptor = scope.getNodeDescriptor();
        model.addNode(descriptor);
        model.addNodeDescriptor(descriptor, options);
    }
}
