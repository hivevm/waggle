// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NonTerminal.java, org/javacc/jjtree/ASTBNF.java

package org.hivevm.waggle.model;

public class ParserDescriptor implements NodeDescriptor {

    private String name;
    private boolean isGT;
    private String text;

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getText() {
        return this.text;
    }

    public boolean isGt() {
        return this.isGT;
    }

    public void setGreaterThan() {
        this.isGT = true;
    }

    public void setExpressionText(String text) {
        this.text = text;
    }
}