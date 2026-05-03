// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.util.HashSet;
import java.util.Set;

import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.NodeDescriptor;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.ZeroOrMore;
import org.hivevm.cc.model.ZeroOrOne;
import org.hivevm.cc.parser.Options;

public class NodeData {

    private final Set<String> nodesToGenerate;

    public NodeData() {
        this.nodesToGenerate = new HashSet<>();
    }
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
     * Finds the expansion nodes.
     */
    public final void parseExpansion(Expansion exp, Options options) {
        if (exp.getNodeScope() != null)
            addNodeDescriptor(exp.getNodeScope().getNodeDescriptor(), options);
        switch (exp) {
            case Choice p -> p.getChoices().forEach(e -> parseExpansion(e, options));
            case Sequence p -> p.getUnits().forEach(e -> parseExpansion((Expansion) e, options));
            case OneOrMore p -> parseExpansion(p.getExpansion(), options);
            case ZeroOrMore p -> parseExpansion(p.getExpansion(), options);
            case ZeroOrOne p -> parseExpansion(p.getExpansion(), options);
            case NormalProduction p -> parseExpansion(p.getExpansion(), options);
            default -> {
            }
        }
    }
}
