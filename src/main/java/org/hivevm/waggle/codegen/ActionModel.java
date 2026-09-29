// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

/**
 * The lexical actions of a token manager as its templates write them (ADR-0031): one case per
 * token kind that has an action or can loop on the empty string. The record type says which of the
 * three action routines the case belongs to, because each updates the image its own way.
 *
 * <p>A template is named after the type of the record it renders, under
 * {@code templates/<target>/lexer/apply/}.
 */
public final class ActionModel {

    private ActionModel() {
    }

    /**
     * A case of TokenLexicalActions.
     *
     * @param kind      the token kind
     * @param lexState  the lexical state whose empty-match loop it guards
     * @param loopCheck whether it guards against matching the empty string for ever
     * @param hasCode   whether the grammar gave the kind an action
     * @param code      that action, laid out from the first column
     * @param reset     whether the image is emptied rather than appended to: the end of input
     * @param literal   whether the image is the kind's literal rather than the matched text
     */
    public record TokenAction(int kind, int lexState, boolean loopCheck, boolean hasCode,
                              String code, boolean reset, boolean literal) {
    }

    /** A case of MoreLexicalActions; see {@link TokenAction} for the components. */
    public record MoreAction(int kind, int lexState, boolean loopCheck, boolean hasCode,
                             String code, boolean literal) {
    }

    /** A case of SkipLexicalActions; see {@link TokenAction} for the components. */
    public record SkipAction(int kind, int lexState, boolean loopCheck, boolean hasCode,
                             String code, boolean literal) {
    }
}
