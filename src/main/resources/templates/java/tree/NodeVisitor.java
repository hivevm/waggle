// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

//@if(JAVA_PACKAGE)
package __JAVA_PACKAGE__;
//@fi

public interface NodeVisitor {

//@if(VISITOR_EXCEPTION)
	__VISITOR_RETURN_TYPE__ visit(Node node, __VISITOR_DATA_TYPE__ data) throws __VISITOR_EXCEPTION__;
//@else
	__VISITOR_RETURN_TYPE__ visit(Node node, __VISITOR_DATA_TYPE__ data);
//@fi
//@if(NODE_MULTI)
//@foreach(NODES)

//@if(VISITOR_EXCEPTION)
	__VISITOR_RETURN_TYPE__ visit(AST__NODES_NAME__ node, __VISITOR_DATA_TYPE__ data) throws __VISITOR_EXCEPTION__;
//@else
	__VISITOR_RETURN_TYPE__ visit(AST__NODES_NAME__ node, __VISITOR_DATA_TYPE__ data);
//@fi
//@end
//@fi
}