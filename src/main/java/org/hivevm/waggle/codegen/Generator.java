// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.api.ParserRequest;

import java.text.ParseException;
import java.util.Optional;

/**
 * The {@link Generator} class.
 */
public interface Generator {

    void generate(ParserRequest request) throws ParseException;

    /**
     * How this back end writes tree code, or nothing when it has no tree support (ADR-0016). A
     * target without one still generates plain parsers; it only refuses grammars that build a tree.
     */
    Optional<TreeEmitter> treeSupport();

    LexerGenerator newLexerGenerator();

    ParserGenerator newParserGenerator();
}
