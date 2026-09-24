// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/NodeScope.java, org/javacc/jjtree/TokenUtils.java

package org.hivevm.waggle.tree;

import org.hivevm.waggle.model.NodeScope;

/**
 * The names a generated parser gives the three locals of one node scope: the node itself, the flag
 * that says whether the scope is still open, and the exception a failing scope is cleaned up for.
 *
 * <p>All three targets use the same names — {@code jjtn000}, {@code jjtc000}, {@code jjte000} — so
 * this is shared tree vocabulary rather than target syntax, and it lives here once instead of three
 * times in the back ends (ADR-0016). It used to live in {@code model/NodeScope}, which put generated
 * identifiers into the one layer that is supposed to know nothing about generated code (ADR-0013).
 */
public interface ScopeVariables {

    /** The node of the scope, e.g. {@code jjtn000}. */
    static String node(NodeScope scope) {
        return ScopeVariables.name("n", scope);
    }

    /** The "scope is still open" flag, e.g. {@code jjtc000}. */
    static String closed(NodeScope scope) {
        return ScopeVariables.name("c", scope);
    }

    /** The tree-runtime call that opens the scope; the same in every target that builds trees. */
    static String openCall(NodeScope scope) {
        return "jjtree.openNodeScope(" + ScopeVariables.node(scope) + ");";
    }

    /** The tree-runtime call that closes the scope, under the descriptor's arity condition. */
    static String closeCall(NodeScope scope) {
        var node = ScopeVariables.node(scope);
        var descriptor = scope.getNodeDescriptor();
        if (descriptor.getText() == null) {
            return "jjtree.closeNodeScope(" + node + ", true);";
        }
        return descriptor.isGt()
                ? "jjtree.closeNodeScope(" + node + ", jjtree.nodeArity() >" + descriptor.getText() + ");"
                : "jjtree.closeNodeScope(" + node + ", " + descriptor.getText() + ");";
    }

    private static String name(String id, NodeScope scope) {
        var s = "000" + scope.getScopeNumber();
        return "jjt" + id + s.substring(s.length() - 3);
    }
}
