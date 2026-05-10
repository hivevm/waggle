// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.jspecify.annotations.NonNull;

import java.io.PrintWriter;
import java.io.Writer;

public interface LinePrinter {

    void print(@NonNull String line);

    void println();

    default void println(@NonNull String line) {
        print(line);
        println();
    }

    LinePrinter indent();

    LinePrinter outdent();

    static LinePrinter wrap(Writer writer) {
        return new Wrapper(writer);
    }

    class Wrapper extends PrintWriter implements LinePrinter {

        public Wrapper(Writer writer) {
            super(writer);
        }

        public final LinePrinter indent() {
            return this;
        }

        public final LinePrinter outdent() {
            return this;
        }
    }
}