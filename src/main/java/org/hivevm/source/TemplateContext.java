// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;


import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The {@code TemplateOptions} class provides a flexible mechanism to manage key-value options
 * within an environment. It allows the definition and retrieval of options, and supports setting
 * options with direct values, suppliers, writers, or custom mappers.
 */
class TemplateContext implements Context {

    private final RenderContext environment;
    private final Map<String, Object> options = new HashMap<>();

    /**
     * Constructs a new instance of the {@code TemplateOptions} class with the specified
     * environment.
     */
    public TemplateContext(RenderContext environment) {
        this.environment = environment;
    }

    /**
     * Where the rendered source goes. A context wraps the caller's own context, so it must pass
     * the sink on: forgetting to made the lexer and the parser write files while the runtime
     * classes went to the sink (ADR-0018). It used to find the sink by downcasting to Waggle's
     * options; the wrapped context now declares it (ADR-0023).
     */
    @Override
    public final OutputSink outputSink() {
        return this.environment.outputSink();
    }

    @Override
    public final String renderTitle() {
        return this.environment.renderTitle();
    }

    /**
     * Checks whether the specified name exists either in the current options map or in the
     * underlying environment.
     */
    @Override
    public final boolean has(String name) {
        return this.options.containsKey(name) || this.environment.has(name);
    }

    /**
     * Retrieves the value associated with the specified name. If the name exists in the current
     * options map, its corresponding value is returned. Otherwise, the value is retrieved from the
     * underlying environment.
     */
    @Override
    public final Object get(String name) {
        return this.options.containsKey(name) ? this.options.get(name) : this.environment.get(name);
    }

    /**
     * Associates the specified value with the given name in the current options map. If the name
     * already exists in the map, its value is updated to the given value.
     */
    public final void set(String name, Object value) {
        this.options.put(name, value);
    }

    public final void set(String name, SourceSupplier supplier) {
        this.options.put(name, supplier);
    }

    public final void set(String name, SourceConsumer consumer) {
        this.options.put(name, consumer);
    }

    public final <T> Qualifier<T> add(String name, T value) {
        this.options.put(name, value);
        return new Qualifier<>();
    }

    public final <T> Qualifier<T> add(String name, Iterable<T> value) {
        this.options.put(name, value);
        return new Qualifier<>();
    }

    public class Qualifier<T> implements Context.Qualifier<T> {

        public final Qualifier<T> set(String key, Function<T, Object> function) {
            TemplateContext.this.options.put(key, function);
            return this;
        }

        public final Qualifier<T> set(String key, SourceProvider<T> provider) {
            TemplateContext.this.options.put(key, provider);
            return this;
        }
    }
}
