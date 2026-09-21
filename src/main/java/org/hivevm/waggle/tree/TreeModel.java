// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.tree;

import org.hivevm.waggle.model.NodeDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The tree-building nodes of a grammar: every node the parser opens, and the node classes the back
 * end has to write for them.
 *
 * <p>A grammar only has one of these when it actually builds a tree; {@link TreeAnalyzer} decides
 * that and hands back an empty {@link java.util.Optional} otherwise (ADR-0016). It used to live in
 * {@code generator/} and be filled as a side effect of emitting code, so there was no way to ask
 * whether a grammar builds a tree without starting a generation.
 */
public class TreeModel {

    // The nodes in the order the grammar declares them.
    private final List<String> nodeIds;
    private final List<String> nodeNames;
    private final Set<String> nodesToGenerate;

    public TreeModel() {
        this.nodeIds = new ArrayList<>();
        this.nodeNames = new ArrayList<>();
        this.nodesToGenerate = new LinkedHashSet<>();
    }

    /** The ids of all nodes ({@code JJTNAME}), in declaration order. */
    public final List<String> getNodeIds() {
        return Collections.unmodifiableList(this.nodeIds);
    }

    /** The names of all nodes, in declaration order; parallel to {@link #getNodeIds()}. */
    public final List<String> getNodeNames() {
        return Collections.unmodifiableList(this.nodeNames);
    }

    /** The node classes to write, one per node type ({@code NODE_MULTI} only). */
    public final Set<String> getNodesToGenerate() {
        return nodesToGenerate;
    }

    /** Registers the node class a descriptor asks the back end to write. */
    public final void addNodeDescriptor(NodeDescriptor descriptor, TreeOptions options) {
        if (descriptor != null) {
            var type = NodeDescriptor.getNodeType(descriptor.getName(), options.multi());
            if (!"Node".equals(type)) {
                this.nodesToGenerate.add(type);
            }
        }
    }

    /** Records a node the parser opens, in the order the grammar declares it. */
    final void addNode(NodeDescriptor descriptor) {
        if (!this.nodeIds.contains(descriptor.getNodeId())) {
            this.nodeIds.add(descriptor.getNodeId());
            this.nodeNames.add(descriptor.getName());
        }
    }

    final boolean isEmpty() {
        return this.nodeIds.isEmpty();
    }
}
