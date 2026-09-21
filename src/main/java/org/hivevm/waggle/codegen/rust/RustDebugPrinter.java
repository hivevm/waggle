// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.rust;

import org.hivevm.source.LinePrinter;

/**
 * The two DEBUG_TOKEN_MANAGER lines the Rust lexer prints, shared by the generator and its emitters
 * so that splitting them did not duplicate the text (ADR-0017).
 */
interface RustDebugPrinter {

    /**
     * The current-character trace. Rust has no redirectable debugStream; the trace goes to stderr,
     * which is what stderr is for.
     */
    static void printCurrentCharacter(LinePrinter printer, boolean withLexState) {
        var prefix = withLexState
                ? "<{}>Current character : {}({}) at line {} column {}\", "
                        + "LEX_STATE_NAMES[self.cur_lex_state as usize], "
                : "Current character : {}({}) at line {} column {}\", ";
        printer.println("eprintln!(\"" + prefix
                + "char::from_u32(self.cur_char).unwrap_or('\\u{fffd}'), self.cur_char, "
                + "self.input_stream.get_end_line(), self.input_stream.get_end_column());");
    }

    /** "Currently matched the first N characters as a X token." */
    static void printCurrentlyMatched(LinePrinter printer, String indent) {
        printer.println(indent + "eprintln!(\"   Currently matched the first {} characters as a {} "
                + "token.\", self.jjmatched_pos + 1, "
                + "TOKEN_IMAGE[self.jjmatched_kind as usize]);");
    }
}
