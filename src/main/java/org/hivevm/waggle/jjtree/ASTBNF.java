// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/ASTBNF.java, org/javacc/jjtree/ASTOptions.java

package org.hivevm.waggle.jjtree;

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
