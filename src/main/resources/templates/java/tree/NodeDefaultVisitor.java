// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package __JAVA_PACKAGE__;

public class NodeDefaultVisitor implements NodeVisitor {

	public __RETURN_TYPE__ defaultVisit(Node node, __ARGUMENT_TYPE__ data)__EXCEPTION__ {
		node.childrenAccept(this, data);
		return__RETURN_VALUE__;
	}

	public __RETURN_TYPE__ visit(Node node, __ARGUMENT_TYPE__ data)__EXCEPTION__ {
		__RETURN__defaultVisit(node, data);
	}
//@if(NODE_MULTI)
//@foreach(NODES)

	public __RETURN_TYPE__ visit(AST__NODES_NAME__ node, __ARGUMENT_TYPE__ data)__EXCEPTION__ {
		__RETURN__defaultVisit(node, data);
	}
//@end
//@fi
}