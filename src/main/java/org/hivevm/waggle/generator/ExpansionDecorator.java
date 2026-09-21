// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.generator;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.model.NodeScope;

/**
 * What the parser generator lets another concern wrap around a node scope.
 *
 * <p>{@link ParserGenerator} calls this where it used to insert tree code itself, and
 * {@link #NONE} is what it calls when the grammar builds no tree or the target cannot (ADR-0016).
 *
 * <p>There are two ways in because the generator has two: a production's own scope is opened on the
 * line that already carries the signature, a nested expansion's scope after a blank line. Both are
 * closed the same way, and {@link #after} covers the failure path too — the emitted region is one
 * {@code try} whose {@code catch} and {@code finally} are written together.
 */
public interface ExpansionDecorator {

    /** Wraps nothing around anything. */
    ExpansionDecorator NONE = new ExpansionDecorator() {

        @Override
        public void beforeProduction(NodeScope scope, LinePrinter printer) {
        }

        @Override
        public void beforeExpansion(NodeScope scope, LinePrinter printer) {
        }

        @Override
        public void after(NodeScope scope, LinePrinter printer) {
        }
    };

    void beforeProduction(NodeScope scope, LinePrinter printer);

    void beforeExpansion(NodeScope scope, LinePrinter printer);

    void after(NodeScope scope, LinePrinter printer);
}
