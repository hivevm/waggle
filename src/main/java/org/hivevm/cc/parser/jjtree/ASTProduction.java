// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser.jjtree;

import org.hivevm.cc.model.NodeScope;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;


class ASTProduction extends ASTNode {

    private String name;
    private int nextNodeScopeNumber;
    private final List<String> throws_list;


    private final Hashtable<NodeScope, Integer> scopes;

    ASTProduction(Parser p, int id) {
        super(p, id);
        this.nextNodeScopeNumber = 0;
        this.scopes = new Hashtable<>();
        this.throws_list = new ArrayList<>();
    }

    String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    protected void addThrow(String throw_name) {
        this.throws_list.add(throw_name);
    }

    final Iterable<String> throwElements() {
        return this.throws_list;
    }

    final int getNodeScopeNumber(NodeScope s) {
        Integer i = this.scopes.get(s);
        if (i == null) {
            i = this.nextNodeScopeNumber++;
            this.scopes.put(s, i);
        }
        return i;
    }

}
