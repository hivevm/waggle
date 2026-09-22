// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/ASTOptions.java, src/main/resources/templates/MultiNode.template

package __JAVA_PACKAGE__;

public class __NODE_TYPE__ extends __NODE_CLASS__ {

	public __NODE_TYPE__(Parser p, int id) {
		super(p, id);
	}
//@if(VISITOR)

//@if(VISITOR_EXCEPTION)
	public __VISITOR_RETURN_TYPE__ jjtAccept(NodeVisitor visitor, __VISITOR_DATA_TYPE__ data) throws __VISITOR_EXCEPTION__ {
//@else
	public __VISITOR_RETURN_TYPE__ jjtAccept(NodeVisitor visitor, __VISITOR_DATA_TYPE__ data) {
//@fi
//@if(VISITOR_RETURN_TYPE_VOID)
		visitor.visit(this, data);
//@else
		return visitor.visit(this, data);
//@fi
	}
//@fi
//@if(NODE_FACTORY)

	public static Node jjtCreate(Parser p, int id) {
		return new __NODE_TYPE__(p, id);
	}
//@fi
}