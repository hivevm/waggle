// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser.jjtree;

import org.hivevm.cc.model.NodeScope;

class JJTNodeScope extends NodeScope {

    JJTNodeScope(ASTProduction p, ASTNodeDescriptor n) {
        super(init(p, n), p::getNodeScopeNumber);
    }

    private static NodeConfig init(ASTProduction p, ASTNodeDescriptor n) {
        if (n == null) {
            String nm = p.name();
            if (p.jjtOptions().getNodeDefaultVoid())
                nm = "void";
            var nd = new ASTNodeDescriptor(p.jjtParser(), NodeType.JJTNODEDESCRIPTOR);
            nd.setName(nm);
            nd.setFaked();
            return new NodeConfig(nm, nd);
        }
        return new NodeConfig(n.getName(), n);
    }
}