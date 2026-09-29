// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/LexGenCPP.java, org/javacc/parser/LexGen.java

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.waggle.codegen.GetNextTokenEmitter;
import org.hivevm.waggle.codegen.TargetSyntax;

/**
 * How C++ spells {@code getNextToken} (ADR-0017).
 */
class CppGetNextTokenEmitter extends GetNextTokenEmitter {

    CppGetNextTokenEmitter(TargetSyntax syntax) {
        super(syntax);
    }

    @Override
    protected String longOne() {
        return "1ULL";
    }

}
