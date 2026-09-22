// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.api.GenerationException;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.api.ParserRequest;

import java.util.Optional;

/**
 * One target's back end: everything needed to render a parsed and analysed grammar as source.
 */
public interface Generator {

    /**
     * Renders the grammar. A back end that cannot generate what the grammar asks for throws
     * {@link GenerationException} (ADR-0011, ADR-0022) rather than reporting a diagnostic and
     * returning; the exception is unchecked, so this method declares no {@code throws}.
     */
    void generate(ParserRequest request);

    /**
     * How this back end writes tree code, or nothing when it has no tree support (ADR-0016). A
     * target without one still generates plain parsers; it only refuses grammars that build a tree.
     */
    Optional<TreeEmitter> treeSupport();

    LexerGenerator newLexerGenerator();

    ParserGenerator newParserGenerator();
}
