// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/TokenProduction.java

package org.hivevm.waggle.model;

/**
 * Definitions of constants that identify the kind of regular expression production this is.
 */
public enum TokenKind {
    TOKEN,
    SKIP,
    MORE,
    SPECIAL
}
