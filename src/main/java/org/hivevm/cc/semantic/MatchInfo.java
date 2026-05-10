// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

/**
 * Describes a match, within a given lookahead.
 */
record MatchInfo(int[] match, int firstFreeLoc) {

    MatchInfo(int limit) {
        this(new int[limit], 0);
    }

    private MatchInfo(int limit, int firstFreeLoc) {
        this(new int[limit], firstFreeLoc);
    }

    MatchInfo copyWith(int token) {
        var copy = new MatchInfo(this.match.length, this.firstFreeLoc + 1);
        System.arraycopy(this.match, 0, copy.match, 0, this.firstFreeLoc);
        copy.match[copy.firstFreeLoc - 1] = token;
        return copy;
    }
}
