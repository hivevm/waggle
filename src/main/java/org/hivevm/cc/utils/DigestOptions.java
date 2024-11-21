// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;


/**
 * The {@link DigestOptions} class.
 */
public class DigestOptions implements Environment {

  private final Environment environment;

  private final Set<String> consumed = new HashSet<>();

  /**
   * Constructs an instance of {@link DigestOptions}.
   *
   * @param environment
   */
  public DigestOptions(Environment environment) {
    this.environment = environment;
  }

  /**
   * Return <code>true</code> if there are consumed environment variables.
   */
  public final boolean hasConsumed() {
    return !this.consumed.isEmpty();
  }

  /**
   * Gets the stream of consumed environment variables.
   */
  public final Stream<String> consumed() {
    return this.consumed.stream().filter(n -> DigestOptions.isPrintableOption(get(n))).sorted()
        .map(n -> DigestOptions.toPrintable(n, get(n)));
  }

  /**
   * Returns <code>true</code> if the environment variable is set.
   *
   * @param name
   */
  @Override
  public final boolean isSet(String name) {
    return this.environment.isSet(name);
  }

  /**
   * Returns the value to which the specified key is mapped, or {@code null} if this map contains no
   * mapping for the key.
   *
   * @param name
   */
  @Override
  public final Object get(String name) {
    this.consumed.add(name);
    return this.environment.get(name);
  }

  /**
   * Sets a key/value option.
   *
   * @param name
   * @param value
   */
  @Override
  public final void set(String name, Object value) {
    this.environment.set(name, value);
  }

  /**
   * Return <code>true</code> if the value is a primitive and printable value.
   *
   * @param value
   */
  private static boolean isPrintableOption(Object value) {
    return (value instanceof String) || (value instanceof Number) || (value instanceof Boolean);
  }

  /**
   * Gets the printed version of the value.
   *
   * @param name
   * @param value
   */
  private static String toPrintable(String name, Object value) {
    if ((value instanceof Number) || (value instanceof Boolean)) {
      return String.format("%s=%s", name, value);
    }
    return String.format("%s='%s'", name, value);
  }
}
