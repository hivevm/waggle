// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.codegen.NfaMoveEmitter;
import org.hivevm.waggle.codegen.TargetSyntax;


/**
 * C++ differs from the shared NFA move loop in one place: how the mixed-state epilogue ends.
 */
class CppNfaMoveEmitter extends NfaMoveEmitter {

    CppNfaMoveEmitter(TargetSyntax syntax) {
        super(syntax);
    }

    @Override
    protected void printMoveNfaMixedEpilogue(LinePrinter printer) {
        printer.print("""
                if (jjmatchedPos > strPos)
                    return curPos;

                int toRet = MAX(curPos, seenUpto);
                if (curPos < toRet)
                    for (i = toRet - MIN(curPos, seenUpto); i-- > 0; ) {
                        assert(!reader->endOfInput());
                        curChar = reader->read();
                    } // UTF8: Support Unicode

                if (jjmatchedPos < strPos) {
                    jjmatchedKind = strKind;
                    jjmatchedPos = strPos;
                } else if (jjmatchedPos == strPos && jjmatchedKind > strKind)
                    jjmatchedKind = strKind;

                return toRet;
                """);
    }
}
