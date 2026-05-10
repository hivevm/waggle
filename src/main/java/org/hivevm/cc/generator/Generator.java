// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.ParserRequest;

import java.text.ParseException;

/**
 * The {@link Generator} class.
 */
public interface Generator {

    void generate(ParserRequest request) throws ParseException;

    NodeGenerator newNodeGenerator();

    LexerGenerator newLexerGenerator();

    ParserGenerator newParserGenerator();
}
