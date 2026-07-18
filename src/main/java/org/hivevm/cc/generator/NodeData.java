// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.NodeDescriptor;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.ZeroOrMore;
import org.hivevm.cc.model.ZeroOrOne;
import org.hivevm.cc.parser.Options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The tree-building nodes of a grammar: every node the parser opens, and the node classes the back
 * end has to write for them.
 */
public class NodeData {

    // The nodes in the order the grammar declares them. They used to live in static fields of
    // NodeScope, which every generation in the same JVM had to remember to clear.
    private final List<String> nodeIds;
    private final List<String> nodeNames;
    private final Set<String> nodesToGenerate;

    public NodeData() {
        this.nodeIds = new ArrayList<>();
        this.nodeNames = new ArrayList<>();
        this.nodesToGenerate = new LinkedHashSet<>();
    }

    /** Whether the grammar builds a tree at all, i.e. declares at least one node. */
    public final boolean usesTree() {
        return !this.nodeIds.isEmpty();
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

    public final void addNodeDescriptor(NodeDescriptor descriptor, Options options) {
        if (descriptor != null) {
            var type = NodeDescriptor.getNodeType(descriptor.getName(), options.getMulti());
            if (!"Node".equals(type)) {
                this.nodesToGenerate.add(type);
            }
        }
    }

    /**
     * Collects the nodes of a production. The order is the one in which the grammar parser meets the
     * node descriptors: a production's own node before its body, and an expansion's node after the
     * expansion it decorates ({@code ( a #X b ) #Y} yields X before Y).
     */
    final void parseExpansion(Expansion exp, Options options) {
        if (exp instanceof NormalProduction p) {
            addNodeScope(p.getNodeScope(), options);
            parseExpansion(p.getExpansion(), options);
            return;
        }

        switch (exp) {
            case Choice p -> p.getChoices().forEach(e -> parseExpansion(e, options));
            case Sequence p -> p.getUnits().forEach(e -> parseExpansion(e, options));
            case OneOrMore p -> parseExpansion(p.getExpansion(), options);
            case ZeroOrMore p -> parseExpansion(p.getExpansion(), options);
            case ZeroOrOne p -> parseExpansion(p.getExpansion(), options);
            default -> {
            }
        }
        addNodeScope(exp.getNodeScope(), options);
    }

    private void addNodeScope(NodeScope scope, Options options) {
        if (scope == null) {
            return;
        }
        var descriptor = scope.getNodeDescriptor();
        if (!this.nodeIds.contains(descriptor.getNodeId())) {
            this.nodeIds.add(descriptor.getNodeId());
            this.nodeNames.add(descriptor.getName());
        }
        addNodeDescriptor(descriptor, options);
    }
}
