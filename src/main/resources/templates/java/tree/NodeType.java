// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

//@if(JAVA_PACKAGE)
package __JAVA_PACKAGE__;
//@fi

public interface NodeType {
//@foreach(NODES)
	int __NODES_LABEL__ = __NODES_ORDINAL__;
//@end

	String[] jjtNodeName = {
//@foreach(NODE_NAMES)
			"__NODE_NAMES_TITLE__",
//@end
	};
}
