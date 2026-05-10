// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

/**
 * Definitions of constants that identify the kind of regular expression production this is.
 */
public enum TokenKind {
    TOKEN,
    SKIP,
    MORE,
    SPECIAL
}
