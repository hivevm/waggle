// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.tree;

import org.hivevm.waggle.api.GenerationException;

/**
 * The tree options a target can build. It is checked for every grammar that builds a tree, before
 * anything is written, whether or not the target writes a runtime for it; an option a target
 * cannot build would otherwise come out as another language, or as code that does not compile
 * (SPECIFICATION.md §3: target feature gaps are tracked, not silently produced).
 *
 * @param visitor     whether it writes VISITOR's visitor
 * @param nodeFactory whether its nodes can be made through NODE_FACTORY
 * @param trackTokens whether its nodes record TRACK_TOKENS' first and last token
 * @param nodeClasses whether it writes the node classes of NODE_MULTI with BUILD_NODE_FILES
 */
public record TreeSupport(boolean visitor, boolean nodeFactory, boolean trackTokens,
                          boolean nodeClasses) {

    /** A target that builds every tree option. */
    public static final TreeSupport ALL = new TreeSupport(true, true, true, true);

    /** Refuses the first option {@code tree} asks for that this target cannot build. */
    public void refuseUnsupported(TreeOptions tree, TreeModel model, String target) {
        if (!this.visitor && tree.visitor()) {
            throw TreeSupport.unsupported("VISITOR is", target);
        }
        if (!this.nodeFactory && !tree.nodeFactory().isEmpty()) {
            throw TreeSupport.unsupported("NODE_FACTORY is", target);
        }
        if (!this.trackTokens && tree.trackTokens()) {
            throw TreeSupport.unsupported("TRACK_TOKENS is", target);
        }
        var excludes = tree.customNodes();
        if (!this.nodeClasses && tree.buildNodeFiles()
                && model.getNodesToGenerate().stream().anyMatch(n -> !excludes.contains(n))) {
            throw TreeSupport.unsupported("Node classes (NODE_MULTI with BUILD_NODE_FILES) are",
                    target);
        }
    }

    private static GenerationException unsupported(String what, String target) {
        return new GenerationException(what + " not supported for the " + target + " target.");
    }
}
