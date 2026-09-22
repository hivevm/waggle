// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/SingleCharacter.java

package org.hivevm.waggle.model;

/**
 * Describes single character descriptors in a character list.
 */
public class SingleCharacter extends Production {

    private final char ch;

    public SingleCharacter(char c) {
        this.ch = c;
    }

    public char getChar() {
        return this.ch;
    }
}
