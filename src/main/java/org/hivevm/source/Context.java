package org.hivevm.source;

import org.hivevm.cc.parser.Options;

import java.util.function.Function;

public interface Context extends Options {

    void set(String name, Object value);

    void set(String name, SourceSupplier supplier);

    void set(String name, SourceConsumer consumer);

    <T> Qualifier<T> add(String name, T value);

    <T> Qualifier<T> add(String name, Iterable<T> value);

    interface Qualifier<T> {

        Qualifier<T> set(String key, SourceProvider<T> provider);

        Qualifier<T> set(String key, Function<T, Object> function);
    }

    @FunctionalInterface
    interface SourceSupplier {

        String get();
    }

    @FunctionalInterface
    interface SourceConsumer {

        void apply(LinePrinter printer);
    }

    @FunctionalInterface
    interface SourceProvider<V> {

        void apply(V value, LinePrinter printer);
    }
}