// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import org.hivevm.cc.model.NodeDescriptor;

public class ASTNodeDescriptor extends ASTNode implements NodeDescriptor {


    private String  name;
    private boolean isGT;
    private String  text;
    private boolean faked;

    public ASTNodeDescriptor(Parser p, int id) {
        super(p, id);
        this.faked = false;
    }

    public String getName() {
        return this.name;
    }

    public String getText() {
        return this.text;
    }

    public boolean isGt() {
        return this.isGT;
    }

    public boolean isVoid() {
        return this.name.equals("void");
    }

    @Override
    public String toString() {
        if (this.faked)
            return "(faked) " + this.name;
        else
            return super.toString() + ": " + this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setGreaterThan() {
        this.isGT = true;
    }

    public void setFaked() {
        this.faked = true;
    }

    public void setExpressionText(String text) {
        this.text = text;
    }

    @Override
    public String translateImage(Token t) {
        return whiteOut(t);
    }

    @Override
    public final Object jjtAccept(NodeVisitor visitor, ASTWriter data) {
        return visitor.visit(this, data);
    }
}