// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import org.hivevm.cc.GenerationException;
import org.hivevm.cc.generator.NodeData;
import org.hivevm.cc.generator.NodeGenerator;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.parser.Options;
import org.hivevm.source.Template;

/**
 * The Rust tree runtime: the tree state, the tree constants and the node trait. Rust has no
 * templates for per-node classes (NODE_MULTI) or visitors (VISITOR) — the ones it had were unported
 * Java leftovers under names that did not exist, so such a grammar died with "Invalid template
 * name". It is rejected up front with a message that says what is missing, as DEPTH_LIMIT is.
 */
class RustNodeGenerator implements NodeGenerator {

    @Override
    public final void generate(Options context, NodeData data) {
        rejectUnsupported(context, data);

        RustTemplate.TREE_STATE.render(context);
        generateTreeConstants(context, data);
        RustTemplate.NODE.render(context);
    }

    private static void rejectUnsupported(Options context, NodeData data) {
        if (context.getVisitor()) {
            throw new GenerationException("VISITOR is not supported for the Rust target.");
        }

        var excludes = context.getExcudeNodes();
        if (context.getBuildNodeFiles()
                && data.getNodesToGenerate().stream().anyMatch(n -> !excludes.contains(n))) {
            throw new GenerationException("Node classes (NODE_MULTI with BUILD_NODE_FILES) are not "
                    + "supported for the Rust target.");
        }
    }

    private void generateTreeConstants(Options context, NodeData data) {
        var options = Template.newContext(context);
        options.add("NODES", NodeScope.getNodeIds().size())
                .set("LABEL", i -> NodeScope.getNodeIds().get(i))
                .set("TITLE", i -> NodeScope.getNodeNames().get(i));

        RustTemplate.TREE_CONSTANTS.render(options);
    }
}
