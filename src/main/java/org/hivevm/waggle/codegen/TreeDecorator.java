// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.tree.ScopePlan;
import org.hivevm.waggle.tree.TreeModel;
import org.hivevm.waggle.tree.TreeOptions;


/**
 * The tree-building decorator: opens a node scope before the expansion it annotates and unwinds it
 * afterwards. Only a target with tree support gets one; the templates write the scope.
 */
final class TreeDecorator implements ExpansionDecorator {

    private final TreeOptions options;
    private final TreeModel model;

    TreeDecorator(TreeOptions options, TreeModel model) {
        this.options = options;
        this.model = model;
    }

    @Override
    public ScopeModel.ScopeOpen open(NodeScope scope, boolean production) {
        var plan = this.model.scope(scope);
        // NODE_FACTORY "*" makes every node through its own class; a named factory makes them all.
        var factory = this.options.nodeFactory().equals("*") ? plan.nodeClass()
                : this.options.nodeFactory();
        return new ScopeModel.ScopeOpen(production, scope.getNodeDescriptorText(),
                plan.nodeClass(), plan.nodeVar(), plan.closedVar(), plan.nodeId(), factory,
                this.options.scopeHook(), this.options.trackTokens());
    }

    @Override
    public ScopeModel.ScopeClose close(NodeScope scope) {
        var plan = this.model.scope(scope);
        return new ScopeModel.ScopeClose(plan.nodeVar(), plan.closedVar(),
                close(plan.arity()), this.options.scopeHook(), this.options.trackTokens());
    }

    /** How a scope closes under {@code arity}. */
    private static ScopeModel.Close close(ScopePlan.Arity arity) {
        return switch (arity) {
            case ScopePlan.Arity.Always a -> new ScopeModel.CloseAlways();
            case ScopePlan.Arity.GreaterThan a ->
                    new ScopeModel.CloseAbove(a.text(), a.text().strip());
            case ScopePlan.Arity.Count a -> new ScopeModel.CloseCount(a.text(), a.text().strip());
            case ScopePlan.Arity.Condition a -> new ScopeModel.CloseIf(a.text());
        };
    }
}
