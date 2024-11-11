// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@link TemplateEnvironment} class.
 */
class TemplateEnvironment implements Environment {

  private final Environment         environment;
  private final Map<String, Object> options = new HashMap<>();

  /**
   * Constructs an instance of {@link TemplateEnvironment}.
   *
   * @param environment
   */
  public TemplateEnvironment(Environment environment) {
    this.environment = environment;
  }

  /**
   * Return <code>true</code> if the environment variable is set.
   *
   * @param name
   */
  @Override
  public final boolean isSet(String name) {
    // return options.containsKey(name);
    return this.options.containsKey(name) || this.environment.isSet(name);
  }

  /**
   * Get the environment variable by name
   *
   * @param name
   */
  @Override
  public final Object get(String name) {
    // return options.get(name);
    return this.options.containsKey(name) ? this.options.get(name) : this.environment.get(name);
  }

  /**
   * Set an environment variable by name
   *
   * @param name
   * @param value
   */
  @Override
  public void set(String name, Object value) {
    this.options.put(name, value);
  }
}
