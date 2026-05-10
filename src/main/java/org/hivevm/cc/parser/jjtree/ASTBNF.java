// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser.jjtree;

class ASTBNF extends ASTProduction {

    ASTBNF(Parser p, int id) {
        super(p, id);
        addThrow("ParseException");
        addThrow("RuntimeException");
    }

    @Override
    public final Object jjtAccept(NodeVisitor visitor, ASTWriter data) {
        return visitor.visit(this, data);
    }

    @Override
    public final String toString() {
        return super.toString() + ": " + name();
    }
}
