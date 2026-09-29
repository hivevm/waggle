// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/CodeGenerator.java, org/javacc/parser/JavaCCGlobals.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.grammar.Token;

public abstract class CodeGenerator<D> {

    private final Language language;

    protected CodeGenerator(Language language) {
        this.language = language;
    }

    protected final Language getLanguage() {
        return this.language;
    }

    public abstract void generate(D context);

    /** A cursor at the start of {@code t}, for one run of verbatim tokens. */
    protected final TokenCursor cursorAt(Token t) {
        return TokenCursor.at(t, this.language);
    }
}
