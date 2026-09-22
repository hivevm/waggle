// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.model.Lookahead;
import org.hivevm.waggle.model.NonTerminal;
import org.hivevm.waggle.model.RExpression;

import java.util.List;
import java.util.function.Consumer;

/**
 * How one target language spells the pieces a lookahead routine is made of: a scan-position local,
 * a test that gives up, one alternative of a choice, a scan loop.
 *
 * <p>These used to be inlined into {@code buildPhase3RoutineRecursive}, which therefore existed
 * three times — 116 lines in Java, 102 in C++, 109 in Rust — walking the same expansions in the
 * same order and differing only in braces and indentation (ADR-0021). They are a vocabulary, not
 * behaviour, and an emitter takes one of these rather than inheriting it.
 *
 * <p>The default answers are Java's, which is why the Java back end needs none of its own — the
 * same arrangement {@link TargetSyntax} makes for the lexer (ADR-0017).
 */
public interface ParserSyntax {

    /** Java's spellings: every default below, unchanged. */
    ParserSyntax JAVA = new ParserSyntax() {
    };

    /** Declares the scan position a lookahead backtracks to, once per routine. */
    default void declareScanPos(LinePrinter printer) {
        printer.println("Token xsp;");
    }

    /** Remembers the scan position. */
    default void saveScanPos(LinePrinter printer) {
        printer.println("xsp = jj_scanpos;");
    }

    /**
     * How a token is named in a scan call: by its constant when it has a name, by its ordinal
     * otherwise.
     */
    default String tokenRef(String name, int ordinal) {
        return (name == null) ? Integer.toString(ordinal) : "ParserConstants." + name;
    }

    /** How a call to another lookahead routine is written where it is tested. */
    default String callRef(String call) {
        return call;
    }

    /** How the abandon-the-lookahead statement is written around the value it yields. */
    default String failure(String value) {
        return value;
    }

    /** Gives up unless {@code token} scans. */
    default void failIfScanToken(LinePrinter printer, String token, String failure) {
        printer.println("if (jj_scan_token(" + token + "))");
        printer.indent();
        printer.println(failure);
        printer.outdent();
    }

    /** Gives up unless the nested routine {@code call} succeeds. */
    default void failIfCall(LinePrinter printer, String call, String failure) {
        printer.println("if (" + callRef(call) + ")");
        printer.indent();
        printer.println(failure);
        printer.outdent();
    }

    /** Opens the semantic-lookahead assignment; the caller writes the action, then closes it. */
    default void beginSemanticLookahead(LinePrinter printer) {
        printer.println("jj_lookingAhead = true;");
        printer.print("jj_semLA = ");
    }

    /** Closes the semantic-lookahead assignment. */
    default void endSemanticLookahead(LinePrinter printer) {
        printer.println(";");
        printer.println("jj_lookingAhead = false;");
    }

    /** The guard a semantic lookahead puts in front of a choice's test. */
    default String semanticGuard() {
        return "!jj_semLA || ";
    }

    /**
     * One alternative of a choice: backtrack and try the next one, or — for the last — give up.
     */
    default void choiceAlternative(LinePrinter printer, String call, boolean semanticLookahead,
            boolean isLast, String failure) {
        printer.print("if (");
        if (semanticLookahead) {
            printer.print(semanticGuard());
        }
        if (isLast) {
            printer.println(callRef(call) + ")");
            printer.indent();
            printer.println(failure);
            printer.outdent();
        } else {
            printer.println(callRef(call) + ") {");
            printer.indent();
            printer.println("jj_scanpos = xsp;");
        }
    }

    /** Closes the {@code count} alternatives {@link #choiceAlternative} left open. */
    default void endChoice(LinePrinter printer, int count) {
        for (int i = 1; i < count; i++) {
            printer.outdent();
            printer.println("}");
        }
    }

    /** Scans {@code call} as often as it matches — the tail of {@code (…)*} and {@code (…)+}. */
    default void scanLoop(LinePrinter printer, String call) {
        printer.println("while (true) {");
        printer.indent();
        saveScanPos(printer);
        printer.println("if (" + callRef(call) + ") {");
        printer.indent();
        printer.println("jj_scanpos = xsp;");
        printer.println("break;");
        printer.outdent();
        printer.println("}");
        printer.outdent();
        printer.println("}");
    }

    /** Scans {@code call} if it matches, and backtracks if it does not — {@code […]}. */
    default void optionalScan(LinePrinter printer, String call) {
        saveScanPos(printer);
        printer.println("if (" + callRef(call) + ")");
        printer.indent();
        printer.println("jj_scanpos = xsp;");
        printer.outdent();
    }

    // ---- the lookahead chain: if / else if / switch default ----------------------------------

    /** Opens the condition of a purely semantic lookahead. */
    default void openSemanticCondition(LinePrinter printer, ParserGenerator.LookaheadState state,
            int index) {
        openConditionArm(printer, state, index, false);
    }

    /** Opens the condition of a syntactic lookahead, which starts on a line of its own. */
    default void openLookaheadCondition(LinePrinter printer, ParserGenerator.LookaheadState state,
            int index) {
        openConditionArm(printer, state, index, true);
    }

    /**
     * Opens one test of a lookahead chain, in whatever shape the previous one left behind. The two
     * callers above differ only in the blank line, which is why Java and C++ need one body.
     */
    private void openConditionArm(LinePrinter printer, ParserGenerator.LookaheadState state,
            int index, boolean leadingBlank) {
        switch (state) {
            case NOOPENSTM -> {
                if (leadingBlank) {
                    printer.println();
                }
                printer.print("if (");
            }
            case OPENIF -> {
                printer.println();
                printer.outdent();
                printer.print("} else if (");
            }
            case OPENSWITCH -> {
                printer.println("default: {");
                printer.indent();
                if (index >= 0) {
                    printer.println("jj_la1[" + index + "] = jj_gen;");
                }
                printer.print("if (");
            }
        }
    }

    /** Opens the arm that runs when no lookahead matched, and writes its action. */
    default void openFallback(LinePrinter printer, ParserGenerator.LookaheadState state, int index,
            Consumer<LinePrinter> action) {
        switch (state) {
            case NOOPENSTM -> action.accept(printer);
            case OPENIF -> {
                printer.println();
                printer.outdent();
                printer.print("} else {");
                printer.indent();
                action.accept(printer);
            }
            case OPENSWITCH -> {
                printer.println("default: {");
                printer.indent();
                if (index >= 0) {
                    printer.print("jj_la1[" + index + "] = jj_gen;");
                }
                action.accept(printer);
            }
        }
    }

    /** Closes a purely semantic condition and opens its block. */
    default void closeSemanticCondition(LinePrinter printer) {
        printer.print(") {");
    }

    /** Closes a syntactic lookahead condition and opens its block. */
    default void closeLookaheadCondition(LinePrinter printer) {
        printer.print(") {");
    }

    /** How deep a lookahead scans, as the target spells an unbounded one. */
    default String lookaheadAmount(Lookahead la) {
        return Integer.toString(la.getAmount());
    }

    /** The call that runs a lookahead routine. */
    default String lookaheadCall(String routine, String amount) {
        return "jj_2" + routine + "(" + amount + ")";
    }

    /**
     * Opens the token switch a one-token lookahead compiles to.
     *
     * <p>OPENIF opens the {@code else} and then writes the switch NOOPENSTM writes, and OPENSWITCH
     * writes nothing because the switch is already open. That used to be a deliberate
     * {@code switch} fall-through in every back end; stated as two conditions it needs no
     * {@code @SuppressWarnings} and no Eclipse-only marker.
     */
    default void openTokenSwitch(LinePrinter printer, ParserGenerator.LookaheadState state,
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
            printer.print(cacheTokens ? "jj_nt.kind" : "(jj_ntk == -1) ? jj_ntk_f() : jj_ntk");
            printer.println(") {");
            printer.indent();
        }
    }

    /** Writes the labels of one switch arm and opens its block. */
    default void caseLabels(LinePrinter printer, List<String> cases) {
        for (int i = 0; i < cases.size(); i++) {
            if (i > 0) {
                printer.println();
            }
            printer.print("case ");
            printer.print(cases.get(i));
            printer.print(":");
        }
        printer.print(" {");
        printer.indent();
    }

    /** Closes one switch arm after its action. */
    default void closeSwitchArm(LinePrinter printer) {
        printer.println();
        printer.println("break;");
        printer.outdent();
        printer.println("}");
    }

    /** Closes the {@code indents} blocks a lookahead chain left open. */
    default void endBlocks(LinePrinter printer, int indents) {
        for (int i = 0; i < indents; i++) {
            printer.println();
            printer.outdent();
            printer.print("}");
        }
    }

    // ---- one production's body ----------------------------------------------------------------

    /** Opens the call that consumes the next token. */
    default void consumeToken(LinePrinter printer) {
        printer.print("jj_consume_token(");
    }

    /** Closes it, assigning the token to the rule's right-hand side if it has one. */
    default void consumeTokenEnd(RExpression re, LinePrinter printer) {
        printer.print(re.getRhsToken() == null ? ");" : ")." + re.getRhsToken().image + ";");
    }

    /** What a choice does when none of its alternatives matched. */
    default void noAlternativeMatched(LinePrinter printer) {
        printer.println();
        printer.println("jj_consume_token(-1);");
        printer.print("throw new ParseException();");
    }

    /** Opens the call to another production. */
    default void callProduction(NonTerminal non, LinePrinter printer) {
        printer.print(non.getName());
        printer.print("(");
    }

    /** Closes it. */
    default void callProductionEnd(LinePrinter printer) {
        printer.print(");");
    }

    /** Opens the loop a {@code (…)*} or {@code (…)+} runs in. */
    default void openRepetition(int labelIndex, LinePrinter printer) {
        printer.println("label_" + labelIndex + ":");
        printer.print("while (true) {");
        printer.indent();
    }

    /** Leaves that loop. */
    default void breakRepetition(int labelIndex, LinePrinter printer, int offset) {
        if (offset == 1) {
            printer.print("\nbreak label_" + labelIndex + ";");
        }
    }

    /** Anything the target needs after the loop; Java and Rust need nothing. */
    default void closeRepetition(int labelIndex, LinePrinter printer) {
    }
}
