// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.hivevm.cc.parser.Options;


/**
 * The {@link TemplateOptions} class.
 */
public class TemplateOptions implements Options {

  private final Environment         environment;
  private final Map<String, Object> options = new HashMap<>();

  /**
   * @param environment
   */
  public TemplateOptions(Environment environment) {
    this.environment = environment;
  }

  /**
   * Returns <code>true</code> if the environment variable is set.
   *
   * @param name
   */
  @Override
  public final boolean isSet(String name) {
    return this.options.containsKey(name) || this.environment.isSet(name);
  }

  /**
   * Returns the value to which the specified key is mapped, or {@code null} if this map contains no
   * mapping for the key.
   *
   * @param name
   */
  @Override
  public final Object get(String name) {
    return this.options.containsKey(name) ? this.options.get(name) : this.environment.get(name);
  }

  /**
   * Sets a key/value option.
   *
   * @param name
   * @param value
   */
  @Override
  public final void set(String name, Object value) {
    this.options.put(name, value);
  }

  public final void set(String name, Supplier<Object> value) {
    this.options.put(name, value);
  }

  public final void setWriter(String name, Consumer<PrintWriter> value) {
    set(name, () -> {
      StringWriter builder = new StringWriter();
      try (PrintWriter writer = new PrintWriter(builder)) {
        value.accept(writer);
      }
      return builder.toString();
    });
  }

  public final void set(String name, Function<?, Object> value) {
    this.options.put(name, value);
  }

  public final <T> Mapper<T> add(String name, T value) {
    this.options.put(name, value);
    return new Mapper<>(name);
  }

  public final <T> Mapper<T> add(String name, Iterable<T> value) {
    this.options.put(name, value);
    return new Mapper<>(name);
  }

  public class Mapper<T> {

    private final String name;

    /**
     * Constructs an instance of {@link Mapper}.
     *
     * @param name
     */
    private Mapper(String name) {
      this.name = name;
    }

    public final Mapper<T> set(String key, Function<T, Object> function) {
      TemplateOptions.this.options.put(String.join(".", this.name, key), function);
      return this;
    }

    public final Mapper<T> set(String key, BiConsumer<T, PrintWriter> consumer) {
      set(key, i -> {
        StringWriter builder = new StringWriter();
        try (PrintWriter writer = new PrintWriter(builder)) {
          consumer.accept(i, writer);
        }
        return builder.toString();
      });
      return this;
    }
  }
}
