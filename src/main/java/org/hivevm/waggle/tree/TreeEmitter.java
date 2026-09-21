// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.tree;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.api.Options;

import java.util.Collection;

/**
 * Everything a target has to be able to write for a grammar that builds a tree: the code around one
 * node scope, and the tree runtime the scopes call into.
 *
 * <p>A back end provides one of these, or does not support trees at all
 * ({@code Generator.treeSupport()}). The three methods around a scope used to be abstract methods on
 * {@code ParserGenerator}, so every back end had to implement them whether or not it could, and the
 * parser generator itself was where tree code was woven in (ADR-0016).
 */
public interface TreeEmitter {

    /** Opens a node scope: declares the node and the flag, and starts the guarded region. */
    void openScope(NodeScope scope, String nodeClass, LinePrinter printer, Options options);

    /**
     * Closes a node scope.
     *
     * @param isFinal whether this is the close in the cleanup path, which must not clear the flag
     *                again
     */
    void closeScope(NodeScope scope, LinePrinter printer, Options options, boolean isFinal);

    /** Ends the guarded region: unwinds the scope on failure and closes it on the way out. */
    void catchBlocks(NodeScope scope, LinePrinter printer, Options options,
            Collection<String> thrown);

    /** Writes the tree runtime: the node base, the node constants, the tree state, the visitor. */
    void emitRuntime(Options options, TreeModel tree);
}
