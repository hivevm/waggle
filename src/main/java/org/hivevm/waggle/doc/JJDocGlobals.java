// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.doc;

/**
 * How JJDoc talks to the user running it.
 *
 * <p>This used to hold the input file, the output file and the generator in static fields, which one
 * run then handed to the next (ADR-0015). They now belong to the {@link BNFGenerator} of a single
 * run; what is left here has no state at all.
 */
class JJDocGlobals {

    /**
     * Log informational messages.
     *
     * @param message the message to log
     */
    static void info(String message) {
        System.out.println(message);
    }

    /**
     * Log error messages.
     *
     * @param message the message to log
     */
    static void error(String message) {
        System.err.println(message);
    }
}
