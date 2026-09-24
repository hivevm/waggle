// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

//@if(JAVA_PACKAGE)
package __JAVA_PACKAGE__;
//@fi

public interface NodeVisitor {

	__RETURN_TYPE__ visit(Node node, __ARGUMENT_TYPE__ data)__EXCEPTION__;
//@if(NODE_MULTI)
//@foreach(NODES)

	__RETURN_TYPE__ visit(AST__NODES_NAME__ node, __ARGUMENT_TYPE__ data)__EXCEPTION__;
//@end
//@fi
}