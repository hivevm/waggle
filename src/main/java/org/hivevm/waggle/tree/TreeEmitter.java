// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.tree;

import org.hivevm.waggle.api.Options;

/**
 * Everything a target has to be able to write for a grammar that builds a tree: the options it can
 * build, and the tree runtime the scopes call into.
 *
 * <p>A back end provides one of these, or does not support trees at all
 * ({@code Generator.treeSupport()}). The code around a scope used to be written by three abstract
 * methods on {@code ParserGenerator}, then by three methods here; it is template text now
 * (apply/ScopeOpen, apply/ScopeClose and the Close* records, ADR-0031).
 *
 * <p>{@code emitRuntime} takes the typed {@link TreeOptions}, the view of the {@code NODE_*} and
 * {@code VISITOR_*} settings, and the name-keyed {@link Options}, because it renders templates and
 * reading option keys by name is the template contract (ADR-0005).
 */
public interface TreeEmitter {

    /** The tree options this target can build; every one of them by default. */
    default TreeSupport support() {
        return TreeSupport.ALL;
    }

    /** Writes the tree runtime: the node base, the node constants, the tree state, the visitor. */
    void emitRuntime(Options options, TreeOptions tree, TreeModel model);
}
