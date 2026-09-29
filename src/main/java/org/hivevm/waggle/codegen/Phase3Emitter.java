// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.analysis.Jj3Routine;
import org.hivevm.waggle.analysis.ParserPlan;
import org.hivevm.waggle.analysis.ScanStep;

/**
 * The body of a lookahead (jj_3) routine: writes the scan steps the planner decided, in order.
 *
 * <p>The walk over the expansion that decides what a routine scans existed three times, once per
 * back end (ADR-0021), and then once here; it is the planner's now (ADR-0029). This writes the
 * steps and takes a {@link ParserSyntax} for the spelling.
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
     * Writes the body of {@code routine}.
     *
     * @param traced the production the routine's returns trace, or {@code null}; it decides what
     *               giving up yields
     */
    public final void emit(ParserPlan data, String traced, Jj3Routine routine,
            LinePrinter printer) {
        var failure = this.syntax.failure(this.parser.genReturn(traced, true, data));

        for (var step : routine.body()) {
            switch (step) {
                case ScanStep.DeclareScanPos d -> this.syntax.declareScanPos(printer);
                case ScanStep.ScanToken t ->
                        this.syntax.failIfScanToken(printer, this.syntax.tokenRef(t.token()), failure);
                case ScanStep.FailIfCall c ->
                        this.syntax.failIfCall(printer, this.parser.genjj_3Call(c.call()), failure);
                case ScanStep.Choice c -> {
                    if (c.saveScanPos()) {
                        this.syntax.saveScanPos(printer);
                    }
                    for (var alternative : c.alternatives()) {
                        var semantic = !alternative.semantic().isEmpty();
                        if (semantic) {
                            this.syntax.beginSemanticLookahead(printer);
                            this.parser.printTokens(alternative.semantic(), null,
                                    printer);
                            this.syntax.endSemanticLookahead(printer);
                        }
                        this.syntax.choiceAlternative(printer,
                                this.parser.genjj_3Call(alternative.call()), semantic,
                                alternative.last(), failure);
                    }
                    this.syntax.endChoice(printer, c.alternatives().size());
                }
                case ScanStep.ScanLoop l ->
                        this.syntax.scanLoop(printer, this.parser.genjj_3Call(l.call()));
                case ScanStep.OptionalScan o ->
                        this.syntax.optionalScan(printer, this.parser.genjj_3Call(o.call()));
            }
        }
    }
}
