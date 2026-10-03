// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

//@if(JAVA_PACKAGE)
package __JAVA_PACKAGE__;
//@fi

public interface NodeType {
//@foreach(NODES)
	int __name__ = __value__;
//@end

	String[] jjtNodeName = {
//@foreach(NODE_NAMES)
			"__name__",
//@end
	};
}
