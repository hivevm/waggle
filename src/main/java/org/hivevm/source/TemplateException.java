// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

/**
 * Signals that a template is malformed, or that it refers to something the environment does not
 * provide.
 *
 * <p>The engine deliberately fails instead of rendering a best effort: a dropped block or an empty
 * placeholder yields source that does not compile, and does so far away from the cause. Failing here
 * names the template and the offending directive.
 */
public class TemplateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    TemplateException(String message) {
        super(message);
    }

    TemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
