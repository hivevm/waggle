// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.tree;

import org.hivevm.waggle.model.NodeScope;

/**
 * The two placeholders a grammar action may use inside a node scope.
 *
 * <p>{@code $NODE} is the node the enclosing scope opened and {@code $BOOL} the flag that says
 * whether that scope is still open — the locals a {@link TreeEmitter} declares. An action written
 * against them keeps working whatever the back end calls those locals.
 *
 * <p>This used to live in {@code generator/CodeGenerator}, which is why JJTree — the reference
 * consumer of tree building — had to reach into a back end to rewrite an action (ADR-0016).
 */
public interface ActionRewriter {

    String NODE = "$NODE";
    String CLOSED = "$BOOL";

    /** Whether the text mentions either placeholder at all. */
    static boolean rewrites(String text) {
        return text.contains(ActionRewriter.CLOSED) || text.contains(ActionRewriter.NODE);
    }

    /** Replaces the placeholders with the locals of {@code scope}. */
    static String rewrite(String text, NodeScope scope) {
        return text.replace(ActionRewriter.CLOSED, ScopeVariables.closed(scope))
                .replace(ActionRewriter.NODE, ScopeVariables.node(scope));
    }
}
