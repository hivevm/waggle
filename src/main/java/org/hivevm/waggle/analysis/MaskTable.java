// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * The token masks of the one-token switches ({@code jj_la1}), in the layout the parser declares
 * them: one table per 32-bit word of token kinds, each with one entry per slot. The planner files
 * a mask per slot; the generator used to transpose them to words while printing (ADR-0029).
 *
 * @param wordCount the number of 32-bit words a set of token kinds takes
 * @param slots     the number of {@code jj_la1} slots
 * @param words     per word, the mask of every slot in slot order
 */
public record MaskTable(int wordCount, int slots, List<int[]> words) {

    /** The number of 32-bit words a set of {@code tokenCount} token kinds takes. */
    static int wordCount(int tokenCount) {
        return ((tokenCount - 1) / 32) + 1;
    }

    /** Transposes the masks of the slots into the words of the table. */
    static MaskTable of(int tokenCount, List<int[]> masks) {
        var wordCount = MaskTable.wordCount(tokenCount);
        var words = new ArrayList<int[]>(wordCount);
        for (int w = 0; w < wordCount; w++) {
            var word = new int[masks.size()];
            for (int slot = 0; slot < masks.size(); slot++) {
                word[slot] = masks.get(slot)[w];
            }
            words.add(word);
        }
        return new MaskTable(wordCount, masks.size(), List.copyOf(words));
    }

    /** The first token kind that word {@code word} covers. */
    public static int firstToken(int word) {
        return 32 * word;
    }
}
