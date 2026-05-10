// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.doc;

import org.hivevm.cc.HiveCCOptions;

/**
 * Global variables for JJDoc.
 */
class JJDocGlobals {

    /**
     * The name of the input file.
     */
    static String input_file;
    /**
     * The name of the output file.
     */
    static String output_file;

    /**
     * The Generator to create output with.
     */
    static Generator generator;

    /**
     * The commandline option is either TEXT or not, but the generator might have been set to some
     * other Generator using the setGenerator method.
     *
     * @return the generator configured in options or set by setter.
     */
    static Generator getGenerator(HiveCCOptions opts) {
        JJDocGlobals.generator = new BNFGenerator(opts);
        return JJDocGlobals.generator;
    }

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
