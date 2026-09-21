// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

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

    /** The exception a failing scope is unwound for, e.g. {@code jjte000}. */
    static String exception(NodeScope scope) {
        return ScopeVariables.name("e", scope);
    }

    private static String name(String id, NodeScope scope) {
        var s = "000" + scope.getScopeNumber();
        return "jjt" + id + s.substring(s.length() - 3);
    }
}
