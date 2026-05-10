// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.hivevm.core.Environment;
import org.jspecify.annotations.NonNull;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The DigestWriter class extends {@link PrintWriter} and implements the {@link Environment}
 * interface. It is designed to wrap an output stream and compute a cryptographic digest (MD5) of
 * the written contents. Additionally, it tracks and processes environment variables consumed during
 * its operations, allowing for formatted output with metadata such as a checksum and consumed
 * options.
 */
class TemplateWriter implements LinePrinter, Environment, AutoCloseable {

    private static final String INDENT = "    ";

    private final PrintWriter writer;
    private final DigestOutputStream stream;
    private final Set<String> consumed;
    private final Environment environment;


    private int indent;
    private boolean newLine;

    /**
     * Constructs an instance of {@link TemplateWriter}.
     */
    private TemplateWriter(DigestOutputStream stream, Environment environment) {
        this.writer = new PrintWriter(stream);
        this.stream = stream;
        this.consumed = new HashSet<>();
        this.environment = environment;
        this.indent = 0;
        this.newLine = false;
    }

    /**
     * Checks whether the specified name exists in the underlying environment.
     */
    @Override
    public final boolean has(String name) {
        return this.environment.has(name);
    }

    /**
     * Retrieves the value associated with the specified name from the underlying environment and
     * records the name as consumed by adding it to the tracking set.
     */
    @Override
    public final Object get(String name) {
        this.consumed.add(name);
        return this.environment.get(name);
    }

    /**
     * Formats a given name and value into a printable string representation. If the value is an
     * instance of {@code Number} or {@code Boolean}, the result is formatted as `name=value`. For
     * all other types of values, the result is formatted as `name='value'`.
     */
    private static String toPrintable(String name, Object value) {
        if ((value instanceof Number) || (value instanceof Boolean))
            return String.format("%s=%s", name, value);
        if (value instanceof Collection<?> collection)
            return String.format("%s=%s", name, collection.size());
        if (value instanceof Map<?, ?> map)
            return String.format("%s=%s", name, map.size());
        if (value instanceof String)
            return String.format("%s='%s'", name, value);
        return name;
    }

    /**
     * Creates an instance of MD5 {@link MessageDigest}.
     */
    private static MessageDigest create() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final void println() {
        this.newLine = true;
        writer.write('\n');
    }

    @Override
    public final void print(@NonNull String text) {
        for (var line : text.splitWithDelimiters("\n", -1)) {
            if (line.equals("\n")) {
                newLine = true;
                writer.write('\n');
            } else if (!line.isEmpty()) {
                if (newLine) {
                    IntStream.range(0, indent).forEach(i -> writer.write(TemplateWriter.INDENT));
                }
                newLine = false;
                write(line);
            }
        }
    }

    /**
     * Writes a string.  This method cannot be inherited from the Writer class
     * because it must suppress I/O exceptions.
     *
     * @param s String to be written
     */
    public final void write(@NonNull String s) {
        writer.write(s.replace("\t", TemplateWriter.INDENT));
    }

    public final TemplateWriter indent() {
        this.indent++;
        return this;
    }

    public final TemplateWriter outdent() {
        this.indent--;
        return this;
    }

    /**
     * Closes the stream and releases any system resources associated with it. Closing a previously
     * closed stream has no effect.
     */
    @Override
    public void close() {
        writer.flush();
        writer.printf("\n// Checksum=%s (Do not edit this line!)\n", HexFormat.of()
                .formatHex(this.stream.getMessageDigest().digest()).toUpperCase());
        if (!this.consumed.isEmpty()) {
            writer.printf("// Options: %s\n", this.consumed.stream()
                    .filter(n -> !n.contains(".")).sorted()
                    .map(n -> TemplateWriter.toPrintable(n, get(n)))
                    .collect(Collectors.joining(", ")));
        }
        writer.close();
    }

    /**
     * Creates a new instance of {@link TemplateWriter} using the specified title, output stream,
     * and environment. This method initializes the necessary internal structures, including the
     * message digest and byte buffer, to write output while computing its digest.
     */
    public static TemplateWriter create(String title, OutputStream stream, Environment environment) {
        var digest = new DigestOutputStream(stream, TemplateWriter.create());
        var writer = new TemplateWriter(digest, environment);
        writer.writer.printf("// Generated by %s - Do not edit this line!\n\n", title);
        return writer;
    }
}