// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright (c) 2007-2009, Paul Cager. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/JavaCCGlobals.java, org/javacc/Version.java

package org.hivevm.waggle.api;

import org.hivevm.core.Version;

/**
 * The version of Waggle. The build fills it in from the {@code version} in {@code gradle.properties}
 * (task {@code generateVersion}); this file is a template, not a source.
 */
public interface WaggleVersion {

    Version VERSION = Version.parse("${version}");
}
