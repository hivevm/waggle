// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.tree.TreeModel;
import org.hivevm.waggle.tree.TreeOptions;


/**
 * The tree-building decorator: opens a node scope before the expansion it annotates and unwinds it
 * afterwards, through the target's {@link TreeEmitter}.
 */
final class TreeDecorator implements ExpansionDecorator {

    private final TreeEmitter emitter;
    private final TreeOptions options;
    private final TreeModel model;

    TreeDecorator(TreeEmitter emitter, TreeOptions options, TreeModel model) {
        this.emitter = emitter;
        this.options = options;
        this.model = model;
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
        this.emitter.catchBlocks(this.model.scope(scope), printer, this.options);
    }

    private void open(NodeScope scope, LinePrinter printer) {
        this.emitter.openScope(this.model.scope(scope), printer, this.options);
        printer.indent();
    }
}
