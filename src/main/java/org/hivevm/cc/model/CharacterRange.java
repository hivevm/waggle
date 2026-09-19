// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

/**
 * Describes character range descriptors in a character list.
 */

public class CharacterRange extends Production {

    /**
     * The leftmost and the rightmost characters in this character range.
     */
    private char right;

    private char left;

    // An inverted user range (left > right) is caught and reported earlier, in the parser layer
    // (AbstractJavaCCParser#character_descriptor_assign clamps the right end); ranges built here are
    // always well-formed, so the model performs no reporting of its own (ADR-0013).
    public CharacterRange(char l, char r) {
        setLeft(l);
        setRight(r);
    }

    public void setLeft(char left) {
        this.left = left;
    }

    public char getLeft() {
        return this.left;
    }

    public void setRight(char right) {
        this.right = right;
    }

    public char getRight() {
        return this.right;
    }
}
