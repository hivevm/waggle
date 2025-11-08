// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

/**
 * Describes a match, within a given lookahead.
 */
class MatchInfo {

    final int[] match;
    int firstFreeLoc;

    /**
     * Constructs an instance of {@link MatchInfo}.
     */
    MatchInfo(int limit) {
        this.match = new int[limit];
        this.firstFreeLoc = 0;
    }
}
