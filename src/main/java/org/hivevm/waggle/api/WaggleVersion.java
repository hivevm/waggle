// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright (c) 2007-2009, Paul Cager. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/JavaCCGlobals.java, org/javacc/Version.java

package org.hivevm.waggle.api;

import org.hivevm.core.Version;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The version of Waggle, read from the {@code /version} resource of the build.
 */
public interface WaggleVersion {

    Version VERSION = WaggleVersion.load();

    static Version load() {
        String version = "0.0";
        try (InputStream stream = WaggleVersion.class.getResourceAsStream("/version")) {
            Properties properties = new Properties();
            properties.load(stream);
            version = properties.getProperty("release", "0.0");
        } catch (IOException e) {
            System.err.println("Could not read version.properties: " + e);
        }
        return Version.parse(version);
    }
}
