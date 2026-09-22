// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/ASTProduction.java, org/javacc/jjdoc/BNFGenerator.java

package org.hivevm.waggle.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describes BNF productions.
 */
public final class BNFProduction extends NormalProduction {

    private int nextScope;
    private final Map<NodeScope, Integer> scopes;

    public BNFProduction() {
        this.nextScope = 0;
        this.scopes = new LinkedHashMap<>();
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
