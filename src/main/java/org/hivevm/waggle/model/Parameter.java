// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.model;

import org.hivevm.waggle.grammar.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * One parameter of a production, split out of the list the grammar wrote.
 *
 * <p>A target that writes the parameter list verbatim never needs this; one that reorders it --
 * Rust writes the name before the type (ADR-0030) -- used to walk the tokens itself and count
 * angle brackets while it emitted, which put grammar parsing inside a back end (ADR-0029).
 *
 * @param type the type as the grammar wrote it, with one space where it left a gap
 * @param name the parameter's name
 */
public record Parameter(String type, String name) {

    /**
     * The parameters in {@code tokens}, which are "type name" pairs between commas. A type may
     * hold commas of its own inside angle brackets, which do not separate parameters.
     */
    public static List<Parameter> split(List<Token> tokens) {
        var parameters = new ArrayList<Parameter>();
        var pending = new ArrayList<Token>();
        int depth = 0;
        for (var token : tokens) {
            if (token.image.equals(",") && (depth == 0)) {
                parameters.add(Parameter.of(pending));
                pending.clear();
                continue;
            }
            if (token.image.equals("<")) {
                depth++;
            } else if (token.image.equals(">")) {
                depth--;
            }
            pending.add(token);
        }
        if (!pending.isEmpty()) {
            parameters.add(Parameter.of(pending));
        }
        return List.copyOf(parameters);
    }

    /** One parameter: the last token names it, the tokens before it are its type. */
    private static Parameter of(List<Token> tokens) {
        var type = new StringBuilder();
        for (int i = 0; i < (tokens.size() - 1); i++) {
            var token = tokens.get(i);
            if ((i > 0) && ((token.beginLine != tokens.get(i - 1).endLine)
                    || (token.beginColumn > (tokens.get(i - 1).endColumn + 1)))) {
                type.append(' ');
            }
            type.append(token.image);
        }
        return new Parameter(type.toString(), tokens.getLast().image);
    }
}
