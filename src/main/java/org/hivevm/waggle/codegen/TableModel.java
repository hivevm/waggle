// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import java.util.ArrayList;
import java.util.List;

/**
 * The run-time tables of a token manager as its templates write them (ADR-0031): every value is
 * already spelled for the target, and the values are already broken into the lines they are
 * written on, which a template cannot count.
 *
 * <p>A template is named after the type of the record it renders, under
 * {@code templates/<target>/lexer/apply/}.
 */
public final class TableModel {

    private TableModel() {
    }

    /**
     * {@code jjnextStates}: the state sets the NFA moves into, one after the other.
     *
     * @param length how many states it holds
     * @param rows   its lines
     */
    public record NextStates(int length, List<Row> rows) {
    }

    /**
     * {@code jjnewLexState}: per kind, the lexical state it switches to, or -1.
     *
     * @param length how many kinds it holds
     * @param rows   its lines
     */
    public record LexStateTable(int length, List<Row> rows) {
    }

    /**
     * One of the {@code jjtoToken}, {@code jjtoSkip}, {@code jjtoSpecial} and {@code jjtoMore}
     * bit vectors, 64 kinds to a word.
     *
     * @param name   its name in the target
     * @param length how many words it holds
     * @param rows   its lines
     */
    public record KindVector(String name, int length, List<Row> rows) {
    }

    /**
     * One line of a table: its values, each followed by a comma and a blank.
     *
     * @param values the line as it is written
     */
    public record Row(String values) {
    }

    /** {@code values} as lines of {@code perLine} values each. */
    static List<Row> rows(List<String> values, int perLine) {
        var rows = new ArrayList<Row>();
        for (int i = 0; i < values.size(); i += perLine) {
            var line = new StringBuilder();
            for (var value : values.subList(i, Math.min(i + perLine, values.size()))) {
                line.append(value).append(", ");
            }
            rows.add(new Row(line.toString()));
        }
        return List.copyOf(rows);
    }
}
