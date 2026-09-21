// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.model.NodeDescriptor;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.api.Options;

import java.util.Collections;

/**
 * The tree-building decorator: opens a node scope before the expansion it annotates and unwinds it
 * afterwards, through the target's {@link TreeEmitter}.
 */
final class TreeDecorator implements ExpansionDecorator {

    private final TreeEmitter emitter;
    private final Options options;

    TreeDecorator(TreeEmitter emitter, Options options) {
        this.emitter = emitter;
        this.options = options;
    }

    @Override
    public void beforeProduction(NodeScope scope, LinePrinter printer) {
        printer.println(" // " + scope.getNodeDescriptorText());
        open(scope, printer);
    }

    @Override
    public void beforeExpansion(NodeScope scope, LinePrinter printer) {
        printer.println();
        printer.println("// " + scope.getNodeDescriptorText());
        open(scope, printer);
    }

    @Override
    public void after(NodeScope scope, LinePrinter printer) {
        printer.outdent();
        this.emitter.catchBlocks(scope, printer, this.options, Collections.emptyList());
    }

    private void open(NodeScope scope, LinePrinter printer) {
        var descriptor = scope.getNodeDescriptor();
        var nodeClass = NodeDescriptor.getNodeClass(descriptor.getName(), this.options.getMulti(),
                this.options.getNodeClass());

        this.emitter.openScope(scope, nodeClass, printer, this.options);
        printer.indent();
    }
}
