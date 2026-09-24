// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.api;


/**
 * The settings that decide what kind of parser is generated, as values.
 *
 * <p>They are read from the resolved option map rather than replacing it: templates read option
 * keys by name ({@code //@if(CACHE_TOKENS)}, {@code __PARSER_NAME__}) by decision
 * ([ADR-0005](../../../../../../../docs/adr/0005-custom-template-engine.md)), so the name-keyed
 * {@code Environment} is the template contract and this is a view over it (ADR-0019). What it buys
 * is that a stage takes the settings it uses, and a typo is a compile error rather than a
 * {@code null} at run time.
 *
 * @param lookahead            the default lookahead depth
 * @param choiceAmbiguityCheck how far a choice is checked for ambiguity
 * @param otherAmbiguityCheck  how far a repetition is checked for ambiguity
 * @param depthLimit           the recursion guard of the generated parser, or 0 for none
 * @param noDfa                whether the string-literal DFA is skipped and everything goes to the NFA
 * @param debugParser          whether the parser traces its productions
 * @param debugLookahead       whether the parser traces its lookahead
 * @param debugTokenManager    whether the token manager traces its moves
 * @param errorReporting       whether the parser collects expected-token sets
 * @param ignoreCase           whether the whole lexical specification is case-insensitive
 * @param sanityCheck          whether the grammar is checked beyond what generation needs
 * @param forceLaCheck         whether lookahead adequacy is checked even above LL(1)
 * @param cacheTokens          whether the parser caches the next token
 * @param keepLineColumn       whether tokens carry their position
 */
public record ParserOptions(int lookahead, int choiceAmbiguityCheck, int otherAmbiguityCheck,
                            int depthLimit, boolean noDfa, boolean debugParser,
                            boolean debugLookahead, boolean debugTokenManager,
                            boolean errorReporting, boolean ignoreCase, boolean sanityCheck,
                            boolean forceLaCheck, boolean cacheTokens, boolean keepLineColumn) {

    public static ParserOptions from(Options options) {
        return new ParserOptions(options.intValue(Waggle.LOOKAHEAD),
                options.intValue(Waggle.CHOICE_AMBIGUITY_CHECK),
                options.intValue(Waggle.OTHER_AMBIGUITY_CHECK),
                options.intValue(Waggle.DEPTH_LIMIT),
                options.booleanValue(Waggle.NO_DFA),
                options.booleanValue(Waggle.DEBUG_PARSER),
                options.booleanValue(Waggle.DEBUG_LOOKAHEAD),
                options.booleanValue(Waggle.DEBUG_TOKEN_MANAGER),
                options.booleanValue(Waggle.ERROR_REPORTING),
                options.booleanValue(Waggle.IGNORE_CASE),
                options.booleanValue(Waggle.SANITY_CHECK),
                options.booleanValue(Waggle.FORCE_LA_CHECK),
                options.booleanValue(Waggle.CACHE_TOKENS),
                options.booleanValue(Waggle.KEEP_LINE_COLUMN));
    }
}
