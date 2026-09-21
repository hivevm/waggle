// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.diag;

/**
 * How badly a {@link Diagnostic} affects the generation it belongs to.
 */
public enum Severity {

    /** The generation has failed; the caller is told by an exception (ADR-0011). */
    ERROR,

    /** The generation continues, but something in the grammar deserves attention. */
    WARNING
}
