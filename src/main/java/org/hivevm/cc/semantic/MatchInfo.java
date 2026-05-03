// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

/**
 * Describes a match, within a given lookahead.
 */
class MatchInfo {

    final int[] match;
    int firstFreeLoc;

    MatchInfo(int limit) {
        this.match = new int[limit];
        this.firstFreeLoc = 0;
    }

    MatchInfo copyWith(int token) {
        var copy = new MatchInfo(this.match.length);
        System.arraycopy(this.match, 0, copy.match, 0, this.firstFreeLoc);
        copy.firstFreeLoc = this.firstFreeLoc;
        copy.match[copy.firstFreeLoc++] = token;
        return copy;
    }
}
