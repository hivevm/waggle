// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

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