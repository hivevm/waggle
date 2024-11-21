// Copyright 2024 HiveVM.org. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.hivevm.cc.utils.Version;

/**
 * This package contains data created as a result of parsing and semanticizing a JavaCC input file.
 * This data is what is used by the back-ends of JavaCC as well as any other back-end of JavaCC
 * related tools such as JJTree.
 */
public interface HiveCCVersion {

  Version VERSION = HiveCCVersion.load();

  static Version load() {
    String version = "0.0";
    try (InputStream stream = HiveCCVersion.class.getResourceAsStream("/version")) {
      Properties properties = new Properties();
      properties.load(stream);
      version = properties.getProperty("release", "0.0");
    } catch (IOException e) {
      System.err.println("Could not read version.properties: " + e);
    }
    return Version.parse(version);
  }
}
