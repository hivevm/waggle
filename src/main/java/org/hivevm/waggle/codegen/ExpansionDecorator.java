// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.model.NodeScope;

/**
 * What the parser generator lets another concern wrap around a node scope.
 *
 * <p>{@link ParserGenerator} asks this where it used to insert tree code itself, and
 * {@link #NONE} is what it asks when the grammar builds no tree or the target cannot (ADR-0016).
 * The answers are output model records that the templates write (ADR-0031); null wraps nothing.
 *
 * <p>A production's own scope is opened on the line that already carries the signature, a nested
 * expansion's scope after a blank line. Both are closed the same way, and the closing covers the
 * failure path too -- the written region is one {@code try} whose {@code catch} and
 * {@code finally} are written together.
 */
public interface ExpansionDecorator {

    /** Wraps nothing around anything. */
    ExpansionDecorator NONE = new ExpansionDecorator() {

        @Override
        public ScopeModel.ScopeOpen open(NodeScope scope, boolean production) {
            return null;
        }

        @Override
        public ScopeModel.ScopeClose close(NodeScope scope) {
            return null;
        }
    };

    /** What opens {@code scope}, or null for nothing. */
    ScopeModel.ScopeOpen open(NodeScope scope, boolean production);

    /** What closes {@code scope}, or null for nothing. */
    ScopeModel.ScopeClose close(NodeScope scope);
}
