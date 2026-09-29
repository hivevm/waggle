// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.tree;

import org.hivevm.waggle.model.NodeDescriptor;
import org.hivevm.waggle.model.NodeScope;

/**
 * One node scope as the parser writes it, decided once: the locals it declares, the node it
 * creates and when that node is kept. The tree decorator used to decide the node class on every
 * scope it opened, and each back end read the arity out of the descriptor text as it printed the
 * close — Rust by testing whether the text is a number (ADR-0029).
 *
 * @param nodeVar   the node local, e.g. {@code jjtn000}
 * @param closedVar the "scope is still open" local, e.g. {@code jjtc000}
 * @param nodeId    the node's constant, e.g. {@code JJTNAME}
 * @param nodeClass the class of the node
 * @param arity     when the node is kept
 */
public record ScopePlan(String nodeVar, String closedVar, String nodeId, String nodeClass,
                        Arity arity) {

    /** When a closing scope keeps its node, as the descriptor's parentheses say. */
    public sealed interface Arity {

        /** Always: the descriptor has no parentheses. */
        record Always() implements Arity {
        }

        /** When it has exactly {@code text} children: an integer literal, as written. */
        record Count(String text) implements Arity {
        }

        /** When the condition {@code text} holds, as written. */
        record Condition(String text) implements Arity {
        }

        /** When it has more than {@code text} children: {@code #Name(>text)}, as written. */
        record GreaterThan(String text) implements Arity {
        }
    }

    /** The plan of {@code scope}. */
    static ScopePlan of(NodeScope scope, TreeOptions options) {
        var descriptor = scope.getNodeDescriptor();
        return new ScopePlan(ScopeVariables.node(scope), ScopeVariables.closed(scope),
                descriptor.getNodeId(),
                NodeDescriptor.getNodeClass(descriptor.getName(), options.multi(),
                        options.nodeClass()),
                ScopePlan.arity(descriptor));
    }

    private static Arity arity(NodeDescriptor descriptor) {
        var text = descriptor.getText();
        if (text == null) {
            return new Arity.Always();
        }
        if (descriptor.isGt()) {
            return new Arity.GreaterThan(text);
        }
        return text.strip().matches("\\d+") ? new Arity.Count(text) : new Arity.Condition(text);
    }
}
