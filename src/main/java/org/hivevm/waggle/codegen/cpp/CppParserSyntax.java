// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.codegen.ParserGenerator;
import org.hivevm.waggle.codegen.ParserSyntax;
import org.hivevm.waggle.model.Lookahead;
import org.hivevm.waggle.model.RExpression;

import java.util.List;

/**
 * How C++ spells a lookahead routine (ADR-0021). It writes the routine body at a fixed indent
 * rather than through the printer's indent level, which is why almost every line carries its own
 * leading spaces.
 */
final class CppParserSyntax implements ParserSyntax {

    @Override
    public void declareScanPos(LinePrinter printer) {
        printer.println("    Token* xsp;");
    }

    @Override
    public void saveScanPos(LinePrinter printer) {
        printer.println("    xsp = jj_scanpos;");
    }

    @Override
    public String tokenRef(String name, int ordinal) {
        return (name == null) ? Integer.toString(ordinal) : name;
    }

    @Override
    public void failIfScanToken(LinePrinter printer, String token, String failure) {
        printer.println("    if (jj_scan_token(" + token + ")) " + failure);
    }

    @Override
    public void failIfCall(LinePrinter printer, String call, String failure) {
        printer.println("    if (" + call + ") " + failure);
    }

    @Override
    public void beginSemanticLookahead(LinePrinter printer) {
        printer.println("    jj_lookingAhead = true;");
        printer.print("    jj_semLA = ");
    }

    @Override
    public void endSemanticLookahead(LinePrinter printer) {
        printer.println(";");
        printer.println("    jj_lookingAhead = false;");
    }

    @Override
    public void choiceAlternative(LinePrinter printer, String call, boolean semanticLookahead,
            boolean isLast, String failure) {
        printer.print("    if (");
        if (semanticLookahead) {
            printer.print(semanticGuard());
        }
        printer.print(call + ") ");
        if (isLast) {
            printer.println(failure);
        } else {
            printer.println("{\n    jj_scanpos = xsp;");
        }
    }

    @Override
    public void endChoice(LinePrinter printer, int count) {
        for (int i = 1; i < count; i++) {
            printer.println("    }");
        }
    }

    @Override
    public void scanLoop(LinePrinter printer, String call) {
        printer.println("    while (true) {");
        printer.println("      xsp = jj_scanpos;");
        printer.println("      if (" + call + ") { jj_scanpos = xsp; break; }");
        printer.println("    }");
    }

    @Override
    public void optionalScan(LinePrinter printer, String call) {
        saveScanPos(printer);
        printer.println("    if (" + call + ") jj_scanpos = xsp;");
    }

    @Override
    public String lookaheadAmount(Lookahead la) {
        return (la.getAmount() == Integer.MAX_VALUE) ? "INT_MAX" : Integer.toString(la.getAmount());
    }

    @Override
    public void closeLookaheadCondition(LinePrinter printer) {
        printer.println(") {");
    }

    @Override
    public void openTokenSwitch(LinePrinter printer, ParserGenerator.LookaheadState state,
            boolean cacheTokens) {
        if (state == ParserGenerator.LookaheadState.OPENIF) {
            printer.println();
            printer.outdent();
            printer.print("} else {");
            printer.indent();
        }
        if ((state == ParserGenerator.LookaheadState.OPENIF)
                || (state == ParserGenerator.LookaheadState.NOOPENSTM)) {
            printer.println();
            printer.print("switch (");
            printer.print(cacheTokens ? "jj_nt->kind()" : "(jj_ntk == -1) ? jj_ntk_f() : jj_ntk");
            printer.print(") {");
            printer.indent();
        }
    }

    @Override
    public void caseLabels(LinePrinter printer, List<String> cases) {
        for (var c : cases) {
            printer.println();
            printer.print("case ");
            printer.print(c);
            printer.print(":");
        }
        printer.print(" {");
        printer.indent();
    }

    @Override
    public void closeSwitchArm(LinePrinter printer) {
        printer.println("break;");
        printer.outdent();
        printer.println("}");
    }

    @Override
    public void consumeTokenEnd(RExpression re, LinePrinter printer) {
        printer.print(re.getRhsToken() == null ? ");" : ")->" + re.getRhsToken().image + ";");
    }

    @Override
    public void noAlternativeMatched(LinePrinter printer) {
        printer.println("\njj_consume_token(-1);");
        printer.print("errorHandler->parseError(token, getToken(1), __FUNCTION__), hasError = true;");
    }

    @Override
    public void openRepetition(int labelIndex, LinePrinter printer) {
        printer.print("while (!hasError) {");
        printer.indent();
    }

    @Override
    public void breakRepetition(int labelIndex, LinePrinter printer, int offset) {
        if (offset == 0) {
            printer.print("\n;");
        } else {
            printer.print("\ngoto end_label_" + labelIndex + ";");
        }
    }

    @Override
    public void closeRepetition(int labelIndex, LinePrinter printer) {
        printer.print("\nend_label_" + labelIndex + ": ;");
    }
}
