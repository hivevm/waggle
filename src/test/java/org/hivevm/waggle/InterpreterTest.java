// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright (c) 2007-2009, Paul Cager. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: src/main/jjtree/JJTree.jjt, src/main/javacc/JavaCC.jj

package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.waggle.api.ParserInterpreter;
import org.hivevm.waggle.diag.DiagnosticSink;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.lexer.LexerInterpreter;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A grammar can be run over input without generating anything.
 *
 * <p>JavaCC called this an interpreted token manager and needed a data structure of its own for it.
 * Here stage 4 already produces the automaton (ADR-0012), so the interpreter reads the same model
 * the back ends render.
 */
class InterpreterTest {

    private static final String GRAMMAR = """
            grammar Calc;

            Input = ( <NUMBER> | <IDENT> | <PLUS> )* <EOF> ;

            SKIP = " " | "\\t" | "\\n" | "\\r" ;

            TOKEN =
              < PLUS: "+" >
            | < NUMBER: (["0"-"9"])+ >
            | < IDENT: ["a"-"z"] ( ["a"-"z","0"-"9","_"] )* >
            ;
            """;

    private static List<LexerInterpreter.Match> tokenize(String input) {
        return new ParserInterpreter(new Diagnostics(DiagnosticSink.SILENT))
                .tokenize(InterpreterTest.GRAMMAR, input);
    }

    @Test
    void itReadsTheTokensOfAGrammar() {
        var images = InterpreterTest.tokenize("x1 + 42").stream()
                .map(LexerInterpreter.Match::image).toList();

        assertEquals(List.of("x1", "+", "42"), images);
    }

    /** The longest match wins, and declaration order breaks a tie. */
    @Test
    void itPrefersTheLongestMatch() {
        var images = InterpreterTest.tokenize("abc123").stream()
                .map(LexerInterpreter.Match::image).toList();

        assertEquals(List.of("abc123"), images, "an identifier swallows the digits after it");
    }

    /** A keyword and an identifier start the same way; the earlier declaration wins. */
    @Test
    void declarationOrderDecidesATie() {
        var keywords = """
                grammar Kw;

                Input = ( <IF> | <ID> )* <EOF> ;

                SKIP = " " ;

                TOKEN =
                  < IF: "if" >
                | < ID: ["a"-"z"] ( ["a"-"z"] )* >
                ;
                """;
        var matches = new ParserInterpreter(new Diagnostics(DiagnosticSink.SILENT))
                .tokenize(keywords, "if ifx");

        assertEquals(List.of("if", "ifx"), matches.stream()
                .map(LexerInterpreter.Match::image).toList());
        assertTrue(matches.get(0).kind() < matches.get(1).kind(),
                "IF is declared before ID, so it has the lower kind");
    }

    /** SKIP never reaches the caller. */
    @Test
    void skippedTokensDoNotAppear() {
        assertEquals(3, InterpreterTest.tokenize("  a  +  b  ").size());
    }

    @Test
    void unmatchableInputSaysWhereItStopped() {
        var error = assertThrows(LexerInterpreter.LexerError.class,
                () -> InterpreterTest.tokenize("a ? b"));

        assertEquals(2, error.offset(), error.getMessage());
        assertTrue(error.getMessage().contains("'?'"), error.getMessage());
    }

    /**
     * The interpreter and the generated token manager read the same specification, so what the
     * interpreter matches must be what the grammar declares.
     */
    @Test
    void theKindsAreTheGrammarsOwnOrdinals() {
        var matches = InterpreterTest.tokenize("+ 1 a");

        assertEquals(List.of("+", "1", "a"),
                matches.stream().map(LexerInterpreter.Match::image).toList());
        assertTrue(matches.get(0).kind() < matches.get(1).kind(),
                "PLUS is declared before NUMBER");
        assertTrue(matches.get(1).kind() < matches.get(2).kind(),
                "NUMBER is declared before IDENT");
    }

    /**
     * A catch-all token ({@code ~[]}) never enters the NFA; the generated lexer applies it after the
     * automaton. The interpreter used to walk the NFA only and failed where the generated lexer
     * matched.
     */
    @Test
    void aCatchAllTokenMatchesWhatNothingElseDoes() {
        var grammar = """
                grammar Any;

                Input = ( <WORD> | <ANY> )* <EOF> ;

                TOKEN =
                  < WORD: (["a"-"z"])+ >
                | < ANY: ~[] >
                ;
                """;
        var matches = new ParserInterpreter(new Diagnostics(DiagnosticSink.SILENT))
                .tokenize(grammar, "ab?c");

        assertEquals(List.of("ab", "?", "c"), matches.stream()
                .map(LexerInterpreter.Match::image).toList());
        assertEquals(matches.get(0).kind(), matches.get(2).kind(), "a longer or earlier WORD wins");
        assertTrue(matches.get(1).kind() > matches.get(0).kind(), "'?' is only matched by ANY");
    }

    /** Declared first, the catch-all wins every one-character tie, as it does in generated code. */
    @Test
    void anEarlierCatchAllWinsAOneCharacterTie() {
        var grammar = """
                grammar Any;

                Input = ( <ANY> | <WORD> )* <EOF> ;

                TOKEN =
                  < ANY: ~[] >
                | < WORD: (["a"-"z"])+ >
                ;
                """;
        var matches = new ParserInterpreter(new Diagnostics(DiagnosticSink.SILENT))
                .tokenize(grammar, "a bc");

        assertEquals(List.of("a", " ", "bc"), matches.stream()
                .map(LexerInterpreter.Match::image).toList());
        assertEquals(matches.get(0).kind(), matches.get(1).kind(), "'a' is taken by ANY");
        assertTrue(matches.get(2).kind() > matches.get(0).kind(), "the longer WORD still wins");
    }
}
