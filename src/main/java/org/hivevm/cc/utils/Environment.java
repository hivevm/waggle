// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

/**
 * The {@link Environment} provides environment variables.
 */
public interface Environment {

  boolean isSet(String name);

  Object get(String name);

  void set(String name, Object value);

  default int intValue(final String option) {
    return ((Integer) get(option));
  }

  default boolean booleanValue(final String option) {
    return ((Boolean) get(option));
  }

  default String stringValue(final String option) {
    return ((String) get(option));
  }
}
