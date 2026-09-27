// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.lexer;

/**
 * Bit {@code k} of a set stored as 64-bit words, the layout of every kind and character set of the
 * lexer tables.
 */
final class Bits {

    private Bits() {
    }

    static void set(long[] words, int k) {
        words[k / 64] |= 1L << (k % 64);
    }

    static boolean test(long[] words, int k) {
        return (words[k / 64] & (1L << (k % 64))) != 0L;
    }
}
