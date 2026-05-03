// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import org.hivevm.cc.parser.Options;

public interface NodeDescriptor {

    String getName();

    String getText();

    boolean isGt();

    boolean isVoid();

    default String getDescriptor() {
        return getText() == null
                ? getName()
                : "#" + getName() + "(" + (isGt() ? ">" : "") + getText() + ")";
    }

    default String openNode(String nodeVar) {
        return "jjtree.openNodeScope(" + nodeVar + ");";
    }


    default String closeNode(String nodeVar) {
        if (getText() == null) {
            return "jjtree.closeNodeScope(" + nodeVar + ", true);";
        }
        else if (isGt()) {
            return "jjtree.closeNodeScope(" + nodeVar + ", jjtree.nodeArity() >" + getText() + ");";
        }
        else {
            return "jjtree.closeNodeScope(" + nodeVar + ", " + getText() + ");";
        }
    }

    static String getNodeId(String name) {
        return "JJT" + name.toUpperCase().replace('.', '_');
    }

    static String getNodeType(String name, boolean isMulti) {
        return isMulti ? "AST" + name : "Node";
    }

    static String getNodeClass(String name, Options options) {
        var type = NodeDescriptor.getNodeType(name, options.getMulti());
        var isType = options.getNodeClass().isEmpty() || options.getMulti();
        return isType ? type : options.getNodeClass();
    }
}