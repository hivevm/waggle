// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

/**
 * Signals that a parser could not be generated.
 *
 * <p>Generation used to report a failure either by printing a stack trace and carrying on — after
 * which it still announced success — or by calling {@code System.exit()}, which kills the Gradle
 * daemon and the test executor rather than failing the task. Failures now travel as this exception
 * (see ADR-0011).
 */
public class GenerationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GenerationException(String message) {
        super(message);
    }

    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
