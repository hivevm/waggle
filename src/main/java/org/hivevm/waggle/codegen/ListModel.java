// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

/**
 * The elements of the flat lists a template walks with {@code //@foreach}: constants, names and
 * table lines, every value already spelled for the target.
 *
 * <p>A pass of a {@code //@foreach} reads the components of its element by name, as an applied
 * template reads its record's. They used to be functions bound under names such as
 * {@code TOKENS_LABEL}, which the engine applied to whatever the current element was; a record says
 * what an element holds, and the compiler checks it. Unlike the records a template is applied to,
 * these have no template of their own: the body of the {@code //@foreach} is theirs.
 */
public final class ListModel {

    private ListModel() {
    }

    /** Each of {@code names} as spelled by {@code spelling}, numbered by its position. */
    public static List<Constant> numbered(List<String> names, UnaryOperator<String> spelling) {
        return IntStream.range(0, names.size())
                .mapToObj(i -> new Constant(spelling.apply(names.get(i)), i)).toList();
    }

    /** Each of {@code names} as it stands. */
    public static List<Name> names(Collection<String> names) {
        return names.stream().map(Name::new).toList();
    }

    /**
     * A named number: a token kind, a lexical state, a node type.
     *
     * @param name  the name, spelled as the target writes it
     * @param value the number it stands for
     */
    public record Constant(String name, int value) {
    }

    /**
     * Text written as it stands: a name, an import, a literal image.
     *
     * @param name the text, or {@code null} for none
     */
    public record Name(String name) {
    }

    /**
     * The image of a token kind as {@code tokenImage} lists it.
     *
     * @param index the kind
     * @param label the label, spelled for the target
     * @param image the literal image, spelled for the target
     */
    public record TokenImage(int index, String label, String image) {
    }

    /**
     * One of the {@code jjbitVec} bit vectors of the non-ASCII character tests.
     *
     * @param index  its number
     * @param values its words, spelled and separated for the target
     */
    public record BitVector(int index, String values) {
    }

    /**
     * One word of the {@code jj_la1} masks: which tokens a choice point expects, 32 to a word.
     *
     * @param index  the word
     * @param values its mask per choice point, separated for the target
     * @param offset what precedes a bit's number to make it a token kind: nothing for the first
     *               word, the first kind of the word and a {@code +} after that
     */
    public record MaskWord(int index, String values, String offset) {
    }

    /**
     * One case of the {@code jj_rescan_token} dispatch: the save slot of a {@code jj_2} routine and
     * the number of the {@code jj_3} routine it runs again.
     */
    public record Jj2Case(int saveSlot, int number) {
    }
}
