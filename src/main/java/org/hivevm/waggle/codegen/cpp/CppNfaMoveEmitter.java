// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.codegen.NfaMoveEmitter;
import org.hivevm.waggle.codegen.TargetSyntax;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.lexer.NfaState;
import org.hivevm.waggle.lexer.NfaStateData;
import org.hivevm.waggle.lexer.NfaStateData.KindInfo;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

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
