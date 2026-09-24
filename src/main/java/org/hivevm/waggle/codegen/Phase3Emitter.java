// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.analysis.ParserData;
import org.hivevm.waggle.model.Choice;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.Lookahead;
import org.hivevm.waggle.model.NonTerminal;
import org.hivevm.waggle.model.OneOrMore;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.Sequence;
import org.hivevm.waggle.model.ZeroOrMore;
import org.hivevm.waggle.model.ZeroOrOne;

/**
 * The body of a lookahead (jj_3) routine: the walk over an expansion that decides what the routine
 * scans, and in which order.
 *
 * <p>This walk existed three times, once per back end, as a private method of each — the same
 * expansion kinds in the same order, differing only in how a test, a backtrack and a give-up are
 * spelled (ADR-0021). It exists once now and takes a {@link ParserSyntax} for the spelling.
 *
 * <p>What a routine's header and trailer look like stays with the back end: those differ in
 * substance, not in wording.
 */
public class Phase3Emitter {

    protected final ParserSyntax syntax;
    private final ParserGenerator parser;

    public Phase3Emitter(ParserSyntax syntax, ParserGenerator parser) {
        this.syntax = syntax;
        this.parser = parser;
    }

    /**
     * Writes the scan for {@code e}, up to {@code count} tokens deep.
     *
     * @param jj3_expansion the routine this body belongs to, which decides what giving up yields
     * @param xspDeclared   whether the scan-position local has already been declared
     * @return whether it has been declared after this call
     */
    public final boolean emit(ParserData data, Expansion jj3_expansion, boolean xspDeclared,
            Expansion e, int count, LinePrinter printer) {
        if (this.parser.internalName(e).startsWith("jj_scan_token")) {
            return xspDeclared;
        }

        var failure = this.syntax.failure(this.parser.genReturn(jj3_expansion, true, data));

        switch (e) {
            case RExpression e_nrw -> {
                var name = e_nrw.getLabel().isEmpty()
                        ? data.getNameOfToken(e_nrw.getOrdinal())
                        : e_nrw.getLabel();
                this.syntax.failIfScanToken(printer,
                        this.syntax.tokenRef(name, e_nrw.getOrdinal()), failure);
            }
            case NonTerminal e_nrw -> {
                // All expansions of non-terminals have the "name" fields set. So
                // there's no need to check it below for "e_nrw" and "ntexp". In
                // fact, we rely here on the fact that the "name" fields of both these
                // variables are the same.
                var ntprod = data.getProduction(e_nrw.getName());
                var ntexp = ntprod.getExpansion();
                this.syntax.failIfCall(printer, this.parser.genjj_3Call(ntexp), failure);
            }
            case Choice e_nrw -> {
                if (e_nrw.getChoices().size() != 1) {
                    xspDeclared = declareScanPos(printer, xspDeclared);
                    this.syntax.saveScanPos(printer);
                }

                for (int i = 0; i < e_nrw.getChoices().size(); i++) {
                    var nested_seq = (Sequence) e_nrw.getChoices().get(i);
                    var la = (Lookahead) nested_seq.getUnits().getFirst();
                    var semanticLookahead = !la.getActionTokens().isEmpty();
                    if (semanticLookahead) {
                        this.syntax.beginSemanticLookahead(printer);
                        this.parser.printTokens(la.getActionTokens(), null, printer);
                        this.syntax.endSemanticLookahead(printer);
                    }
                    this.syntax.choiceAlternative(printer, this.parser.genjj_3Call(nested_seq),
                            semanticLookahead, i == (e_nrw.getChoices().size() - 1), failure);
                }
                this.syntax.endChoice(printer, e_nrw.getChoices().size());
            }
            case Sequence e_nrw -> {
                // We skip the first element in the following iteration since it is the
                // Lookahead object.
                int cnt = count;
                for (int i = 1; i < e_nrw.getUnits().size(); i++) {
                    var eseq = e_nrw.getUnits().get(i);
                    xspDeclared = emit(data, jj3_expansion, xspDeclared, eseq, cnt, printer);
                    cnt -= data.minimumSize(eseq);
                    if (cnt <= 0) {
                        break;
                    }
                }
            }
            case OneOrMore e_nrw -> {
                xspDeclared = declareScanPos(printer, xspDeclared);
                var nested_e = e_nrw.getExpansion();
                this.syntax.failIfCall(printer, this.parser.genjj_3Call(nested_e), failure);
                this.syntax.scanLoop(printer, this.parser.genjj_3Call(nested_e));
            }
            case ZeroOrMore e_nrw -> {
                xspDeclared = declareScanPos(printer, xspDeclared);
                this.syntax.scanLoop(printer,
                        this.parser.genjj_3Call(e_nrw.getExpansion()));
            }
            case ZeroOrOne e_nrw -> {
                xspDeclared = declareScanPos(printer, xspDeclared);
                this.syntax.optionalScan(printer,
                        this.parser.genjj_3Call(e_nrw.getExpansion()));
            }
            default -> {
            }
        }
        return xspDeclared;
    }

    /** Declares the scan position a lookahead backtracks to, once per routine. */
    private boolean declareScanPos(LinePrinter printer, boolean declared) {
        if (!declared) {
            this.syntax.declareScanPos(printer);
        }
        return true;
    }
}
