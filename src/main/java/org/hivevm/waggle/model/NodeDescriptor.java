// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.model;

import java.util.Locale;

/**
 * A {@code #Node} annotation, as data: the node's name, the arity expression in its parentheses and
 * whether that expression was written with {@code >}.
 *
 * <p>How a target spells "open this scope" is the back end's business (ADR-0016). This interface
 * used to answer it with a Java string, which was also what C++ needed and never what Rust used.
 */
public interface NodeDescriptor {

    default String getNodeId() {
        return NodeDescriptor.getNodeId(getName());
    }

    String getName();

    String getText();

    boolean isGt();

    default String getDescriptor() {
        return getText() == null
                ? getName()
                : "#" + getName() + "(" + (isGt() ? ">" : "") + getText() + ")";
    }

    static String getNodeId(String name) {
        return "JJT" + name.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    static String getNodeType(String name, boolean isMulti) {
        return isMulti ? "AST" + name : "Node";
    }

    static String getNodeClass(String name, boolean isMulti, String configuredNodeClass) {
        var type = NodeDescriptor.getNodeType(name, isMulti);
        var isType = configuredNodeClass.isEmpty() || isMulti;
        return isType ? type : configuredNodeClass;
    }
}