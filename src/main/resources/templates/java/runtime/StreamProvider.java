// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: src/main/resources/templates/gwt/StreamProvider.template

//@if(JAVA_PACKAGE)
package __JAVA_PACKAGE__;
//@fi

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Reads the input from a stream or a reader. A stream is read as UTF-8 unless a charset is given.
 */
public class StreamProvider implements Provider {

	Reader _reader;

	public StreamProvider(Reader reader) {
		this._reader = reader;
	}

	public StreamProvider(InputStream stream) throws IOException {
		this._reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
	}

	public StreamProvider(InputStream stream, String charsetName) throws IOException {
		this._reader = new BufferedReader(new InputStreamReader(stream, charsetName));
	}

	@Override
	public int read(char[] buffer, int off, int len) throws IOException {
		return this._reader.read(buffer, off, len);
	}

	@Override
	public void close() throws IOException {
		this._reader.close();
	}
}