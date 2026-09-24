// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjdoc/BNFGenerator.java, org/javacc/jjdoc/TextGenerator.java

package org.hivevm.waggle.doc;

import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.api.WaggleOptions;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.NonTerminal;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.RCharacterList;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RJustName;
import org.hivevm.waggle.model.TokenProduction;

import java.io.PrintWriter;

class BNFGenerator implements DocGenerator {

    private PrintWriter ostr;
    private final WaggleOptions opts;
    private final Diagnostics diagnostics;
    private final String inputFile;
    private String outputFile;
    private boolean printing = true;

    /**
     * Constructs an instance of {@link BNFGenerator}.
     */
    public BNFGenerator(WaggleOptions opts, Diagnostics diagnostics, String inputFile) {
        this.opts = opts;
        this.diagnostics = diagnostics;
        this.inputFile = inputFile;
        this.outputFile = "standard output";
    }

    /** Where this run wrote its documentation. */
    final String outputFile() {
        return this.outputFile;
    }

    private PrintWriter create_output_stream() {

        if (this.opts.stringValue(Waggle.OUTPUT_FILE).isEmpty()) {
            if (this.inputFile.equals("standard input")) {
                return new java.io.PrintWriter(new java.io.OutputStreamWriter(System.out));
            } else {
                String ext = ".bnf";
                int i = this.inputFile.lastIndexOf('.');
                if (i == -1) {
                    this.outputFile = this.inputFile + ext;
                } else {
                    String suffix = this.inputFile.substring(i);
                    if (suffix.equals(ext)) {
                        this.outputFile = this.inputFile + ext;
                    } else {
                        this.outputFile = this.inputFile.substring(0, i) + ext;
                    }
                }
            }
        } else {
            this.outputFile = this.opts.stringValue(Waggle.OUTPUT_FILE);
        }
        try {
            this.ostr = new java.io.PrintWriter(new java.io.FileWriter(this.outputFile));
        } catch (java.io.IOException e) {
            this.diagnostics.warning("JJDoc: can't open output stream on file " + this.outputFile
                    + ".  Using standard output.");
            this.ostr = new java.io.PrintWriter(new java.io.OutputStreamWriter(System.out));
        }

        return this.ostr;
    }

    private void println() {
        print("\n");
    }

    @Override
    public void text(String s) {
        if (this.printing && !((s.length() == 1) && ((s.charAt(0) == '\n') || (s.charAt(0)
                == '\r')))) {
            print(s);
        }
    }

    @Override
    public void print(String s) {
        this.ostr.print(s);
    }

    @Override
    public void documentStart() {
        this.ostr = create_output_stream();
    }

    @Override
    public void documentEnd() {
        this.ostr.close();
    }

    @Override
    public void specialTokens(String s) {
    }

    @Override
    public void nonterminalsStart() {
    }

    @Override
    public void nonterminalsEnd() {
    }

    @Override
    public void tokensStart() {
    }

    @Override
    public void tokensEnd() {
    }

    @Override
    public void expansionEnd(Expansion e, boolean first) {
    }

    @Override
    public void nonTerminalStart(NonTerminal nt) {
    }

    @Override
    public void nonTerminalEnd(NonTerminal nt) {
    }

    @Override
    public void productionStart(NormalProduction np) {
        println();
        print(np.getLhs() + " ::= ");
    }

    @Override
    public void productionEnd(NormalProduction np) {
        println();
    }

    @Override
    public void expansionStart(Expansion e, boolean first) {
        if (!first) {
            print(" | ");
        }
    }

    @Override
    public void reStart(RExpression r) {
        if (r.getClass().equals(RJustName.class) || r.getClass().equals(RCharacterList.class)) {
            this.printing = false;
        }
    }

    @Override
    public void reEnd(RExpression r) {
        this.printing = true;
    }

    @Override
    public void handleTokenProduction(TokenProduction tp) {
        this.printing = false;
        String text = JJDoc.getStandardTokenProductionText(tp);
        text(text);
        this.printing = true;
    }
}
