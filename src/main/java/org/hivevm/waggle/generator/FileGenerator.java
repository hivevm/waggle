// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.generator;

import org.hivevm.waggle.lexer.LexerData;

/**
 * The {@link FileGenerator} class.
 */
@FunctionalInterface
public interface FileGenerator {

    void generate(LexerData context);
}
