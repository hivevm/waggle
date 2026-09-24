// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The command line of JJDoc, which no test ran: a missing file was reported and then parsed
 * anyway, which ended in a NullPointerException, and writing to standard output closed it.
 */
class JJDocMainTest {

    private static final String GRAMMAR = """
            grammar Doc;

            options {
              JAVA_PACKAGE: "org.example"
            }

            Input = ( Word() )* <EOF> ;

            Word = <WORD> | "\u00e4h" ;

            TOKEN = < WORD: (["a"-"z", "ä"])+ > ;
            """;

    @Test
    void aMissingFileFailsWithoutAnException(@TempDir Path dir) {
        assertEquals(1, JJDocMain.run(new String[] {dir.resolve("missing.waggle").toString()}));
    }

    @Test
    void aDirectoryFailsWithoutAnException(@TempDir Path dir) {
        assertEquals(1, JJDocMain.run(new String[] {dir.toString()}));
    }

    /**
     * The grammar is read as UTF-8, whatever the platform default: JJDoc escapes the literal as
     * {@code \u00e4h}, where a misread grammar gives {@code \u00c3\u00a4h}.
     */
    @Test
    void theBnfIsWrittenBesideTheGrammar(@TempDir Path dir) throws Exception {
        var grammar = dir.resolve("Doc.waggle");
        Files.writeString(grammar, GRAMMAR, StandardCharsets.UTF_8);
        var bnf = dir.resolve("Doc.bnf");

        assertEquals(0, JJDocMain.run(new String[] {"-OUTPUT_FILE=" + bnf, grammar.toString()}));

        var text = Files.readString(bnf, StandardCharsets.UTF_8);
        assertTrue(text.contains("Word ::=") && text.contains("\"\\u00e4h\""), text);
    }

    /** Standard input in, standard output out: the output must not be closed behind the caller. */
    @Test
    void standardOutputStaysOpen() throws Exception {
        var in = System.in;
        var out = System.out;
        var captured = new ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream(GRAMMAR.getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

            assertEquals(0, JJDocMain.run(new String[] {"-"}));
            System.out.println("still open");
            assertTrue(!System.out.checkError(), "standard output was closed");
        } finally {
            System.setIn(in);
            System.setOut(out);
        }
        var text = captured.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Word ::=") && text.endsWith("still open\n"), text);
    }
}
