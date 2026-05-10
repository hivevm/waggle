// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.Hashtable;

/**
 * Describes BNF productions.
 */

public class BNFProduction extends NormalProduction {

    private int nextScope;
    private final Hashtable<NodeScope, Integer> scopes;

    public BNFProduction() {
        this.nextScope = 0;
        this.scopes = new Hashtable<>();
    }

    int getNodeScopeNumber(NodeScope s) {
        Integer i = this.scopes.get(s);
        if (i == null) {
            i = this.nextScope++;
            this.scopes.put(s, i);
        }
        return i;
    }
}
