// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import java.io.FileNotFoundException;

/**
 * The {@link TemplateProvider} class.
 */
public interface TemplateProvider {

  String getTemplate();

  String getFilename(String name);

  DigestWriter createDigestWriter(DigestOptions options) throws FileNotFoundException;

  DigestWriter createDigestWriter(String filename, DigestOptions options) throws FileNotFoundException;
}
