// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.jspecify.annotations.NonNull;

public interface LinePrinter {

    void print(@NonNull String line);

    void println();

    default void println(@NonNull String line) {
        print(line);
        println();
    }

    LinePrinter indent();

    LinePrinter outdent();
}
