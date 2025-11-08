// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import org.hivevm.cc.parser.JavaCCErrors;

/**
 * Describes character range descriptors in a character list.
 */

public class CharacterRange extends Production {

    /**
     * The leftmost and the rightmost characters in this character range.
     */
    private char right;

    private char left;

    public CharacterRange(char l, char r) {
        if (l > r) {
            JavaCCErrors.semantic_error(this, "Invalid range : \"" + (int) l + "\" - \"" + (int) r
                    + "\". First character should be less than or equal to the second one in a range.");
        }

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
