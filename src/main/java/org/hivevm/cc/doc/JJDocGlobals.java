// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.doc;

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
    static Generator getGenerator(JJDocOptions opts) {
        if (JJDocGlobals.generator == null) {
            if (opts.getText()) {
                JJDocGlobals.generator = new TextGenerator(opts);
            }
            else if (opts.getBNF()) {
                JJDocGlobals.generator = new BNFGenerator(opts);
            }
            else if (opts.getXText()) {
                JJDocGlobals.generator = new XTextGenerator(opts);
            }
            else {
                JJDocGlobals.generator = new HTMLGenerator(opts);
            }
        }
        else if (opts.getText()) {
            if (JJDocGlobals.generator instanceof HTMLGenerator) {
                JJDocGlobals.generator = new TextGenerator(opts);
            }
        }
        else if (opts.getBNF()) {
            JJDocGlobals.generator = new BNFGenerator(opts);
        }
        else if (opts.getXText()) {
            JJDocGlobals.generator = new XTextGenerator(opts);
        }
        else if (JJDocGlobals.generator instanceof TextGenerator) {
            JJDocGlobals.generator = new HTMLGenerator(opts);
        }
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
