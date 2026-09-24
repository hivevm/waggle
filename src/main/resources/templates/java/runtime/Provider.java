// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: src/main/resources/templates/gwt/StreamProvider.template

//@if(JAVA_PACKAGE)
package __JAVA_PACKAGE__;
//@fi

import java.io.Closeable;
import java.io.IOException;

public interface Provider extends Closeable {

	/**
	 * Reads characters into an array
	 */
	int read(char[] buffer, int offset, int length) throws IOException;
}