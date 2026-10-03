// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

//@if(JAVA_PACKAGE)
package __JAVA_PACKAGE__;
//@fi

public class NodeDefaultVisitor implements NodeVisitor {

//@if(VISITOR_EXCEPTION)
	public __VISITOR_RETURN_TYPE__ defaultVisit(Node node, __VISITOR_DATA_TYPE__ data) throws __VISITOR_EXCEPTION__ {
//@else
	public __VISITOR_RETURN_TYPE__ defaultVisit(Node node, __VISITOR_DATA_TYPE__ data) {
//@fi
		node.childrenAccept(this, data);
//@if(VISITOR_RETURN_TYPE_VOID)
		return;
//@else
		return __VISITOR_RETURN_VALUE__;
//@fi
	}

//@if(VISITOR_EXCEPTION)
	public __VISITOR_RETURN_TYPE__ visit(Node node, __VISITOR_DATA_TYPE__ data) throws __VISITOR_EXCEPTION__ {
//@else
	public __VISITOR_RETURN_TYPE__ visit(Node node, __VISITOR_DATA_TYPE__ data) {
//@fi
//@if(VISITOR_RETURN_TYPE_VOID)
		defaultVisit(node, data);
//@else
		return defaultVisit(node, data);
//@fi
	}
//@if(NODE_MULTI)
//@foreach(NODES)

//@if(VISITOR_EXCEPTION)
	public __VISITOR_RETURN_TYPE__ visit(AST__name__ node, __VISITOR_DATA_TYPE__ data) throws __VISITOR_EXCEPTION__ {
//@else
	public __VISITOR_RETURN_TYPE__ visit(AST__name__ node, __VISITOR_DATA_TYPE__ data) {
//@fi
//@if(VISITOR_RETURN_TYPE_VOID)
		defaultVisit(node, data);
//@else
		return defaultVisit(node, data);
//@fi
	}
//@end
//@fi
}