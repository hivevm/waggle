// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/JavaCodeGenerator.java, org/javacc/jjtree/CPPCodeGenerator.java

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.codegen.ListModel;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.tree.TreeModel;
import org.hivevm.waggle.tree.TreeOptions;
import org.hivevm.waggle.tree.TreeSupport;

/**
 * Rust's tree support: the code around one node scope, and the tree runtime it calls into.
 *
 * <p>Rust has no templates for per-node classes (NODE_MULTI) or visitors (VISITOR) — the ones it had
 * were unported Java leftovers under names that did not exist, so such a grammar died with "Invalid
 * template name". Both are rejected up front with a message that says what is missing, as
 * DEPTH_LIMIT is, and so are NODE_FACTORY and TRACK_TOKENS, which came out as Java.
 */
public class RustTreeEmitter implements TreeEmitter {

    /** The Rust nodes build none of these; the options would otherwise come out as Java. */
    @Override
    public TreeSupport support() {
        return new TreeSupport(false, false, false, false);
    }

    @Override
    public void emitRuntime(Options context, TreeOptions tree, TreeModel data) {
        RustTemplate.TREE_STATE.render(context);
        generateTreeConstants(context, data);
        RustTemplate.NODE.render(context);
    }

    private void generateTreeConstants(Options context, TreeModel data) {
        var options = OptionsContext.of(context);
        options.set("NODES", ListModel.numbered(data.getNodeIds(), name -> name));
        options.set("NODE_NAMES", ListModel.names(data.getNodeNames()));
        options.set("NODE_COUNT", data.getNodeIds().size());

        RustTemplate.TREE_CONSTANTS.render(options);
    }
}
