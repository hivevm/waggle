// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.jspecify.annotations.NonNull;

public interface SourceWriter {

    SourceWriter append(@NonNull String text);

    default SourceWriter indent() {
        return this;
    }

    default SourceWriter outdent() {
        return this;
    }

    default SourceWriter new_line() {
        return append("\n");
    }

    static SourceWriter create(StringWriter writer) {
        return new SourcePrinter(writer);
    }

    class SourcePrinter extends PrintWriter implements SourceWriter {

        public SourcePrinter(StringWriter writer) {
            super(writer);
        }

        @Override
        public SourceWriter append(@NonNull String text) {
            write(text);
            return this;
        }
    }
}
