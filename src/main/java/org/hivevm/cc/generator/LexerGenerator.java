// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.Language;
import org.hivevm.cc.lexer.LexerData;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.lexer.NfaStateData;
import org.hivevm.cc.lexer.NfaStateData.KindInfo;
import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.model.TokenKind;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Token;
import org.hivevm.source.Context;
import org.hivevm.source.LinePrinter;
import org.hivevm.source.SourceProvider;
import org.hivevm.source.Template;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import java.util.function.IntToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The {@link LexerGenerator} class.
 */
public abstract class LexerGenerator extends CodeGenerator<LexerData> {

    protected static final String LOHI_BYTES = "LOHI_BYTES";
    protected static final String NON_ASCII_TABLE = "NON_ASCII_TABLE";

    private static final String HAS_LOOP = "HAS_LOOP";
    private static final String HAS_SKIP = "HAS_SKIP";
    private static final String HAS_MORE = "HAS_MORE";
    private static final String HAS_SPECIAL = "HAS_SPECIAL";

    private static final String HAS_MOPRE_ACTIONS = "HAS_MORE_ACTIONS";
    private static final String HAS_SKIP_ACTIONS = "HAS_SKIP_ACTIONS";
    private static final String HAS_TOKEN_ACTIONS = "HAS_TOKEN_ACTIONS";
    private static final String HAS_EMPTY_MATCH = "HAS_EMPTY_MATCH";

    private static final String DEFAULT_LEX_STATE = "DEFAULT_LEX_STATE";
    private static final String MAX_LEX_STATES = "MAX_LEX_STATES";
    private static final String STATE_NAMES = "STATE_NAMES";
    private static final String KEEP_LINE_COL = "KEEP_LINE_COOL";
    private static final String STATE_COUNT = "STATE_COUNT";
    private static final String STATE_SET_SIZE = "STATE_SET_SIZE";
    private static final String DUAL_NEED = "CHECK_NADD_STATES_DUAL_NEEDED";
    private static final String UNARY_NEED = "CHECK_NADD_STATES_UNARY_NEEDED";

    protected String self() {
        return "";
    }

    protected LexerGenerator(Language language) {
        super(language);
    }

    // ---------------------------------------------------------------- dialect
    // How a target spells the moves into the next NFA state set. The defaults are the Java/C++ form;
    // Rust overrides them. These used to be "if (__IS_RUST__) … else …" inside the emitter itself,
    // four times over.

    /** Adds one state, checking first that it is not already in the set. */
    protected void printCheckNAdd(LinePrinter printer, int state) {
        printer.println("jjCheckNAdd(" + state + ");");
    }

    /** Adds one state unconditionally. */
    protected void printAddState(LinePrinter printer, int state) {
        printer.println("jjstateSet[jjnewStateCnt++] = " + state + ";");
    }

    /** Adds two states, checking each. */
    protected void printCheckNAddTwoStates(LinePrinter printer, int first, int second) {
        printer.println("jjCheckNAddTwoStates(" + first + ", " + second + ");");
    }

    /** Adds a whole range of states, checking each. */
    protected void printCheckNAddStates(LinePrinter printer, int first, int last, boolean isRange) {
        printer.print("jjCheckNAddStates(" + first);
        if (isRange) {
            printer.print(", " + last);
        }
        printer.println(");");
    }

    /** Adds a whole range of states unconditionally. */
    protected void printAddStates(LinePrinter printer, int first, int last) {
        printer.println("jjAddStates(" + first + ", " + last + ");");
    }

    /**
     * Leaves the case of a switch. Rust's match arms fall out on their own, so there it emits
     * nothing.
     */
    protected void printBreak(LinePrinter printer, String indent) {
        printer.println(indent + "break;");
    }

    // The pieces a condition is built from. Only the spelling differs per target, so they are the
    // dialect; the emitters below just compose them.

    /** "the current character equals c" */
    protected String charEquals(int c) {
        return "curChar == " + c;
    }

    /** "the current character does not equal c" */
    protected String charNotEquals(int c) {
        return "curChar != " + c;
    }

    /** "the bit for the current character is set in mask" */
    protected String bitIsSet(long mask) {
        return "(" + toHexString(mask) + " & l) != 0L";
    }

    /** "the bit for the current character is not set in mask" */
    protected String bitIsClear(long mask) {
        return "(" + toHexString(mask) + " & l) == 0L";
    }

    /** "the kind matched so far is weaker than kind" */
    protected String kindIsWeakerThan(int kind) {
        return "kind > " + kind;
    }

    /** The call that tests whether a non-ASCII character can move out of "state". */
    protected String canMove(NfaState state) {
        return "jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2)";
    }

    // A switch over states. Java and C++ write each label out as it arrives and let the cases fall
    // into one body; Rust has to join them into a single match arm, so it collects them first. Both
    // shapes are driven from the same call sites through these three hooks.

    /** Adds one label to the case that is being built. */
    protected final void printCaseLabel(LinePrinter printer, List<String> labels, int state) {
        printCaseLabel(printer, labels, "", state);
    }

    protected void printCaseLabel(LinePrinter printer, List<String> labels, String indent,
                                  int state) {
        printer.println(indent + "case " + state + ":");
    }

    /** Opens the body shared by the labels collected so far. */
    protected final void printCasesOpen(LinePrinter printer, List<String> labels) {
        printCasesOpen(printer, labels, "");
    }

    protected void printCasesOpen(LinePrinter printer, List<String> labels, String indent) {
    }

    /** Closes that body again. */
    protected void printCasesClose(LinePrinter printer) {
    }

    /**
     * The tail of the state machine: the default case, and the end of the switch and of the loop
     * around it. Rust closes a "match" inside a "loop", Java and C++ a "switch" inside a "do/while",
     * so the whole epilogue differs and lives behind this one hook.
     *
     * @param breakInDefault whether the default case breaks out — Java and C++ always do
     */
    protected void printDefaultAndEndLoop(LinePrinter printer, boolean breakInDefault) {
        printer.println("default: {");
        printer.indent();
        printer.println("break;");
        printer.outdent();
        printer.println("}");

        printer.outdent();
        printer.println("}");
        printer.outdent();
        printer.println("} while (i != startsAt);");
    }

    /** An "if" that opens a block. */
    protected void printIf(LinePrinter printer, String condition) {
        printer.println("if (" + condition + ") {");
    }

    /**
     * An "if" over a single statement. Java and C++ leave the braces out; Rust has no such form, so
     * there it opens a block that {@link #printEndIf} closes again.
     */
    protected final void printIfNoBlock(LinePrinter printer, String condition) {
        printIfNoBlock(printer, "if ", condition);
    }

    /** The same, but as a link in an "else if" chain: "prefix" is the "if " or "else if " part. */
    protected void printIfNoBlock(LinePrinter printer, String prefix, String condition) {
        printer.println(prefix + "(" + condition + ")");
    }

    /** Closes what {@link #printIfNoBlock} opened — nothing at all, unless the target needs braces. */
    protected void printEndIf(LinePrinter printer) {
    }

    /**
     * Emits the move into the next state set of "state". This was written out four times, verbatim,
     * across DumpAsciiMove, DumpAsciiMoveForCompositeState, DumpNonAsciiMove and
     * DumpNonAsciiMoveForCompositeState.
     */
    private void printNextStates(LinePrinter printer, NfaStateData data, NfaState state,
                                 boolean nextIntersects) {
        if ((state.next == null) || (state.next.usefulEpsilonMoves <= 0)) {
            return;
        }

        int[] stateNames = data.getNextStates(state.next.epsilonMovesString);

        if (state.next.usefulEpsilonMoves == 1) {
            if (nextIntersects) {
                printCheckNAdd(printer, stateNames[0]);
            } else {
                printAddState(printer, stateNames[0]);
            }
            return;
        }

        if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
            printCheckNAddTwoStates(printer, stateNames[0], stateNames[1]);
            return;
        }

        int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
        boolean isRange = (indices[0] + 1) != indices[1];

        if (nextIntersects) {
            data.global.setCheckNAddStates(isRange);
            printCheckNAddStates(printer, indices[0], indices[1], isRange);
        } else {
            printAddStates(printer, indices[0], indices[1]);
        }
    }

    @Override
    public final void generate(LexerData data) {
        var options = Template.newContext(data.options());
        options.add(LexerGenerator.LOHI_BYTES, data.getLohiByte())
                .set("LOHI_BYTES_INDEX", i -> i)
                .set("LOHI_BYTES_VALUE", i -> getLohiBytes(data, i));
        options.add(LexerGenerator.NON_ASCII_TABLE, data.getNonAsciiTableForMethod())
                .set("NON_ASCII_TABLE_NAME", this::getNonAsciiMethod)
                .set("NON_ASCII_TABLE_METHOD", (s, w) -> dumpNonAsciiMoveMethod(data, s, w));

        options.set(LexerGenerator.HAS_SKIP, data.hasSkip());
        options.set(LexerGenerator.HAS_MORE, data.hasMore());
        options.set(LexerGenerator.HAS_LOOP, data.hasLoop());
        options.set(LexerGenerator.HAS_SPECIAL, data.hasSpecial());

        options.set(LexerGenerator.HAS_MOPRE_ACTIONS, data.hasMoreActions());
        options.set(LexerGenerator.HAS_SKIP_ACTIONS, data.hasSkipActions());
        options.set(LexerGenerator.HAS_TOKEN_ACTIONS, data.hasTokenActions());
        options.set(LexerGenerator.HAS_EMPTY_MATCH, data.hasEmptyMatch());

        options.set(LexerGenerator.DEFAULT_LEX_STATE, data.defaultLexState());
        options.add(LexerGenerator.MAX_LEX_STATES, data.maxLexStates())
                .set(LexerGenerator.MAX_LEX_STATES + "_INDEX", i -> i);
        options.add(LexerGenerator.STATE_NAMES, data.getStateNames())
                .set(LexerGenerator.STATE_NAMES + "_VALUE", i -> i);
        options.set(LexerGenerator.STATE_COUNT, data.getStateCount());
        options.set(LexerGenerator.STATE_SET_SIZE, data.stateSetSize());
        options.set(LexerGenerator.STATE_SET_SIZE + "_2", data.stateSetSize() * 2);
        options.set(LexerGenerator.KEEP_LINE_COL, data.keepLineCol());
        options.set(LexerGenerator.DUAL_NEED, data.jjCheckNAddStatesDualNeeded());
        options.set(LexerGenerator.UNARY_NEED, data.jjCheckNAddStatesUnaryNeeded());

        options.set("DUMP_SKIP_ACTIONS", p -> dumpSkipActions(p, data));
        options.set("DUMP_MORE_ACTIONS", p -> dumpMoreActions(p, data));
        options.set("DUMP_TOKEN_ACTIONS", p -> dumpTokenActions(p, data));

        options.set("DUMP_STATE_SETS", p -> dumpStateSets(p, data));
        options.set("DUMP_GET_NEXT_TOKEN", p -> dumpGetNextToken(p, data));
        options.set("DUMP_STATIC_VAR_DECLARATIONS", p -> dumpStaticVarDeclarations(p, data));
        options.set("DUMP_NFA_AND_DFA", w ->
                data.getStateNames().forEach(name -> dump_nfa_and_dfa(data.getStateData(name), w))
        );

        generate(data, options);

        // Generate Constants
        options = Template.newContext(data.options());
        options.add("STATES", data.getStateCount())
                .set("STATES_INDEX", i -> i)
                .set("STATES_NAME", data::getStateName);
        options.add("TOKENS", data.getOrderedsTokens())
                .set("TOKENS_ORDINAL", RExpression::getOrdinal)
                .set("TOKENS_LABEL", RExpression::getLabel);

        var expressions = new ArrayList<RExpression>();
        for (var production : data.getTokenProductions()) {
            for (var spec : production.getRespecs()) {
                expressions.add(spec.rexp);
            }
        }
        options.add("REXPRESSION_COUNT", expressions.size() + 1)
                .set("REXPRESSION_INDEX", i -> i)
                .set("REXPRESSION_LABEL", (i, w) -> getRegExp(w, i, expressions, false))
                .set("REXPRESSION_IMAGE", (i, w) -> getRegExp(w, i, expressions, true));

        getConstantsTemplate().render(options, options.getParserName());
    }

    protected abstract SourceProvider getConstantsTemplate();

    protected abstract void generate(LexerData data, Context context);

    protected String getNonAsciiMethod(NfaState state) {
        return "" + state.nonAsciiMethod;
    }

    /** Opens {@code jjCanMove_N}. Only C++ needs a method of its own; the others are inlined. */
    protected void printCanMoveSignature(LinePrinter printer, LexerData data, NfaState state) {
    }

    /** Closes the method opened by {@link #printCanMoveSignature}. */
    protected void printCanMoveEnd(LinePrinter printer) {
        printer.outdent();
    }

    /** One {@code case} of the high-byte dispatch. */
    protected void printCanMoveCase(LinePrinter printer, int hiByte) {
        printer.println("case " + hiByte + ":");
        printer.indent();
    }

    protected void printCanMoveCaseEnd(LinePrinter printer) {
        printer.outdent();
    }

    protected void printCanMoveDefault(LinePrinter printer) {
        printer.println("default:");
        printer.indent();
    }

    /** The single-vector answer: the character is in the set iff its bit is set. */
    protected void printCanMoveReturnBitVector(LinePrinter printer, int vector) {
        printer.println("return ((jjbitVec" + vector + "[i2] & l2) != " + longZero() + ");");
    }

    protected void printCanMoveReturnTrue(LinePrinter printer) {
        printer.println("return true;");
    }

    /**
     * One arm of the default branch: a pair of bit vectors, the first indexed by the high byte, the
     * second by the low byte. An all-ones vector needs no test at all.
     */
    protected void printCanMoveArm(LinePrinter printer, int hiVector, int loVector, boolean testHi,
                                   boolean testLo) {
        int nested = 0;
        if (testHi) {
            printer.println("if ((jjbitVec" + hiVector + "[i1] & l1) != " + longZero() + ")");
            printer.indent();
            nested++;
        }
        if (testLo) {
            printer.println("if ((jjbitVec" + loVector + "[i2] & l2) == " + longZero() + ")");
            printer.indent();
            printer.println("return false;");
            printer.outdent();
            printer.println("else");
            printer.indent();
            nested++;
        }
        printer.println("return true;");
        if (nested > 0) {
            printer.outdent();
        }
    }

    /**
     * Emits {@code jjCanMove_N}: whether a non-ASCII character is in the state's character set. The
     * set is stored as a two-level bit vector, indexed by the high and the low byte.
     */
    protected final void dumpNonAsciiMoveMethod(LexerData data, NfaState state, LinePrinter printer) {
        printCanMoveSignature(printer, data, state);

        for (int j = 0; j < state.loByteVec.size(); j += 2) {
            printCanMoveCase(printer, state.loByteVec.get(j));
            int vector = state.loByteVec.get(j + 1);
            if (NfaState.AllBitsSet(data.getAllBitVectors(vector))) {
                printCanMoveReturnTrue(printer);
            } else {
                printCanMoveReturnBitVector(printer, vector);
            }
            printCanMoveCaseEnd(printer);
        }

        printCanMoveDefault(printer);
        for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
            int hiVector = state.nonAsciiMoveIndices[j - 2];
            int loVector = state.nonAsciiMoveIndices[j - 1];
            printCanMoveArm(printer, hiVector, loVector,
                    !NfaState.AllBitsSet(data.getAllBitVectors(hiVector)),
                    !NfaState.AllBitsSet(data.getAllBitVectors(loVector)));
        }
        printCanMoveEnd(printer);
    }




    /** A lone zero keeps an empty C-style array from being a syntax error. */
    protected void printEmptyStateSet(LinePrinter printer) {
        printer.print("0");
    }

    /** Opens the {@code jjnextStates} array. */
    protected void printNextStatesOpen(LinePrinter printer, LexerData data) {
        printer.print("static final int[] jjnextStates = {");
    }

    /** The flattened NFA state sets the DFA jumps into, 16 per line. */
    protected final void dumpStateSets(LinePrinter printer, LexerData data) {
        printNextStatesOpen(printer, data);
        printer.indent();

        if (data.getOrderedStateSet().isEmpty()) {
            printEmptyStateSet(printer);
        } else {
            int cnt = 0;
            for (int[] set : data.getOrderedStateSet()) {
                for (int element : set) {
                    if ((cnt++ % 16) == 0) {
                        printer.println();
                    }
                    printer.print(element + ", ");
                }
            }
        }

        printer.println();
        printer.outdent();
        printArrayClose(printer);
    }

    /** The lexical actions of the EOF token, emitted into the token-manager template. */
    protected void printEofTokenActions(LinePrinter printer) {
        printer.println("    TokenLexicalActions(matchedToken);");
    }

    /** Closes the EOF branch the template opened and starts the token loop. */
    protected void printGetNextTokenPrologue(LinePrinter printer) {
        printer.println("    return matchedToken;");
        printer.println("}");
    }

    /** Resets the image buffer the lexical actions append to. */
    protected void printImageInit(LinePrinter printer) {
        printer.println("image = jjimage;");
        printer.println("image.setLength(0);");
        printer.println("jjimageLen = 0;");
    }

    /** The loop a MORE production jumps back to. */
    protected void printEofLoop(LinePrinter printer) {
        printer.println("for (; ; ) {");
    }

    /** Dispatches on the current lexical state. This also sets up the start state of the NFA. */
    protected void printSwitchOnLexState(LinePrinter printer) {
        printer.println("switch (curLexState) {");
    }

    protected void printLexStateCase(LinePrinter printer, int state) {
        printer.println("case " + state + ": {");
    }

    protected void printLexStateCaseEnd(LinePrinter printer) {
        printer.println("break;");
        printer.outdent();
        printer.println("}");
    }

    protected void printSwitchOnLexStateEnd(LinePrinter printer) {
        printer.outdent();
        printer.println("}");
    }

    /** Without a lexical state there is nothing to match. */
    protected void printNoLexState(LinePrinter printer) {
        printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    }

    /** Skips over the characters that can only ever be skipped, without going through the NFA. */
    protected void printSkipSingles(LinePrinter printer, LexerData data, int state) {
        // the backup(0) is there to make the JIT happy
        printer.println("try {");
        printer.indent();
        printer.println("input_stream.backup(0);");

        long lower = data.singlesToSkip(state).asciiMoves[0];
        long upper = data.singlesToSkip(state).asciiMoves[1];
        if ((lower != 0L) && (upper != 0L)) {
            printer.println("while ((curChar < 64 && (0x" + Long.toHexString(lower)
                    + "L & (1L << curChar)) != 0L) || \n"
                    + "          (curChar >> 6) == 1 && (0x" + Long.toHexString(upper)
                    + "L & (1L << (curChar & 077))) != 0L)");
        } else if (upper == 0L) {
            printer.println("while (curChar <= " + (int) LexerGenerator.MaxChar(lower)
                    + " && (0x" + Long.toHexString(lower) + "L & (1L << curChar)) != 0L)");
        } else if (lower == 0L) {
            printer.println("while (curChar > 63 && curChar <= "
                    + (LexerGenerator.MaxChar(upper) + 64) + " && (0x" + Long.toHexString(upper)
                    + "L & (1L << (curChar & 077))) != 0L)");
        }

        if (data.options().getDebugTokenManager()) {
            printer.println(" {");
            printer.indent();
            printer.println("debugStream.println("
                    + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                    + "\"Skipping character : \" + TokenException.addEscapes(String.valueOf((char) curChar)) + \" (\" + (int)curChar + \")\");");
        }

        printer.println("curChar = input_stream.BeginToken();");

        if (data.options().getDebugTokenManager()) {
            printer.outdent();
            printer.println("}");
        }

        printer.outdent();
        printer.println("} catch (java.io.IOException e1) { continue EOFLoop; }");
    }

    /** A lexical state can start out having matched the empty string. */
    protected void printInitialMatch(LinePrinter printer, LexerData data, int state) {
        if (hasInitialMatch(data, state)) {
            if (data.options().getDebugTokenManager()) {
                printer.println("debugStream.println(\"   Matched the empty string as \" + " + tokenImages() + "["
                        + data.initMatch(state) + "] + \" token.\");");
            }
            printer.println("jjmatchedKind = " + data.initMatch(state) + ";");
            printer.println("jjmatchedPos = -1;");
            printer.println("curPos = 0;");
        } else {
            printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
            printer.println("jjmatchedPos = 0;");
        }
    }

    /** Whether the lexical state matches the empty string. */
    protected static boolean hasInitialMatch(LexerData data, int state) {
        return (data.initMatch(state) != Integer.MAX_VALUE) && (data.initMatch(state) != 0);
    }

    protected void printDebugCurrentCharacter(LinePrinter printer, LexerData data) {
        printer.println("debugStream.println("
                + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                + "\"Current character : \" + TokenException.addEscapes(String.valueOf((char) curChar)) + \" (\" + (int)curChar + \") "
                + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
    }

    protected void printMoveStringLiteralDfa0Call(LinePrinter printer, int state) {
        printer.println("curPos = jjMoveStringLiteralDfa0_" + state + "();");
    }

    /** The state has a catch-all token that matches whatever the string-literal DFA did not. */
    protected void printCanMatchAnyChar(LinePrinter printer, LexerData data, int state) {
        int kind = data.canMatchAnyChar(state);
        if (hasInitialMatch(data, state)) {
            printer.println("if (jjmatchedPos < 0 || (jjmatchedPos == 0 && jjmatchedKind > " + kind
                    + ")) {");
        } else {
            printer.println("if (jjmatchedPos == 0 && jjmatchedKind > " + kind + ") {");
        }
        printer.indent();

        if (data.options().getDebugTokenManager()) {
            printer.println("debugStream.println(\"Current character matched as a \" + " + tokenImages() + "["
                    + kind + "] + \" token.\");");
        }
        printer.println("jjmatchedKind = " + kind + ";");

        if (hasInitialMatch(data, state)) {
            printer.println("jjmatchedPos = 0;");
        }

        printer.outdent();
        printer.println("}");
    }

    protected void printIfMatchedKind(LinePrinter printer) {
        printer.println("if (jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ") {");
    }

    /** Puts back the characters the DFA read past the longest match. */
    protected void printBackupBlock(LinePrinter printer, LexerData data) {
        printer.println("if (jjmatchedPos + 1 < curPos) {");
        printer.indent();

        if (data.options().getDebugTokenManager()) {
            printer.println("debugStream.println("
                    + "\"   Putting back \" + (curPos - jjmatchedPos - 1) + \" characters into the input stream.\");");
        }

        printer.println("input_stream.backup(curPos - jjmatchedPos - 1);");
        printer.outdent();
        printer.println("}");
    }

    protected void printDebugFoundMatch(LinePrinter printer) {
        printer.println("debugStream.println("
                + "\"****** FOUND A \" + " + tokenImages() + "[jjmatchedKind] + \" MATCH "
                + "(\" + TokenException.addEscapes(new String(input_stream.GetSuffix(jjmatchedPos + 1))) + "
                + "\") ******\\n\");");
    }

    protected void printIfToToken(LinePrinter printer) {
        printer.println("if ((jjtoToken[jjmatchedKind >> 6] & "
                + "(1L << (jjmatchedKind & 077))) != 0L) {");
    }

    /** The matched kind is a TOKEN: build it, run its actions and hand it to the parser. */
    protected void printTokenBranch(LinePrinter printer, LexerData data) {
        printer.println("matchedToken = jjFillToken();");

        if (data.hasSpecial()) {
            printer.println("matchedToken.specialToken = specialToken;");
        }
        if (data.hasTokenActions()) {
            printer.println("TokenLexicalActions(matchedToken);");
        }
        if (data.maxLexStates() > 1) {
            printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
            printer.indent();
            printer.println("curLexState = jjnewLexState[jjmatchedKind];");
            printer.outdent();
        }
        printer.println("return matchedToken;");
    }

    /** The matched kind is a SKIP or a SPECIAL_TOKEN: keep it out of the parser's way. */
    protected void printSkipBranch(LinePrinter printer, LexerData data) {
        if (data.hasMore()) {
            printer.print("else if ((jjtoSkip[jjmatchedKind >> 6] & (1L << (jjmatchedKind & 077))) != 0L)");
        } else {
            printer.print("else");
        }

        printer.println(" {");
        printer.indent();

        if (data.hasSpecial()) {
            printer.println("if ((jjtoSpecial[jjmatchedKind >> 6] & "
                    + "(1L << (jjmatchedKind & 077))) != 0L) {");
            printer.indent();

            printer.println("matchedToken = jjFillToken();");
            printer.println("if (specialToken == null)");
            printer.indent();
            printer.println("specialToken = matchedToken;");
            printer.outdent();
            printer.println("else {");
            printer.indent();
            printer.println("matchedToken.specialToken = specialToken;");
            printer.println("specialToken = (specialToken.next = matchedToken);");
            printer.outdent();
            printer.println("}");

            if (data.hasSkipActions()) {
                printer.println("SkipLexicalActions(matchedToken);");
            }

            printer.outdent();
            printer.print("}");

            if (data.hasSkipActions()) {
                printer.println(" else");
                printer.indent();
                printer.println("SkipLexicalActions(null);");
                printer.outdent();
            } else {
                printer.println();
            }
        } else if (data.hasSkipActions()) {
            printer.println("SkipLexicalActions(null);");
        }

        if (data.maxLexStates() > 1) {
            printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
            printer.indent();
            printer.println("curLexState = jjnewLexState[jjmatchedKind];");
            printer.outdent();
        }

        printer.println("continue EOFLoop;");
        printer.outdent();
        printer.println("}");
    }

    /** The matched kind is a MORE: keep the image and go round again. */
    protected void printMoreBranch(LinePrinter printer, LexerData data) {
        if (data.hasMoreActions()) {
            printer.println("MoreLexicalActions();");
        } else if (data.hasSkipActions() || data.hasTokenActions()) {
            printer.println("jjimageLen += jjmatchedPos + 1;");
        }

        if (data.maxLexStates() > 1) {
            printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
            printer.indent();
            printer.println("curLexState = jjnewLexState[jjmatchedKind];");
            printer.outdent();
        }
        printer.println("curPos = 0;");
        printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

        printer.println("try {");
        printer.indent();
        printer.println("curChar = input_stream.readChar();");

        if (data.options().getDebugTokenManager()) {
            printer.println("debugStream.println("
                    + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                    + "\"Current character : \" + "
                    + "TokenException.addEscapes(String.valueOf((char) curChar)) + \" (\" + (int)curChar + \") "
                    + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
        }
        printer.println("continue;");
        printer.outdent();
        printer.println("} catch (java.io.IOException e1) {");
        printer.println("}");
    }

    /** Nothing matched: report where the input went wrong. */
    protected void printLexicalErrorEpilogue(LinePrinter printer) {
        printer.outdent();
        printer.print("""
                }
                int error_line = input_stream.getEndLine();
                int error_column = input_stream.getEndColumn();
                String error_after = null;
                boolean EOFSeen = false;
                try {
                    input_stream.readChar();
                    input_stream.backup(1);
                } catch (java.io.IOException e1) {
                    EOFSeen = true;
                    error_after = curPos <= 1 ? "" : input_stream.GetImage();
                    if (curChar == '\\n' || curChar == '\\r') {
                        error_line++;
                        error_column = 0;
                    } else
                        error_column++;
                }
                if (!EOFSeen) {
                    input_stream.backup(1);
                    error_after = curPos <= 1 ? "" : input_stream.GetImage();
                }
                throw new TokenException(EOFSeen, curLexState, error_line, error_column, error_after, curChar, TokenException.LEXICAL_ERROR);
                """);
    }

    /**
     * Emits the body of {@code getNextToken}: skip what can be skipped, run the string-literal DFA,
     * fall back to the NFA, then dispatch the matched kind to TOKEN, SKIP/SPECIAL_TOKEN or MORE.
     */
    protected final void dumpGetNextToken(LinePrinter printer, LexerData data) {
        boolean debug = data.options().getDebugTokenManager();
        boolean notPlainToken = data.hasSkip() || data.hasMore() || data.hasSpecial();

        if (data.hasEof()) {
            printEofTokenActions(printer);
        }
        printGetNextTokenPrologue(printer);

        if (data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions()) {
            printImageInit(printer);
        }

        printer.println();

        if (data.hasMore()) {
            printEofLoop(printer);
            printer.indent();
        }

        if (data.maxLexStates() > 1) {
            printSwitchOnLexState(printer);
            printer.indent();
        }

        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.maxLexStates() > 1) {
                printLexStateCase(printer, i);
                printer.indent();
            }

            if (data.singlesToSkip(i).HasTransitions()) {
                printSkipSingles(printer, data, i);
            }

            printInitialMatch(printer, data, i);

            if (debug) {
                printDebugCurrentCharacter(printer, data);
            }

            printMoveStringLiteralDfa0Call(printer, i);

            if (data.canMatchAnyChar(i) != -1) {
                printCanMatchAnyChar(printer, data, i);
            }

            if (data.maxLexStates() > 1) {
                printLexStateCaseEnd(printer);
            }
        }

        if (data.maxLexStates() > 1) {
            printSwitchOnLexStateEnd(printer);
        } else if (data.maxLexStates() == 0) {
            printNoLexState(printer);
        }

        if (data.maxLexStates() > 0) {
            printIfMatchedKind(printer);
            printer.indent();

            printBackupBlock(printer, data);

            if (debug) {
                printDebugFoundMatch(printer);
            }

            if (notPlainToken) {
                printIfToToken(printer);
                printer.indent();
            }

            printTokenBranch(printer, data);

            if (notPlainToken) {
                printer.outdent();
                printer.println("}");

                if (data.hasSkip() || data.hasSpecial()) {
                    printSkipBranch(printer, data);
                }
                if (data.hasMore()) {
                    printMoreBranch(printer, data);
                }
            }

            printLexicalErrorEpilogue(printer);
        }

        if (data.hasMore()) {
            printer.outdent();
            printer.println("}");
        }
    }

    /** Opens the array that maps a token kind to the lexical state it switches to. */
    protected void printLexStateArrayOpen(LinePrinter printer, LexerData data) {
        printer.println();
        printer.print("public static final int[] jjnewLexState = {");
    }

    /** Opens one of the {@code jjtoToken}/{@code jjtoSkip}/… bit vectors. */
    protected void printBitVectorOpen(LinePrinter printer, LexerData data, String name) {
        printer.print("static final " + longType() + "[] " + name + " = {");
    }

    /** Closes an array opened by one of the two hooks above. */
    protected void printArrayClose(LinePrinter printer) {
        printer.println("};");
    }

    /** Emits one {@code jjto…} bit vector, 64 token kinds per element, 4 elements per line. */
    private void dumpBitVector(LinePrinter printer, LexerData data, String name,
                               IntToLongFunction bits) {
        printBitVectorOpen(printer, data, name);
        printer.indent();
        for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
            if ((i % 4) == 0) {
                printer.println();
            }
            printer.print(toHexString(bits.applyAsLong(i)) + ", ");
        }
        printer.println();
        printer.outdent();
        printArrayClose(printer);
    }

    /** The tables the lexer reads at run time: the lexical-state map and the four kind bit vectors. */
    protected final void dumpStaticVarDeclarations(LinePrinter printer, LexerData data) {
        if (data.maxLexStates() > 1) {
            printLexStateArrayOpen(printer, data);
            printer.indent();
            for (int i = 0; i < data.maxOrdinal(); i++) {
                if ((i % 25) == 0) {
                    printer.println();
                }
                String state = data.newLexState(i);
                printer.print((state == null ? -1 : data.getStateIndex(state)) + ", ");
            }
            printer.println();
            printer.outdent();
            printArrayClose(printer);
        }

        if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
            dumpBitVector(printer, data, "jjtoToken", data::toToken);
        }
        if (data.hasSkip() || data.hasSpecial()) {
            dumpBitVector(printer, data, "jjtoSkip", data::toSkip);
        }
        if (data.hasSpecial()) {
            dumpBitVector(printer, data, "jjtoSpecial", data::toSpecial);
        }
        if (data.hasMore()) {
            dumpBitVector(printer, data, "jjtoMore", data::toMore);
        }
    }

    /** Renders one entry of the token-image table. */
    protected void printTokenImage(LinePrinter printer, String image) {
        printer.print("\"" + image + "\"");
    }

    /**
     * Renders a string literal's entry. Only C++ has two tables — the label and the raw image — and
     * so only C++ looks at {@code isImage}; the other back ends have no {@code REXPRESSION_IMAGE}
     * placeholder in their templates.
     */
    protected void printStringLiteralImage(LinePrinter printer, RStringLiteral literal,
                                           boolean isImage) {
        printer.print("\"\\\"" + Encoding.escape(Encoding.escape(literal.getImage())) + "\\\"\"");
    }

    /** Separates two entries of the table. C++ emits one array per entry and needs none. */
    protected void printImageSeparator(LinePrinter printer, int i, List<RExpression> expressions) {
        if ((i == 0) || (expressions.indexOf(expressions.get(i - 1)) < (expressions.size() - 1))) {
            printer.print(",");
        }
    }

    /** One entry of the table the parser reports an unexpected token with. */
    protected final void getRegExp(LinePrinter printer, int i, List<RExpression> expressions,
                                   boolean isImage) {
        if (i == 0) {
            printTokenImage(printer, "<EOF>");
        } else {
            var re = expressions.get(i - 1);
            if (re instanceof RStringLiteral literal) {
                printStringLiteralImage(printer, literal, isImage);
            } else if (!re.getLabel().isEmpty()) {
                printTokenImage(printer, "<" + re.getLabel() + ">");
            } else if (re.getTokenKind() == TokenKind.TOKEN) {
                JavaCCErrors.warning(re,
                        "Consider giving this non-string token a label for better error reporting.");
            } else {
                printTokenImage(printer, "<token of kind " + re.getOrdinal() + ">");
            }
        }
        printImageSeparator(printer, i, expressions);
    }
// --------------------------------------- RString

    public static <T> String[] re_arrange(Hashtable<String, T> tab) {
        var ret = new String[tab.size()];
        int cnt = 0;

        for (var s : tab.keySet()) {
            int i = 0, j;
            char c = s.charAt(0);

            while ((i < cnt) && (ret[i].charAt(0) < c)) {
                i++;
            }

            if (i < cnt) {
                for (j = cnt - 1; j >= i; j--) {
                    ret[j + 1] = ret[j];
                }
            }

            ret[i] = s;
            cnt++;
        }

        return ret;
    }

    private int GetLine(LexerData data, int kind) {
        return data.getRegExp(kind).getLine();
    }

    private int GetColumn(LexerData data, int kind) {
        return data.getRegExp(kind).getColumn();
    }

    protected final void show_warning_intermediate(NfaStateData data, int i, int j, int k) {
        JavaCCErrors.warning(
                " \"" + Encoding.escape(data.global.getImage((j * 64) + k))
                        + "\" cannot be matched as a string literal token  at line "
                        + GetLine(data.global, (j * 64) + k) + ", column "
                        + GetColumn(data.global, (j * 64) + k)
                        + ". It will be matched as " + GetLabel(data.global,
                        data.getIntermediateKinds()[((j * 64) + k)][i]) + ".");
    }

    protected final void show_warning_match(NfaStateData data, int i, int j, int k) {
        JavaCCErrors.warning(
                " \"" + Encoding.escape(data.global.getImage((j * 64) + k))
                        + "\" cannot be matched as a string literal token  at line "
                        + GetLine(data.global, (j * 64) + k) + ", column "
                        + GetColumn(data.global, (j * 64) + k)
                        + ". It will be matched as " + GetLabel(data.global,
                        data.global.canMatchAnyChar(data.getStateIndex())) + ".");
    }

    protected final int GetStateSetForKind(NfaStateData data, int pos, int kind) {
        if (data.isMixedState() || (data.generatedStates() == 0)) {
            return -1;
        }

        var allStateSets = data.statesForPos[pos];
        if (allStateSets == null) {
            return -1;
        }

        for (var s : allStateSets.keySet()) {
            long[] actives = allStateSets.get(s);

            s = s.substring(s.indexOf(", ") + 2);
            s = s.substring(s.indexOf(", ") + 2);
            if (s.equals("null;")) {
                continue;
            }

            if ((actives != null) && ((actives[kind / 64] & (1L << (kind % 64))) != 0L)) {
                return AddCompositeStateSet(data, s);
            }
        }
        return -1;
    }

    protected final boolean CanStartNfaUsingAscii(NfaStateData data, char c) {
        if (c >= 128) {
            throw new IllegalStateException(
                    "CanStartNfaUsingAscii called with a non-ASCII character: " + (int) c);
        }

        var move = data.getInitialState().GetEpsilonMovesString();
        if ((move == null) || move.equals("null;")) {
            return true;
        }

        int[] states = data.getNextStates(move);
        for (int state : states) {
            NfaState tmp = data.getIndexedState(state);
            if ((tmp.asciiMoves[c / 64] & (1L << (c % 64))) != 0L) {
                return false;
            }
        }
        return true;
    }

    // ////////////////////////// NFaState

    private void re_arrange(NfaStateData data) {
        List<NfaState> v = data.cloneAllStates();

        if (data.getAllStateCount() != data.generatedStates()) {
            throw new IllegalStateException("NFA state count changed while rearranging: "
                    + data.getAllStateCount() + " states, but " + data.generatedStates()
                    + " were generated");
        }

        for (NfaState tmp : v) {
            if ((tmp.stateName != -1) && !tmp.dummy) {
                data.setAllState(tmp.stateName, tmp);
            }
        }
    }

    private void FixStateSets(NfaStateData data) {
        Hashtable<String, int[]> fixedSets = new Hashtable<>();
        int[] tmp = new int[data.generatedStates()];
        int i;

        for (String s : data.stateSetsToFix.keySet()) {
            int[] toFix = data.stateSetsToFix.get(s);
            int cnt = 0;

            // System.out.print("Fixing : ");
            for (i = 0; i < toFix.length; i++) {
                // System.out.print(toFix[i] + ", ");
                if (toFix[i] != -1) {
                    tmp[cnt++] = toFix[i];
                }
            }

            int[] fixed = new int[cnt];
            System.arraycopy(tmp, 0, fixed, 0, cnt);
            fixedSets.put(s, fixed);
            data.setNextStates(s, fixed);
        }

        for (i = 0; i < data.getAllStateCount(); i++) {
            NfaState tmpState = data.getAllState(i);
            int[] newSet;

            if ((tmpState.next == null) || (tmpState.next.usefulEpsilonMoves == 0)) {
                continue;
            }

            if ((newSet = fixedSets.get(tmpState.next.epsilonMovesString)) != null) {
                tmpState.FixNextStates(newSet);
            }
        }
    }

    private void dump_nfa_and_dfa(NfaStateData stateData, LinePrinter printer) {
        if (stateData.hasNFA && !stateData.isMixedState())
            dumpNfaStartStatesCode(printer, stateData, stateData.statesForPos);
        dumpDfaCode(printer, stateData);
        if (stateData.hasNFA) {
            prepareNfaStates(stateData);
            dumpMoveNfa(printer, stateData);
        }
    }

    /** The signature of {@code jjStopStringLiteralDfa}. */
    protected void printStopStringLiteralDfaSignature(LinePrinter printer, NfaStateData data,
                                                      int maxKindsReqd) {
        printer.print("private final int jjStopStringLiteralDfa" + data.getLexerStateSuffix()
                + "(int pos");
        for (int i = 0; i < maxKindsReqd; i++) {
            printer.print(", " + longType() + " active" + i);
        }
        printer.println(") {");
    }

    /** The signature of {@code jjStartNfa}. */
    protected void printStartNfaSignature(LinePrinter printer, NfaStateData data,
                                          int maxKindsReqd) {
        printer.print("private final int jjStartNfa" + data.getLexerStateSuffix() + "(int pos");
        for (int i = 0; i < maxKindsReqd; i++) {
            printer.print(", " + longType() + " active" + i);
        }
        printer.println(") {");
    }

    /** Hands the position the string-literal DFA stopped at over to the NFA. */
    protected void printStartNfaBody(LinePrinter printer, NfaStateData data, String arguments) {
        printer.println("    return " + moveNfaName(data) + "(" + stopStringLiteralDfaName(data)
                + "(pos, " + arguments + "), pos + 1);");
    }

    /** The name {@code jjMoveNfa} is called under. */
    protected String moveNfaName(NfaStateData data) {
        return "jjMoveNfa" + data.getLexerStateSuffix();
    }

    /** The name {@code jjStopStringLiteralDfa} is called under. */
    protected String stopStringLiteralDfaName(NfaStateData data) {
        return "jjStopStringLiteralDfa" + data.getLexerStateSuffix();
    }

    /** The value {@code jjStopStringLiteralDfa} returns when no NFA state is left to go to. */
    protected String noState() {
        return "-1";
    }

    /** Dispatches on how far the string-literal DFA got. */
    protected void printSwitchOnPos(LinePrinter printer) {
        printer.println("switch (pos) {");
    }

    protected void printPosCase(LinePrinter printer, int pos) {
        printer.println("case " + pos + ":");
    }

    protected void printPosCaseEnd(LinePrinter printer) {
    }

    protected void printPosDefault(LinePrinter printer) {
        printer.println("default:");
        printer.indent();
        printer.println("return " + noState() + ";");
        printer.outdent();
    }

    /** Opens the body of a guard. Rust has no braceless {@code if}. */
    protected void printStopDfaBodyOpen(LinePrinter printer, boolean hasKind) {
        if (hasKind) {
            printer.println(" {");
        } else {
            printer.println();
        }
    }

    protected void printStopDfaBodyClose(LinePrinter printer, boolean hasKind) {
        if (hasKind) {
            printer.println("}");
        }
    }

    /** The trace {@code jjStopStringLiteralDfa} writes when it is entered. */
    protected void printDebugNoMoreStringLiteralMatches(LinePrinter printer) {
        printer.println(
                "debugStream.println(\"   No more string literal token matches are possible.\");");
    }

    /** {@code active0, active1, …} — the arguments the two functions above are called with. */
    private static String activeArguments(int maxKindsReqd) {
        return IntStream.range(0, maxKindsReqd).mapToObj(i -> "active" + i)
                .collect(Collectors.joining(", "));
    }

    /**
     * Emits {@code jjStopStringLiteralDfa}, which reports how far the string-literal DFA got, and
     * {@code jjStartNfa}, which hands the result over to the NFA.
     */
    protected final void dumpNfaStartStatesCode(LinePrinter printer, NfaStateData data,
                                                Hashtable<String, long[]>[] statesForPos) {
        if (data.getMaxStrKind() == 0) { // there is no string literal to stop on
            return;
        }

        int maxKindsReqd = (data.getMaxStrKind() / 64) + 1;
        boolean condGenerated = false;

        printer.println();
        printStopStringLiteralDfaSignature(printer, data, maxKindsReqd);
        printer.indent();

        if (data.global.options().getDebugTokenManager()) {
            printDebugNoMoreStringLiteralMatches(printer);
        }

        printSwitchOnPos(printer);
        printer.indent();

        for (int i = 0; i < (data.getMaxLen() - 1); i++) {
            if (statesForPos[i] == null) {
                continue;
            }

            printPosCase(printer, i);
            printer.indent();

            for (String stateSetString : statesForPos[i].keySet()) {
                long[] actives = statesForPos[i].get(stateSetString);

                for (int j = 0; j < maxKindsReqd; j++) {
                    if (actives[j] == 0L) {
                        continue;
                    }

                    printer.print(condGenerated ? " || " : "if (");
                    condGenerated = true;
                    printer.print("(active" + j + " & " + toHexString(actives[j]) + ") != "
                            + longZero());
                }

                if (!condGenerated) {
                    continue;
                }
                printer.print(")");

                int ind = stateSetString.indexOf(", ");
                String kindStr = stateSetString.substring(0, ind);
                String afterKind = stateSetString.substring(ind + 2);
                int matchedPos = Integer.parseInt(afterKind.substring(0, afterKind.indexOf(", ")));
                boolean hasKind = !kindStr.equals(String.valueOf(Integer.MAX_VALUE));

                printStopDfaBodyOpen(printer, hasKind);
                printer.indent();

                if (hasKind) {
                    if (i == 0) {
                        printer.println(matchedKind() + " = " + kindStr + ";");

                        int initMatch = data.global.initMatch(data.getStateIndex());
                        if ((initMatch != 0) && (initMatch != Integer.MAX_VALUE)) {
                            printer.println(matchedPos() + " = 0;");
                        }
                    } else if (i == matchedPos) {
                        if (data.isSubStringAtPos(i)) {
                            printer.println("if (" + matchedPos() + " != " + i + ")  {");
                            printer.indent();
                            printer.println(matchedKind() + " = " + kindStr + ";");
                            printer.println(matchedPos() + " = " + i + ";");
                            printer.outdent();
                            printer.println("}");
                        } else {
                            printer.println(matchedKind() + " = " + kindStr + ";");
                            printer.println(matchedPos() + " = " + i + ";");
                        }
                    } else {
                        if (matchedPos > 0) {
                            printer.print("if (" + matchedPos() + " < " + matchedPos + ")");
                        } else {
                            printer.print("if (" + matchedPos() + " == 0)");
                        }
                        printer.println(" {");
                        printer.indent();
                        printer.println(matchedKind() + " = " + kindStr + ";");
                        printer.println(matchedPos() + " = " + matchedPos + ";");
                        printer.outdent();
                        printer.println("}");
                    }
                }

                String stateSet = afterKind.substring(afterKind.indexOf(", ") + 2);
                if (stateSet.equals("null;")) {
                    printer.println("return " + noState() + ";");
                } else {
                    printer.println("return " + getCompositeStateSet(data, stateSet) + ";");
                }

                printer.outdent();
                printStopDfaBodyClose(printer, hasKind);
                condGenerated = false;
            }

            printer.println("return " + noState() + ";");
            printer.outdent();
            printPosCaseEnd(printer);
        }

        printPosDefault(printer);
        printer.outdent();
        printer.println("}");
        printer.outdent();
        printer.println("}");

        printer.println();
        printStartNfaSignature(printer, data, maxKindsReqd);

        String arguments = activeArguments(maxKindsReqd);
        if (data.isMixedState()) {
            if (data.generatedStates() != 0) {
                printer.println("    return " + moveNfaName(data) + "(" + InitStateName(data)
                        + ", pos + 1);");
            } else {
                printer.println("    return pos + 1;");
            }
        } else {
            printStartNfaBody(printer, data, arguments);
        }
        printer.println("}");
    }

    // ---------------------------------------------------------------- DFA prologue

    /** The declaration of the trivial jjMoveStringLiteralDfa0, used when there is nothing to match. */
    protected void printMoveStringLiteralDfa0Signature(LinePrinter printer, NfaStateData data) {
        printer.println("private int jjMoveStringLiteralDfa0" + data.getLexerStateSuffix() + "() {");
    }

    /** The declaration of jjStopAtPos. */
    protected void printStopAtPosSignature(LinePrinter printer, NfaStateData data) {
        printer.println();
        printer.println("private int " + "jjStopAtPos(int pos, int kind) {");
    }

    /** The field holding the kind matched so far. */
    protected String matchedKind() {
        return "jjmatchedKind";
    }

    /** The field holding the position matched so far. */
    protected String matchedPos() {
        return "jjmatchedPos";
    }

    /** Returns a value from the emitted function. Rust's tail expression carries no "return". */
    protected void printReturn(LinePrinter printer, String expression) {
        printer.println("return " + expression + ";");
    }

    /** The trace written when no string literal can match any more. */
    protected void printDebugNoMoreMatches(LinePrinter printer) {
        printer.println("debugStream.println(\"No more string literal token matches are possible.\");");
        printer.println("debugStream.println(\"Currently matched the first \" + (jjmatchedPos + 1) + " + "\" characters as a \" + " + tokenImages() + "[jjmatchedKind] + \" token.\");");
    }

    /**
     * The string-literal DFA. The prologue below was written out three times, once per target; only
     * the declarations, the field names and the shape of a "return" ever differed. What is left per
     * target is the state machine itself.
     */
    protected final void dumpDfaCode(LinePrinter printer, NfaStateData data) {
        if (data.getMaxLen() == 0) {
            printer.println();
            printMoveStringLiteralDfa0Signature(printer, data);
            printer.indent();
            if (data.generatedStates() > 0)
                printer.println("return " + self() + "jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", 0);");
            else
                printer.println("return 1;");
            printer.outdent();
            printer.println("}");
            return;
        }

        if (!data.global.isBoilerPlateDumped()) {
            printStopAtPosSignature(printer, data);
            printer.indent();
            printer.println(matchedKind() + " = kind;");
            printer.println(matchedPos() + " = pos;");

            if (data.global.options().getDebugTokenManager()) {
                printDebugNoMoreMatches(printer);
            }

            printReturn(printer, "pos + 1");
            printer.outdent();
            printer.println("}");
            data.global.setBoilerPlateDumped(true);
        }

        dumpDfaStates(printer, data);
    }

/** The declaration of jjMoveStringLiteralDfa<i>, up to the opening parenthesis. */
    protected void printMoveStringLiteralDfaHead(LinePrinter printer, NfaStateData data, int i) {
        printer.print("private int jjMoveStringLiteralDfa" + i + data.getLexerStateSuffix() + "(");
    }

    /** The 64-bit integer type of the target. */
    protected String longType() {
        return "long";
    }

    /**
     * The signature of jjMoveStringLiteralDfa<i>, including its "active"/"old" bit-vector parameters.
     *
     * <p>Java and C++ build a parameter list that starts empty, so each parameter after the first has
     * to prepend a comma; Rust always leads with "&mut self" and therefore overrides this wholesale.
     */
    protected void printMoveStringLiteralDfaSignature(LinePrinter printer, NfaStateData data, int i,
                                                      int maxLongsReqd) {
        boolean atLeastOne = false;
        int j;

        printer.println();
        printMoveStringLiteralDfaHead(printer, data, i);

        if (i != 0) {
            if (i == 1) {
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= data.getMaxLenForActive(j)) {
                        if (atLeastOne) {
                            printer.print(", ");
                        } else {
                            atLeastOne = true;
                        }
                        printer.print(longType() + " active" + j);
                    }
                }

                if (i <= data.getMaxLenForActive(j)) {
                    if (atLeastOne) {
                        printer.print(", ");
                    }
                    printer.print(longType() + " active" + j);
                }
            } else {
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        if (atLeastOne) {
                            printer.print(", ");
                        } else {
                            atLeastOne = true;
                        }
                        printer.print(longType() + " old" + j + ", " + longType() + " active" + j);
                    }
                }

                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    if (atLeastOne) {
                        printer.print(", ");
                    }
                    printer.print(longType() + " old" + j + ", " + longType() + " active" + j);
                }
            }
        }

        printer.println(") {");
        printer.indent();
    }

    /**
     * The early exit of jjMoveStringLiteralDfa<i>: when no bit of the "active" vectors survives being
     * masked with "old", no string literal can match any more.
     *
     * <p>Java and C++ fold the masking into the test itself — "(active0 &= old0) | …" — which Rust
     * cannot express, since an assignment is not a value there. Rust therefore overrides this and
     * emits a "let" per vector first.
     */

    protected void printActiveCheck(LinePrinter printer, NfaStateData data, int i,
                                   int maxLongsReqd) {
        int j;

        if (i > 1) {
            var atLeastOne = false;
            printer.print("if ((");

            for (j = 0; j < (maxLongsReqd - 1); j++) {
                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    if (atLeastOne) {
                        printer.print(" | ");
                    } else {
                        atLeastOne = true;
                    }
                    printer.print("(active" + j + " &= old" + j + ")");
                }
            }

            if (i <= (data.getMaxLenForActive(j) + 1)) {
                if (atLeastOne)
                    printer.print(" | ");
                printer.print("(active" + j + " &= old" + j + ")");
            }
            printer.println(") == 0L) {");
            printer.indent();

            if (!data.isMixedState() && (data.generatedStates() != 0)) {
                printer.print("return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 2) + ", ");
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        printer.print("old" + j + ", ");
                    } else {
                        printer.print("0L, ");
                    }
                }
                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    printer.println("old" + j + ");");
                } else {
                    printer.println("0L);");
                }
            } else if (data.generatedStates() != 0) {
                printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "("
                        + InitStateName(data) + ", " + (i - 1) + ");");
            } else {
                printer.println("return " + i + ";");
            }
            printer.outdent();
            printer.println("}");
        }
    }

    /**
     * The DEBUG_TOKEN_MANAGER trace listing the string literals that can still match. Every target
     * spells its logging differently — Java concatenates, C++ builds a printf format — so this is a
     * pure dialect method.
     */

    protected void printDebugPossibleMatches(LinePrinter printer, NfaStateData data, int i) {
        if ((i != 0) && data.global.options().getDebugTokenManager()) {
            printer.println("if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
            printer.println("    debugStream.println(\"   Currently matched the first \" + " + "(jjmatchedPos + 1) + \" characters as a \" + " + tokenImages() + "[jjmatchedKind] + \" token.\");");
            printer.println("    debugStream.println(\"   Possible string literal matches : { \"");

            for (int vecs = 0; vecs < ((data.getMaxStrKind() / 64) + 1); vecs++) {
                if (i <= data.getMaxLenForActive(vecs)) {
                    printer.println(" +");
                    printer.print("         jjKindsForBitVector(" + vecs + ", ");
                    printer.print("active" + vecs + ") ");
                }
            }

            printer.println(" + \" } \");");
        }
    }

    /** Opens the guard around reading the next character. */
    protected void printReadCharGuardOpen(LinePrinter printer) {
        printer.println("try {");
        printer.println("    curChar = input_stream.readChar();");
        printer.println("} catch (java.io.IOException e) {");
    }

    /** What follows the guard — the targets that do not read inside it read here. */
    protected void printReadCharAfterGuard(LinePrinter printer) {
    }

    /** The zero of a 64-bit literal. */
    protected String longZero() {
        return "0L";
    }

    /** The call that records how far the string-literal DFA got. */
    protected String stopStringLiteralDfaCall(NfaStateData data, int i) {
        return "jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ";
    }

    /** The call that hands control to the NFA. */
    protected String moveNfaCall(NfaStateData data, int position) {
        return "jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + position
                + ")";
    }

    /** The trace of what has been matched so far. */
    protected void printDebugCurrentlyMatched(LinePrinter printer) {
        printer.println("if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
        printer.println("    debugStream.println(\"   Currently matched the first \" + " + "(jjmatchedPos + 1) + \" characters as a \" + " + tokenImages() + "[jjmatchedKind] + \" token.\");");
    }

    /**
     * Reads the next character and, when the input is exhausted, bails out of the string-literal DFA.
     * The guard around the read differs per target (try/catch, endOfInput, Result), the body does not.
     */
    protected void printEofBailout(LinePrinter printer, NfaStateData data, int i, int maxLongsReqd) {
        int k;

        printReadCharGuardOpen(printer);
        printer.indent();

        if (!data.isMixedState() && (data.generatedStates() != 0)) {
            printer.print(stopStringLiteralDfaCall(data, i));
            for (k = 0; k < (maxLongsReqd - 1); k++) {
                if (i <= data.getMaxLenForActive(k)) {
                    printer.print("active" + k + ", ");
                } else {
                    printer.print(longZero() + ", ");
                }
            }

            if (i <= data.getMaxLenForActive(k))
                printer.println("active" + k + ");");
            else
                printer.println(longZero() + ");");

            if (data.global.options().getDebugTokenManager()) {
                printDebugCurrentlyMatched(printer);
            }
            printer.println("return " + i + ";");
        } else if (data.generatedStates() != 0)
            printer.println("return " + moveNfaCall(data, i - 1) + ";");
        else
            printer.println("return " + i + ";");

        printer.outdent();
        printer.println("}");
        printReadCharAfterGuard(printer);
    }

    /** The switch over the current character. */
    protected void printSwitchOnChar(LinePrinter printer) {
        printer.println("switch (curChar) {");
    }

    /** A case label for a character, without a body of its own. */
    protected void printCharCase(LinePrinter printer, int c) {
        printer.println("case " + c + ":");
    }

    /** A case label for a character that opens its own body. */
    protected void printCharCaseWithBody(LinePrinter printer, int c) {
        printer.println("case " + c + ": {");
    }

    /** The default case. */
    protected void printDefaultCaseOpen(LinePrinter printer) {
        printer.println("default: {");
    }

    /**
     * Whether the literal behind "key" needs no case of its own in the string-literal DFA: it is a
     * plain SKIP — no action, no lexical state change — and the token manager deals with it elsewhere.
     *
     * <p>The C++ back end had lost this check; only the now-dead loop that computes "j" was left
     * behind. It therefore emitted a case per skipped character (space, tab, newline) that Java and
     * Rust leave out.
     */
    protected final boolean isPlainSkip(NfaStateData data, KindInfo info, int i, int maxLongsReqd,
                                        char c) {
        if (!((i == 0) && (c < 128) && info.hasFinalKindCnt()
                && ((data.generatedStates() == 0) || CanStartNfaUsingAscii(data, c)))) {
            return false;
        }

        int j;
        for (j = 0; j < maxLongsReqd; j++) {
            if (info.finalKinds[j] != 0L) {
                break;
            }
        }

        int kind;
        for (int k = 0; k < 64; k++) {
            if (((info.finalKinds[j] & (1L << k)) != 0L) && !data.isSubString(kind = ((j * 64) + k))) {
                if (((data.getIntermediateKinds() != null)
                        && (data.getIntermediateKinds()[((j * 64) + k)] != null)
                        && (data.getIntermediateKinds()[((j * 64) + k)][i] < ((j * 64) + k))
                        && (data.getIntermediateMatchedPos() != null)
                        && (data.getIntermediateMatchedPos()[((j * 64) + k)][i] == i))
                        || ((data.global.canMatchAnyChar(data.getStateIndex()) >= 0)
                        && (data.global.canMatchAnyChar(data.getStateIndex()) < ((j * 64) + k)))) {
                    return false;
                } else if (((data.global.toSkip(kind / 64) & (1L << (kind % 64))) != 0L)
                        && ((data.global.toSpecial(kind / 64) & (1L << (kind % 64))) == 0L)
                        && (data.global.actions(kind) == null)
                        && (data.global.newLexState(kind) == null)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The trace of the character the token manager is looking at. */
    protected void printDebugCurrentCharacter(LinePrinter printer, NfaStateData data) {
        printer.println("debugStream.println("
                        + (data.global.maxLexStates() > 1
                        ? "\"<\" + lexStateNames[curLexState] + \">\" + "
                        : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf((char) curChar)) + \" (\" + (int)curChar + \") "
                        + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
    }

    /** The trace saying that no string literal can match any more. */
    protected void printDebugNoMatchPossible(LinePrinter printer) {
        printer.println("    debugStream.println(\"   No string literal matches possible.\");");
    }

    // ---------------------------------------------------------------- lexical actions
    // Names and shapes the three action dumpers need. Java is the default; Rust renames, C++ reads
    // through a "reader" and wraps the whole thing in a method of its own.

    /** The table of token names the trace prints. It lives outside the lexer in Java. */
    protected String tokenImages() {
        return "ParserConstants.tokenImage";
    }

    /** The reader method that returns the text of the current match. */
    protected String getSuffix() {
        return "GetSuffix";
    }

    /** How the input stream is addressed. */
    protected String inputStream() {
        return "input_stream.";
    }

    /** The line the current token started on. */
    protected String beginLine() {
        return inputStream() + "getBeginLine()";
    }

    /** The column the current token started on. */
    protected String beginColumn() {
        return inputStream() + "getBeginColumn()";
    }

    /** The position matched so far, unqualified. */
    protected String matchedPosVar() {
        return "jjmatchedPos";
    }

    /** The table of string-literal images. */
    protected String strLiteralImages() {
        return "jjstrLiteralImages";
    }

    /** The length of the current match. */
    protected String lengthOfMatch() {
        return "lengthOfMatch";
    }

    /** How much of the image has been consumed. */
    protected String imageLen() {
        return "jjimageLen";
    }

    /**
     * Opens the block the lexical actions live in. C++ needs a method of its own; for Java and Rust
     * the template already provides it, so nothing is emitted.
     *
     * @param preamble a line to run before the switch, or null
     */
    protected void printActionsPrologue(LinePrinter printer, LexerData data, String method,
                                        String preamble) {
    }

    /** Closes it again. */
    protected void printActionsEpilogue(LinePrinter printer) {
    }

    /** The case for one token kind. */
    protected void printActionCase(LinePrinter printer, int kind) {
        printer.println("      case " + kind + " :");
    }

    /** What a MORE appends: it keeps the image, so there is no length to record. */
    protected void printImageAppendMore(LinePrinter printer, LexerData data, int i) {
        printer.print("         image.append");
        if (data.getImage(i) != null) {
            printer.println("(" + strLiteralImages() + "[" + i + "]);");
        } else {
            printer.println("(" + inputStream() + getSuffix() + "(" + imageLen() + "));");
        }
    }

    /** Empties the image buffer. */
    protected void printImageReset(LinePrinter printer) {
        printer.println("      image.setLength(0);");
    }

    /** Leaves that case. A Rust match arm falls out on its own. */
    protected void printActionBreak(LinePrinter printer) {
        printer.println("         break;");
    }

    /** Closes that case. */
    protected void printActionCaseEnd(LinePrinter printer) {
    }

    /** Reports the empty-match loop the lexer has run into. */
    protected void printLoopDetected(LinePrinter printer) {
        printer.println("               throw new TokenException("
                + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                + "at line \" + " + beginLine() + " + \", "
                + "column \" + " + beginColumn() + " + \".\"), TokenException.LOOP_DETECTED);");
    }

    /**
     * Guards against a token production that matches the empty string over and over: if the lexer is
     * back at the same line and column with nothing consumed, it is looping.
     */
    protected void printEmptyLoopCheck(LinePrinter printer, LexerData data, int i) {
        printer.println("         if (" + matchedPosVar() + " == -1)");
        printer.println("         {");
        printer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
        printer.println("                jjemptyLineNo[" + data.getState(i) + "] == " + beginLine() + " &&");
        printer.println("                jjemptyColNo[" + data.getState(i) + "] == " + beginColumn() + ")");
        printLoopDetected(printer);
        printer.println("            jjemptyLineNo[" + data.getState(i) + "] = " + beginLine() + ";");
        printer.println("            jjemptyColNo[" + data.getState(i) + "] = " + beginColumn() + ";");
        printer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
        printer.println("         }");
    }

    /** Appends what was matched to the image being built. */
    protected void printImageAppend(LinePrinter printer, LexerData data, int i, String indent) {
        printer.print(indent + "image.append");
        if (data.getImage(i) != null) {
            printer.println("(" + strLiteralImages() + "[" + i + "]);");
            printer.println("        " + lengthOfMatch() + " = " + strLiteralImages() + "[" + i
                    + "].length();");
        } else {
            printer.println("(" + inputStream() + getSuffix() + "(" + imageLen() + " + (" + lengthOfMatch()
                    + " = " + matchedPosVar() + " + 1)));");
        }
    }

    /** The lexical actions of the TOKEN productions. */
    protected void dumpTokenActions(LinePrinter printer, LexerData data) {
        printActionsPrologue(printer, data, "TokenLexicalActions(Token *matchedToken)", null);

        Outer:
        for (int i = 0; i < data.maxOrdinal(); i++) {
            if ((data.toToken(i / 64) & (1L << (i % 64))) == 0L) {
                continue;
            }

            for (; ; ) {
                Action act = data.actions(i);
                if (((act == null) || act.getActionTokens().isEmpty())
                        && !data.canLoop(data.getState(i))) {
                    continue Outer;
                }

                printActionCase(printer, i);

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    printEmptyLoopCheck(printer, data, i);
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                if (i == 0) {
                    printImageReset(printer); // for EOF there is no image
                } else {
                    printImageAppend(printer, data, i, "        ");
                }

                setup_token(act.getActionTokens().getFirst());
                reset_column();

                printActionToken(printer, act);
                printer.println();

                break;
            }

            printActionBreak(printer);
            printActionCaseEnd(printer);
        }

        printActionsEpilogue(printer);
    }

    /** The lexical actions of the MORE productions. */
    protected void dumpMoreActions(LinePrinter printer, LexerData data) {
        printActionsPrologue(printer, data, "MoreLexicalActions()",
                "   " + imageLen() + " += (" + lengthOfMatch() + " = " + matchedPosVar() + " + 1);");

        Outer:
        for (int i = 0; i < data.maxOrdinal(); i++) {
            if ((data.toMore(i / 64) & (1L << (i % 64))) == 0L) {
                continue;
            }

            for (; ; ) {
                Action act = data.actions(i);
                if (((act == null) || act.getActionTokens().isEmpty())
                        && !data.canLoop(data.getState(i))) {
                    continue Outer;
                }

                printActionCase(printer, i);

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    printEmptyLoopCheck(printer, data, i);
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                printImageAppendMore(printer, data, i);

                printer.println("         " + imageLen() + " = 0;");
                setup_token(act.getActionTokens().getFirst());
                reset_column();

                printActionToken(printer, act);
                printer.println();

                break;
            }

            printActionBreak(printer);
            printActionCaseEnd(printer);
        }

        printActionsEpilogue(printer);
    }

    /** The lexical actions of the SKIP productions. */
    protected void dumpSkipActions(LinePrinter printer, LexerData data) {
        printActionsPrologue(printer, data, "SkipLexicalActions(Token *matchedToken)", null);

        Outer:
        for (int i = 0; i < data.maxOrdinal(); i++) {
            if ((data.toSkip(i / 64) & (1L << (i % 64))) == 0L) {
                continue;
            }

            for (; ; ) {
                Action act = data.actions(i);
                if (((act == null) || act.getActionTokens().isEmpty())
                        && !data.canLoop(data.getState(i))) {
                    continue Outer;
                }

                printActionCase(printer, i);

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    printEmptyLoopCheck(printer, data, i);
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                printImageAppend(printer, data, i, "         ");

                setup_token(act.getActionTokens().getFirst());
                reset_column();

                printActionToken(printer, act);
                printer.println();

                break;
            }

            printActionBreak(printer);
            printActionCaseEnd(printer);
        }

        printActionsEpilogue(printer);
    }

    /**
     * The per-state code of the string-literal DFA. Java and C++ share it verbatim; every place they
     * used to differ is now one of the dialect hooks above. Rust still overrides it.
     */
    /**
     * The per-state code of the string-literal DFA. Java and C++ share it verbatim — every place
     * they used to differ is now one of the dialect hooks above. Rust still overrides it.
     */
    protected void dumpDfaStates(LinePrinter printer, NfaStateData data) {
        Hashtable<String, ?> tab;
        String key;
        KindInfo info;
        int maxLongsReqd = (data.getMaxStrKind() / 64) + 1;
        int i, j, k;
        boolean ifGenerated;

        for (i = 0; i < data.getMaxLen(); i++) {
            boolean startNfaNeeded = false;
            tab = data.getCharPosKind(i);
            var keys = LexerGenerator.re_arrange(tab);

            printMoveStringLiteralDfaSignature(printer, data, i, maxLongsReqd);

            if (i != 0) {
                printActiveCheck(printer, data, i, maxLongsReqd);

printDebugPossibleMatches(printer, data, i);

                printEofBailout(printer, data, i, maxLongsReqd);
            }

            if ((i != 0) && data.global.options().getDebugTokenManager()) {
                printDebugCurrentCharacter(printer, data);
            }

            printSwitchOnChar(printer);
            printer.indent();

            for (String key2 : keys) {
                key = key2;
                info = (KindInfo) tab.get(key);
                ifGenerated = false;
                char c = key.charAt(0);

                if (isPlainSkip(data, info, i, maxLongsReqd, c)) {
                    continue;
                }

                // Since we know key is a single character ...
                if (data.ignoreCase()) {
                    if (c != Character.toUpperCase(c)) {
                        printCharCase(printer, Character.toUpperCase(c));
                    }

                    if (c != Character.toLowerCase(c)) {
                        printCharCase(printer, Character.toLowerCase(c));
                    }
                }

                printCharCaseWithBody(printer, c);
                printer.indent();

                long matchedKind;
                if (info.hasFinalKindCnt()) {
                    for (j = 0; j < maxLongsReqd; j++) {
                        if ((matchedKind = info.finalKinds[j]) == 0L) {
                            continue;
                        }

                        for (k = 0; k < 64; k++) {
                            if ((matchedKind & (1L << k)) == 0L) {
                                continue;
                            }

                            if (ifGenerated)
                                printer.print("else if ");
                            else if (i != 0)
                                printer.print("if ");

                            ifGenerated = true;

                            int kindToPrint;
                            if (i != 0) {
                                printer.print("((active" + j + " & " + toHexString(1L << k) + ") != 0L)");
                            }

                            if ((data.getIntermediateKinds() != null) && (
                                    data.getIntermediateKinds()[((j * 64) + k)] != null)
                                    && (data.getIntermediateKinds()[((j * 64) + k)][i] < ((j * 64) + k))
                                    && (data.getIntermediateMatchedPos() != null)
                                    && (data.getIntermediateMatchedPos()[((j * 64) + k)][i] == i)) {
                                show_warning_intermediate(data, i, j, k);
                                kindToPrint = data.getIntermediateKinds()[((j * 64) + k)][i];
                            } else if ((i == 0) && (data.global.canMatchAnyChar(data.getStateIndex())
                                    >= 0)
                                    && (data.global.canMatchAnyChar(data.getStateIndex()) < ((j * 64)
                                    + k))) {
                                show_warning_match(data, i, j, k);
                                kindToPrint = data.global.canMatchAnyChar(data.getStateIndex());
                            } else {
                                kindToPrint = (j * 64) + k;
                            }

                            if (!data.isSubString((j * 64) + k)) {
                                int stateSetName = GetStateSetForKind(data, i, (j * 64) + k);

                                if (stateSetName != -1) {
                                    printer.println("return jjStartNfaWithStates"
                                            + data.getLexerStateSuffix() + "(" + i
                                            + ", " + kindToPrint + ", " + stateSetName + ");");
                                } else {
                                    printer.println("return jjStopAtPos" + "(" + i + ", " + kindToPrint + ");");
                                }
                            } else if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex())
                                    != Integer.MAX_VALUE)) || (i
                                    != 0)) {
                                printer.println(" {");
                                printer.indent();
                                printer.println("jjmatchedKind = " + kindToPrint + ";");
                                printer.println("jjmatchedPos = " + i + ";");
                                printer.outdent();
                                printer.println("}");
                            } else {
                                printer.println("jjmatchedKind = " + kindToPrint + ";");
                            }
                        }
                    }
                }

                if (info.hasValidKindCnt()) {
                    var atLeastOne = false;

                    if (i == 0) {
                        printer.print("return jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");
                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= data.getMaxLenForActive(j)) {
                                if (atLeastOne)
                                    printer.print(", ");
                                else
                                    atLeastOne = true;

                                printer.print(toHexString(info.validKinds[j]));
                            }
                        }

                        if ((i + 1) <= data.getMaxLenForActive(j)) {
                            if (atLeastOne)
                                printer.print(", ");

                            printer.print(toHexString(info.validKinds[j]));
                        }
                    } else {
                        printer.print("return jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");

                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                                if (atLeastOne)
                                    printer.print(", ");
                                else
                                    atLeastOne = true;

                                if (info.validKinds[j] != 0L)
                                    printer.print("active" + j + ", " + toHexString(info.validKinds[j]));
                                else
                                    printer.print("active" + j + ", 0L");
                            }
                        }

                        if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                printer.print(", ");
                            if (info.validKinds[j] != 0L)
                                printer.print("active" + j + ", " + toHexString(info.validKinds[j]));
                            else
                                printer.print("active" + j + ", 0L");
                        }
                    }

                    printer.println(");");
                } else { // A very special case.
                    if ((i == 0) && data.isMixedState()) {

                        if (data.generatedStates() != 0) {
                            printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", 0);");
                        } else {
                            printer.println("return 1;");
                        }
                    } else if (i != 0) // No more str literals to look for
                    {
                        printer.println("break;");
                        startNfaNeeded = true;
                    }
                }

                printer.outdent();
                printer.println("}");
            }

            printDefaultCaseOpen(printer);
            printer.indent();

            if (data.global.options().getDebugTokenManager()) {
                printDebugNoMatchPossible(printer);
            }

            if (data.generatedStates() != 0) {
                if (i == 0) {
                    // This means no string literal is possible. Just move nfa with this guy and return.
                    printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "("
                            + InitStateName(data) + ", 0);");
                } else {
                    printer.println("break;");
                    startNfaNeeded = true;
                }
            } else {
                printer.println("return " + (i + 1) + ";");
            }

            printer.outdent();
            printer.println("}");

            printer.outdent();
            printer.println("}");

            if ((i != 0) && startNfaNeeded) {
                if (!data.isMixedState() && (data.generatedStates() != 0)) {
                    /*
                     * Here, a string literal is successfully matched and no more string literals are
                     * possible. So set the kind and state set upto and including this position for the
                     * matched string.
                     */

                    printer.print("return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k))
                            printer.print("active" + k + ", ");
                        else
                            printer.print("0L, ");
                    }
                    if (i <= data.getMaxLenForActive(k))
                        printer.println("active" + k + ");");
                    else
                        printer.println("0L);");
                } else if (data.generatedStates() != 0)
                    printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + i + ");");
                else
                    printer.println("return " + (i + 1) + ";");
            }

            printer.outdent();
            printer.println("}");
        }

        if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
            DumpStartWithStates(printer, data);
        }
    }

    /** The signature of {@code jjStartNfaWithStates}. */
    protected void printStartNfaWithStatesSignature(LinePrinter printer, NfaStateData data) {
        printer.println();
        printer.println("private int jjStartNfaWithStates" + data.getLexerStateSuffix()
                + "(int pos, int kind, int state) {");
    }

    /** Reads the next character; on end of input the match found so far is the answer. */
    protected void printReadCharOrReturn(LinePrinter printer) {
        printer.println("try {");
        printer.println("    curChar = input_stream.readChar();");
        printer.println("} catch (java.io.IOException e) {");
        printer.println("    return pos + 1;");
        printer.println("}");
    }

    /**
     * Emits {@code jjStartNfaWithStates}: the string-literal DFA matched, but the NFA may still find
     * a longer match, so hand it the state it left off in.
     */
    protected final void DumpStartWithStates(LinePrinter printer, NfaStateData data) {
        boolean debug = data.global.options().getDebugTokenManager();

        printStartNfaWithStatesSignature(printer, data);
        printer.indent();
        printer.println(matchedKind() + " = kind;");
        printer.println(matchedPos() + " = pos;");

        if (debug) {
            printDebugNoMoreMatches(printer);
        }

        printReadCharOrReturn(printer);

        if (debug) {
            printDebugCurrentCharacter(printer, data);
        }

        printer.println("return " + moveNfaName(data) + "(state, pos + 1);");
        printer.outdent();
        printer.println("}");
    }

    /** The signature of {@code jjMoveNfa}. */
    protected void printMoveNfaSignature(LinePrinter printer, NfaStateData data) {
        printer.println("private int jjMoveNfa" + data.getLexerStateSuffix()
                + "(int startState, int curPos) {");
    }

    /**
     * A mixed lexical state can reach the NFA with a string literal already matched. The prologue
     * saves that match, rewinds the reader and starts over at position 0; the epilogue below decides
     * which of the two matches wins.
     */
    protected void printMoveNfaMixedPrologue(LinePrinter printer) {
        printer.print("""
                int strKind = jjmatchedKind;
                int strPos = jjmatchedPos;
                int seenUpto;
                input_stream.backup(seenUpto = curPos + 1);
                try {
                    curChar = input_stream.readChar();
                } catch (java.io.IOException e) {
                    throw new Error("Internal Error");
                }
                curPos = 0;
                """);
    }

    /** See {@link #printMoveNfaMixedPrologue(LinePrinter)}. */
    protected void printMoveNfaMixedEpilogue(LinePrinter printer) {
        printer.print("""
                if (jjmatchedPos > strPos)
                    return curPos;

                int toRet = Math.max(curPos, seenUpto);
                if (curPos < toRet)
                    for (i = toRet - Math.min(curPos, seenUpto); i-- > 0; )
                        try {
                            curChar = input_stream.readChar();
                        } catch (java.io.IOException e) {
                            throw new Error("Internal Error : Please send a bug report.");
                        }

                if (jjmatchedPos < strPos) {
                    jjmatchedKind = strKind;
                    jjmatchedPos = strPos;
                } else if (jjmatchedPos == strPos && jjmatchedKind > strKind)
                    jjmatchedKind = strKind;

                return toRet;
                """);
    }

    /** The endless loop the NFA runs in. */
    protected void printForEver(LinePrinter printer) {
        printer.println("for (; ; ) {");
    }

    /**
     * Swaps the two halves of {@code jjstateSet} — the states reached in this round become the states
     * to advance in the next — and leaves the loop when no state is left.
     */
    protected void printSwapStateSets(LinePrinter printer, NfaStateData data) {
        printer.println("if ((i = jjnewStateCnt) == (startsAt = " + data.generatedStates()
                + " - (jjnewStateCnt = startsAt)))");
        printer.indent();
        printer.println(data.isMixedState() ? "break;" : "return curPos;");
        printer.outdent();
    }

    /** Reads the next character; on end of input the NFA is done. */
    protected void printReadCharOrLeave(LinePrinter printer, NfaStateData data) {
        printer.println("try {");
        printer.println("    curChar = input_stream.readChar();");
        printer.println("} catch (java.io.IOException e) {");
        printer.indent();
        printer.println(data.isMixedState() ? "break;" : "return curPos;");
        printer.outdent();
        printer.println("}");
    }

    protected void printDebugStartingNfa(LinePrinter printer) {
        printer.println("debugStream.println(\"   Starting NFA to match one of : \" + "
                + "jjKindsForStateVector(curLexState, jjstateSet, 0, 1));");
    }

    protected void printDebugPossibleLongerMatches(LinePrinter printer) {
        printer.println("debugStream.println(\"   Possible kinds of longer matches : \" + "
                + "jjKindsForStateVector(curLexState, jjstateSet, startsAt, i));");
    }

    /** Emits {@code jjMoveNfa}, the interpreter loop of the generated NFA. */
    protected void dumpMoveNfa(LinePrinter printer, NfaStateData data) {
        boolean debug = data.global.options().getDebugTokenManager();
        String noKind = "0x" + Integer.toHexString(Integer.MAX_VALUE);

        printer.println();
        printMoveNfaSignature(printer, data);
        printer.indent();

        if (data.generatedStates() == 0) {
            printer.println("return curPos;");
            printer.outdent();
            printer.println("}");
            return;
        }

        if (data.isMixedState()) {
            printMoveNfaMixedPrologue(printer);
        }

        printer.println("int startsAt = 0;");
        printer.println("jjnewStateCnt = " + data.generatedStates() + ";");
        printer.println("int i = 1;");
        printer.println("jjstateSet[0] = startState;");

        if (debug) {
            printDebugStartingNfa(printer);
            printDebugCurrentCharacter(printer, data);
        }

        printer.println("int kind = " + noKind + ";");
        printForEver(printer);
        printer.indent();
        printer.println("if (++jjround == " + noKind + ")");
        printer.println("    ReInitRounds();");

        printer.println("if (curChar < 64) {");
        printer.indent();
        DumpAsciiMoves(printer, data, 0);
        printer.outdent();

        printer.println("} else if (curChar < 128) {");
        printer.indent();
        DumpAsciiMoves(printer, data, 1);
        printer.outdent();

        printer.println("} else {");
        printer.indent();
        DumpCharAndRangeMoves(printer, data);
        printer.outdent();
        printer.println("}");

        printer.println("if (kind != " + noKind + ") {");
        printer.println("    jjmatchedKind = kind;");
        printer.println("    jjmatchedPos = curPos;");
        printer.println("    kind = " + noKind + ";");
        printer.println("}");
        printer.println("curPos++;");

        if (debug) {
            printDebugCurrentlyMatched(printer);
        }

        printSwapStateSets(printer, data);

        if (debug) {
            printDebugPossibleLongerMatches(printer);
        }

        printReadCharOrLeave(printer, data);

        if (debug) {
            printDebugCurrentCharacter(printer, data);
        }
        printer.outdent();
        printer.println("}");

        if (data.isMixedState()) {
            printMoveNfaMixedEpilogue(printer);
        }

        printer.outdent();
        printer.println("}");
    }

    protected final int stateNameForComposite(NfaStateData data, String stateSetString) {
        return data.stateNameForComposite.get(stateSetString);
    }

    private void prepareNfaStates(NfaStateData data) {
        int i;
        int[] kindsForStates = null;

        if (data.global.getKinds() == null) {
            data.global.init();
        }

        re_arrange(data);

        for (i = 0; i < data.getAllStateCount(); i++) {
            var temp = data.getAllState(i);
            if ((temp.lexState != data.getStateIndex()) || !temp.HasTransitions() || temp.dummy || (
                    temp.stateName == -1)) {
                continue;
            }

            if (kindsForStates == null) {
                kindsForStates = new int[data.generatedStates()];
                data.global.getStatesForState()[data.getStateIndex()] =
                        new int[Math.max(data.generatedStates(), data.dummyStateIndex + 1)][];
            }

            kindsForStates[temp.stateName] = temp.lookingFor;
            data.global.getStatesForState()[data.getStateIndex()][temp.stateName] = temp.compositeStates;
        }

        for (var s : data.stateNameForComposite.keySet()) {
            int state = data.stateNameForComposite.get(s);
            if (state >= data.generatedStates()) {
                data.global.getStatesForState()[data.getStateIndex()][state] = data.getNextStates(s);
            }
        }

        if (!data.stateSetsToFix.isEmpty()) {
            FixStateSets(data);
        }

        data.global.setKinds(data.getStateIndex(), kindsForStates);
    }

    protected final Vector<List<NfaState>> PartitionStatesSetForAscii(NfaStateData data, int[] states, int byteNum) {
        var cardinalities = new int[states.length];
        var original = new Vector<NfaState>();
        var partition = new Vector<List<NfaState>>();
        NfaState tmp;

        original.setSize(states.length);
        int cnt = 0;
        for (int i = 0; i < states.length; i++) {
            tmp = data.getAllState(states[i]);

            if (tmp.asciiMoves[byteNum] != 0L) {
                int j;
                int p = LexerGenerator.NumberOfBitsSet(tmp.asciiMoves[byteNum]);

                for (j = 0; j < i; j++) {
                    if (cardinalities[j] <= p) {
                        break;
                    }
                }

                for (int k = i; k > j; k--) {
                    cardinalities[k] = cardinalities[k - 1];
                }

                cardinalities[j] = p;
                original.insertElementAt(tmp, j);
                cnt++;
            }
        }

        original.setSize(cnt);

        while (!original.isEmpty()) {
            tmp = original.getFirst();
            original.removeElement(tmp);

            long bitVec = tmp.asciiMoves[byteNum];
            List<NfaState> subSet = new ArrayList<>();
            subSet.add(tmp);

            for (int j = 0; j < original.size(); j++) {
                NfaState tmp1 = original.get(j);

                if ((tmp1.asciiMoves[byteNum] & bitVec) == 0L) {
                    bitVec |= tmp1.asciiMoves[byteNum];
                    subSet.add(tmp1);
                    original.removeElementAt(j--);
                }
            }

            partition.add(subSet);
        }

        return partition;
    }

    private static int NumberOfBitsSet(long l) {
        int ret = 0;
        for (int i = 0; i < 63; i++) {
            if (((l >> i) & 1L) != 0L) {
                ret++;
            }
        }
        return ret;
    }

    protected final int InitStateName(NfaStateData data) {
        if (data.getInitialState().usefulEpsilonMoves == 0) {
            return -1;
        }
        String s = data.getInitialState().GetEpsilonMovesString();
        return stateNameForComposite(data, s);
    }


    protected final int getCompositeStateSet(NfaStateData data, String stateSetString) {
        Integer stateNameToReturn = data.stateNameForComposite.get(stateSetString);

        if (stateNameToReturn != null) {
            return stateNameToReturn;
        }

        int[] nameSet = data.getNextStates(stateSetString);

        if (nameSet.length == 1) {
            return nameSet[0];
        }

        int toRet = 0;
        while ((toRet < nameSet.length) && ((data.getIndexedState(nameSet[toRet]).inNextOf > 1))) {
            toRet++;
        }

        for (var s : data.compositeStateTable.keySet()) {
            if (!s.equals(stateSetString) && NfaState.Intersect(data, stateSetString, s)) {
                int[] other = data.compositeStateTable.get(s);
                while ((toRet < nameSet.length) && (
                        ((data.getIndexedState(nameSet[toRet]).inNextOf > 1))
                                || (NfaState.ElemOccurs(nameSet[toRet], other) >= 0))) {
                    toRet++;
                }
            }
        }
        return nameSet[toRet];
    }

    private int AddCompositeStateSet(NfaStateData data, String stateSetString) {
        Integer stateNameToReturn;

        if ((stateNameToReturn = data.stateNameForComposite.get(stateSetString)) != null) {
            return stateNameToReturn;
        }

        int toRet = 0;
        int[] nameSet = data.getNextStates(stateSetString);

        if (nameSet == null) {
            throw new IllegalStateException(
                    "No next states registered for the state set: " + stateSetString);
        }

        if (nameSet.length == 1) {
            stateNameToReturn = nameSet[0];
            data.stateNameForComposite.put(stateSetString, stateNameToReturn);
            return nameSet[0];
        }

        for (int element : nameSet) {
            if (element == -1) {
                continue;
            }

            NfaState st = data.getIndexedState(element);
            st.isComposite = true;
            st.compositeStates = nameSet;
        }

        while ((toRet < nameSet.length) && ((data.getIndexedState(nameSet[toRet]).inNextOf > 1))) {
            toRet++;
        }

        for (String s : data.compositeStateTable.keySet()) {
            if (!s.equals(stateSetString) && NfaState.Intersect(data, stateSetString, s)) {
                int[] other = data.compositeStateTable.get(s);

                while ((toRet < nameSet.length) && (
                        ((data.getIndexedState(nameSet[toRet]).inNextOf > 1))
                                || (NfaState.ElemOccurs(nameSet[toRet], other) >= 0))) {
                    toRet++;
                }
            }
        }

        int tmp;

        if (toRet >= nameSet.length) {
            if (data.dummyStateIndex == -1) {
                tmp = data.dummyStateIndex = data.generatedStates();
            } else {
                tmp = ++data.dummyStateIndex;
            }
        } else {
            tmp = nameSet[toRet];
        }

        stateNameToReturn = tmp;
        data.stateNameForComposite.put(stateSetString, stateNameToReturn);
        data.compositeStateTable.put(stateSetString, nameSet);
        return tmp;
    }

    protected final void printActionToken(LinePrinter printer, Action action) {
        for (Token token : action.getActionTokens()) {
            printToken(token, printer);
        }
    }

    protected final String GetLabel(LexerData data, int kind) {
        RExpression re = data.getRegExp(kind);
        if (re instanceof RStringLiteral) {
            return " \"" + Encoding.escape(((RStringLiteral) re).getImage()) + "\"";
        } else if (!re.getLabel().isEmpty()) {
            return " <" + re.getLabel() + ">";
        } else {
            return " <token of kind " + kind + ">";
        }
    }

    /** A case that opens its own body right away. */
    protected void print_case(LinePrinter printer, String case_value) {
        printer.println("case " + case_value + ": {");
    }

    private boolean print_no_break(LinePrinter printer, NfaStateData data, NfaState state, int byteNum, boolean[] dumped) {
        if (state.inNextOf != 1) {
            throw new Error("HiveVM Bug");
        }

        dumped[state.stateName] = true;

        if (byteNum >= 0) {
            if (state.asciiMoves[byteNum] != 0L) {
                print_case(printer, "" + state.stateName);
                DumpAsciiMoveForCompositeState(printer, data, state, byteNum, false);
                printer.println("}");
                return false;
            }
        } else if (state.nonAsciiMethod != -1) {
            print_case(printer, "" + state.stateName);
            DumpNonAsciiMoveForCompositeState(printer, data, state);
            printer.println("}");
            return false;
        }
        return true;
    }

    private void DumpCompositeStatesNonAsciiMoves(LinePrinter printer, NfaStateData data,
                                                  String key, boolean[] dumped) {
        int[] nameSet = data.getNextStates(key);
        if ((nameSet.length == 1) || dumped[stateNameForComposite(data, key)]) {
            return;
        }

        NfaState toBePrinted = null;
        int neededStates = 0;
        NfaState tmp;
        NfaState stateForCase = null;
        boolean stateBlock = (data.stateBlockTable.get(key) != null);

        for (int j : nameSet) {
            tmp = data.getAllState(j);
            if (tmp.nonAsciiMethod != -1) {
                if (neededStates++ == 1) {
                    break;
                } else {
                    toBePrinted = tmp;
                }
            } else {
                dumped[tmp.stateName] = true;
            }

            if (tmp.stateForCase != null) {
                if (stateForCase != null) {
                    throw new Error("HiveVM Bug");
                }
                stateForCase = tmp.stateForCase;
            }
        }

        var toPrint = false;
        if (stateForCase != null) {
            toPrint = print_no_break(printer, data, stateForCase, -1, dumped);
        }

        if (neededStates == 0) {
            if ((stateForCase != null) && !toPrint) {
                printer.println("    break;");
            }

            return;
        }

        if (neededStates == 1) {
            // "stateForCase" is one more label on the very same body. Opening a block for it turned
            // the labels that follow into orphaned cases -- the generated Java did not compile.
            var cases = new ArrayList<String>();
            if (toPrint) {
                printCaseLabel(printer, cases, stateForCase.stateName);
            }
            printCaseLabel(printer, cases, stateNameForComposite(data, key));
            if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1)) {
                printCaseLabel(printer, cases, toBePrinted.stateName);
            }

            dumped[toBePrinted.stateName] = true;
            DumpNonAsciiMove(printer, data, toBePrinted, dumped, cases);
            return;
        }

        var cases = new ArrayList<String>();
        if (toPrint) {
            printCaseLabel(printer, cases, stateForCase.stateName);
        }

        int keyState = stateNameForComposite(data, key);
        printCaseLabel(printer, cases, keyState);
        printCasesOpen(printer, cases);
        if (keyState < data.generatedStates()) {
            dumped[keyState] = true;
        }

        for (int j : nameSet) {
            tmp = data.getAllState(j);

            if (tmp.nonAsciiMethod != -1) {
                if (stateBlock) {
                    dumped[tmp.stateName] = true;
                }
                DumpNonAsciiMoveForCompositeState(printer, data, tmp);
            }
        }

        printBreak(printer, "    ");
        printCasesClose(printer);
    }

    private void DumpAsciiMove(LinePrinter printer, NfaStateData data, NfaState state, int byteNum,
                               boolean[] dumped, boolean use_state_name) {
        DumpAsciiMove(printer, data, state, byteNum, dumped, use_state_name, new ArrayList<>(), "");
    }

    /**
     * @param cases      labels the caller has already collected for this very body -- Java writes
     *                   each one out on the spot and lets them fall through, Rust has to join them
     *                   into a single match arm, so there may only ever be one list per body
     * @param openIndent the indentation of that joined arm
     */
    private void DumpAsciiMove(LinePrinter printer, NfaStateData data, NfaState state, int byteNum,
                               boolean[] dumped, boolean use_state_name, List<String> cases,
                               String openIndent) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;
        boolean onlyState = true;
        if (use_state_name) {
            printCaseLabel(printer, cases, state.stateName);
        }

        for (NfaState element : data.getAllStates()) {
            if ((state == element) || (element.stateName == -1)
                    || element.dummy || (state.stateName == element.stateName)
                    || (element.asciiMoves[byteNum] == 0L)) {
                continue;
            }

            if (onlyState && ((state.asciiMoves[byteNum] & element.asciiMoves[byteNum]) != 0L)) {
                onlyState = false;
            }

            if (!nextIntersects && NfaState.Intersect(data, element.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
            }

            if (!dumped[element.stateName] && !element.isComposite && (state.asciiMoves[byteNum]
                    == element.asciiMoves[byteNum])
                    && (state.kindToPrint == element.kindToPrint)
                    && ((state.next.epsilonMovesString == element.next.epsilonMovesString)
                    || ((state.next.epsilonMovesString != null) && (element.next.epsilonMovesString != null)
                    && state.next.epsilonMovesString.equals(element.next.epsilonMovesString)))) {
                dumped[element.stateName] = true;
                printCaseLabel(printer, cases, element.stateName);
            }
        }

        printCasesOpen(printer, cases, openIndent);

        printer.indent();

        int oneBit = NfaState.OnlyOneBitSet(state.asciiMoves[byteNum]);
        if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL)
                && (((state.next == null) || (state.next.usefulEpsilonMoves == 0))
                && (state.kindToPrint != Integer.MAX_VALUE))) {
            String kindCheck = "";

            if (!onlyState) {
                kindCheck = " && kind > " + state.kindToPrint;
            }

            String condition = (oneBit != -1)
                    ? charEquals((64 * byteNum) + oneBit)
                    : bitIsSet(state.asciiMoves[byteNum]);

            printIfNoBlock(printer, condition + kindCheck);
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            printEndIf(printer);
            printBreak(printer, "");

            printer.outdent();
            if (!cases.isEmpty()) {
                printCasesClose(printer);
            }
            return;
        }

        boolean hasIf = false;
        if (state.kindToPrint != Integer.MAX_VALUE) {
            if (oneBit != -1) {
                printIf(printer, charNotEquals((64 * byteNum) + oneBit));
                printer.indent();
                printer.println("break;");
                printer.outdent();
                printer.println("}");
            } else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
                printIf(printer, bitIsClear(state.asciiMoves[byteNum]));
                printer.indent();
                printer.println("break;");
                printer.outdent();
                printer.println("}");
            }

            if (onlyState) {
                printer.println("kind = " + state.kindToPrint + ";");
            } else {
                printIf(printer, kindIsWeakerThan(state.kindToPrint));
                printer.indent();
                printer.println("kind = " + state.kindToPrint + ";");
                printer.outdent();
                printer.println("}");
            }
        } else if (oneBit != -1) {
            printIfNoBlock(printer, charEquals((64 * byteNum) + oneBit));
            hasIf = true;
            printer.indent();
        } else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
            printIfNoBlock(printer, bitIsSet(state.asciiMoves[byteNum]));
            hasIf = true;
            printer.indent();
        }

        printNextStates(printer, data, state, nextIntersects);

        if (hasIf) {
            printer.outdent();
        }

        printBreak(printer, "");

        if (hasIf) {
            printEndIf(printer);
        }

        printer.outdent();
        // Close exactly what printCasesOpen opened -- it is driven by the labels, not by
        // use_state_name. Rust lost the closing brace of every arm whose only labels came
        // from the states merged into it.
        if (!cases.isEmpty()) {
            printCasesClose(printer);
        }
    }


    private void DumpAsciiMoveForCompositeState(LinePrinter printer, NfaStateData data,
                                                NfaState state, int byteNum, boolean elseNeeded) {
        boolean nextIntersects = state.selfLoop();

        for (NfaState temp1 : data.getAllStates()) {
            if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName
                    == temp1.stateName)
                    || (temp1.asciiMoves[byteNum] == 0L)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
                break;
            }
        }

        // System.out.println(stateName + " \'s nextIntersects : " + nextIntersects);
        boolean hasIf = false;
        if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
            int oneBit = NfaState.OnlyOneBitSet(state.asciiMoves[byteNum]);

            var cond = (elseNeeded ? "else if " : "if ");
            printIfNoBlock(printer, cond, (oneBit != -1)
                    ? charEquals((64 * byteNum) + oneBit)
                    : bitIsSet(state.asciiMoves[byteNum]));
            hasIf = true;
        }
        printer.indent();

        if (state.kindToPrint != Integer.MAX_VALUE) {
            if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
                printer.println("{");
            }

            printIf(printer, kindIsWeakerThan(state.kindToPrint));
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            printer.println("}");
        }

        printNextStates(printer, data, state, nextIntersects);

        printer.outdent();
        if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL) && (state.kindToPrint != Integer.MAX_VALUE)) {
            printer.println("}");
        }

        if (hasIf) {
            printEndIf(printer);
        }
    }

    private void DumpNonAsciiMoveForCompositeState(LinePrinter printer, NfaStateData data,
                                                   NfaState state) {
        boolean nextIntersects = state.selfLoop();
        for (NfaState temp1 : data.getAllStates()) {
            if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName
                    == temp1.stateName)
                    || (temp1.nonAsciiMethod == -1)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
                break;
            }
        }

        printer.println("if (" + canMove(state) + ")");

        if (state.kindToPrint != Integer.MAX_VALUE) {
            printer.println("{");
            printer.indent();
            printIf(printer, kindIsWeakerThan(state.kindToPrint));
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            printer.println("}");
        }

        printNextStates(printer, data, state, nextIntersects);

        if (state.kindToPrint != Integer.MAX_VALUE) {
            printer.outdent();
            printer.println("}");
        }
    }

    private void DumpNonAsciiMove(LinePrinter printer, NfaStateData data, NfaState state,
                                  boolean[] dumped) {
        DumpNonAsciiMove(printer, data, state, dumped, new ArrayList<>());
    }

    /** @param cases see {@link #DumpAsciiMove} -- one list per body, never two. */
    private void DumpNonAsciiMove(LinePrinter printer, NfaStateData data, NfaState state,
                                  boolean[] dumped, List<String> cases) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;

        for (NfaState element : data.getAllStates()) {
            if ((state == element) || (element.stateName == -1) || element.dummy || (state.stateName
                    == element.stateName)
                    || (element.nonAsciiMethod == -1)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, element.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
            }

            if (!dumped[element.stateName] && !element.isComposite && (state.nonAsciiMethod == element.nonAsciiMethod)
                    && (state.kindToPrint == element.kindToPrint)
                    && ((state.next.epsilonMovesString == element.next.epsilonMovesString)
                    || ((state.next.epsilonMovesString != null) && (element.next.epsilonMovesString
                    != null)
                    && state.next.epsilonMovesString.equals(element.next.epsilonMovesString)))) {
                dumped[element.stateName] = true;
                printCaseLabel(printer, cases, element.stateName);
            }
        }

        printCasesOpen(printer, cases);

        if ((state.next == null) || (state.next.usefulEpsilonMoves <= 0)) {
            String kindCheck = " && kind > " + state.kindToPrint;

            printIfNoBlock(printer, canMove(state) + kindCheck);
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            printEndIf(printer);
            printBreak(printer, "");

            // This branch used to return without closing what printCasesOpen opened. Java and C++
            // never noticed -- their case labels carry no braces -- but every Rust match arm that
            // took it was left hanging open.
            if (!cases.isEmpty()) {
                printCasesClose(printer);
            }
            return;
        }

        if (state.kindToPrint != Integer.MAX_VALUE) {
            printIfNoBlock(printer, "!" + canMove(state));
            printer.indent();
            printer.println("break;");
            printer.outdent();
            printEndIf(printer);

            printIfNoBlock(printer, kindIsWeakerThan(state.kindToPrint));
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            printEndIf(printer);
        } else {
            printIfNoBlock(printer, canMove(state));
        }
        printer.indent();

        printNextStates(printer, data, state, nextIntersects);


        printBreak(printer, "");

        printer.outdent();

        if (state.kindToPrint == Integer.MAX_VALUE) {
            printEndIf(printer);
        }

        if (!cases.isEmpty()) {
            printCasesClose(printer);
        }
    }

    private void DumpCompositeStatesAsciiMoves(LinePrinter printer, NfaStateData data, String key, int byteNum, boolean[] dumped) {
        int i;
        int[] nameSet = data.getNextStates(key);

        if ((nameSet.length == 1) || dumped[stateNameForComposite(data, key)]) {
            return;
        }

        NfaState toBePrinted = null;
        int neededStates = 0;
        NfaState tmp;
        NfaState stateForCase = null;
        boolean stateBlock = (data.stateBlockTable.get(key) != null);

        for (i = 0; i < nameSet.length; i++) {
            tmp = data.getAllState(nameSet[i]);

            if (tmp.asciiMoves[byteNum] != 0L) {
                if (neededStates++ == 1) {
                    break;
                } else {
                    toBePrinted = tmp;
                }
            } else {
                dumped[tmp.stateName] = true;
            }

            if (tmp.stateForCase != null) {
                if (stateForCase != null) {
                    throw new IllegalStateException(
                            "Two NFA states of the same composite state claim stateForCase");
                }

                stateForCase = tmp.stateForCase;
            }
        }

        var toPrint = false;
        if (stateForCase != null) {
            toPrint = print_no_break(printer, data, stateForCase, byteNum, dumped);
        }

        if (neededStates == 0) {
            if ((stateForCase != null) && !toPrint) {
                printer.println("                  break;");
            }
            return;
        }

        if (neededStates == 1) {
            var cases = new ArrayList<String>();
            if (toPrint) {
                printCaseLabel(printer, cases, "               ", stateForCase.stateName);
            }
            printCaseLabel(printer, cases, "               ", stateNameForComposite(data, key));
            if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1)) {
                printCaseLabel(printer, cases, "               ", toBePrinted.stateName);
            }

            dumped[toBePrinted.stateName] = true;
            DumpAsciiMove(printer, data, toBePrinted, byteNum, dumped, false, cases,
                    "               ");
            return;
        }

        List<List<NfaState>> partition = PartitionStatesSetForAscii(data, nameSet, byteNum);

        var cases = new ArrayList<String>();
        if (toPrint) {
            printCaseLabel(printer, cases, stateForCase.stateName);
        }

        int keyState = stateNameForComposite(data, key);
        printCaseLabel(printer, cases, keyState);
        printCasesOpen(printer, cases);
        printer.indent();
        if (keyState < data.generatedStates()) {
            dumped[keyState] = true;
        }

        for (i = 0; i < partition.size(); i++) {
            List<NfaState> subSet = partition.get(i);

            for (int j = 0; j < subSet.size(); j++) {
                tmp = subSet.get(j);

                if (stateBlock) {
                    dumped[tmp.stateName] = true;
                }
                DumpAsciiMoveForCompositeState(printer, data, tmp, byteNum, j != 0);
            }
        }

        printBreak(printer, "");
        printer.outdent();
        printCasesClose(printer);
    }

    /** Declares the bit masks the NFA state tests the current character against. */
    protected void printCharBits(LinePrinter printer, int byteNum) {
        if (byteNum == 0) {
            printer.println("long l = 1L << curChar;");
        } else if (byteNum == 1) {
            printer.println("long l = 1L << (curChar & 077);");
        } else {
            printer.println("int hiByte = (curChar >> 8);");
            printer.println("int i1 = hiByte >> 6;");
            printer.println("long l1 = 1L << (hiByte & 077);");
            printer.println("int i2 = (curChar & 0xff) >> 6;");
            printer.println("long l2 = 1L << (curChar & 077);");
        }
    }

    /** Opens the loop that advances every state currently in the state set. */
    protected void printMatchLoopOpen(LinePrinter printer) {
        printer.println("do {");
    }

    /** Dispatches on the next state to advance. */
    protected void printSwitchOnStateSet(LinePrinter printer) {
        printer.println("switch (jjstateSet[--i]) {");
    }

    protected final void DumpHeadForCase(LinePrinter printer, int byteNum) {
        printCharBits(printer, byteNum);
        printMatchLoopOpen(printer);
        printer.indent();
        printSwitchOnStateSet(printer);
        printer.indent();
    }

    protected void DumpAsciiMoves(LinePrinter printer, NfaStateData data, int byteNum) {
        boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];

        DumpHeadForCase(printer, byteNum);

        for (String s : data.compositeStateTable.keySet()) {
            DumpCompositeStatesAsciiMoves(printer, data, s, byteNum, dumped);
        }

        for (var element : data.getAllStates()) {
            if (dumped[element.stateName] || (element.lexState != data.getStateIndex())
                    || !element.HasTransitions() || element.dummy
                    || (element.stateName == -1)) {
                continue;
            }

            var toPrint = false;
            if (element.stateForCase != null) {
                if ((element.inNextOf == 1) || dumped[element.stateForCase.stateName]) {
                    continue;
                }

                toPrint = print_no_break(printer, data, element.stateForCase, byteNum, dumped);

                if (element.asciiMoves[byteNum] == 0L) {
                    if (!toPrint) {
                        printer.println("                  break;");
                    }
                    continue;
                }
            }

            if (element.asciiMoves[byteNum] == 0L) {
                continue;
            }

            if (toPrint) {
                print_case(printer, "" + element.stateForCase);
                printer.indent();
            }

            dumped[element.stateName] = true;
            DumpAsciiMove(printer, data, element, byteNum, dumped, true);
        }

        printDefaultAndEndLoop(printer, (byteNum != 0) && (byteNum != 1));
    }

    protected void DumpCharAndRangeMoves(LinePrinter printer, NfaStateData data) {
        boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];

        DumpHeadForCase(printer, -1);

        for (String s : data.compositeStateTable.keySet()) {
            DumpCompositeStatesNonAsciiMoves(printer, data, s, dumped);
        }

        for (var i = 0; i < data.getAllStateCount(); i++) {
            var temp = data.getAllState(i);
            if ((temp.stateName == -1) || dumped[temp.stateName]
                    || (temp.lexState != data.getStateIndex())
                    || !temp.HasTransitions() || temp.dummy) {
                continue;
            }

            var toPrint = false;
            if (temp.stateForCase != null) {
                if ((temp.inNextOf == 1) || dumped[temp.stateForCase.stateName])
                    continue;

                toPrint = print_no_break(printer, data, temp.stateForCase, -1, dumped);

                if (temp.nonAsciiMethod == -1) {
                    if (!toPrint)
                        printer.println("break;");
                    continue;
                }
            }

            if (temp.nonAsciiMethod == -1)
                continue;

            var cases = new ArrayList<String>();
            if (toPrint) {
                printCaseLabel(printer, cases, temp.stateForCase.stateName);
            }

            dumped[temp.stateName] = true;
            printCaseLabel(printer, cases, temp.stateName);
            printer.indent();
            DumpNonAsciiMove(printer, data, temp, dumped, cases);
            printer.outdent();
        }

        printDefaultAndEndLoop(printer, true);
    }


    /** What an absent table looks like. */
    protected String noStateSet() {
        return "null";
    }

    /** How a table of state sets is wrapped. */
    protected String stateSet(CharSequence body) {
        return "{" + body + "}";
    }

    /** How one row of such a table opens. Rust writes a slice where the others write a brace. */
    protected String rowOpen() {
        return "{";
    }

    protected String rowClose() {
        return "}";
    }

protected final String getStatesForState(LexerData data) {
        // A grammar made only of string literals has no NFA, hence no state table. The assert that
        // used to sit here claimed the opposite and blew up as soon as assertions were on.
        if (data.getStatesForState() == null) {
            return noStateSet();
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.getStatesForState()[i] == null) {
                builder.append(rowOpen()).append(rowClose()).append(",");
                continue;
            }
            builder.append(rowOpen());
            for (int j = 0; j < data.getStatesForState()[i].length; j++) {
                int[] stateSet = data.getStatesForState()[i][j];
                if (stateSet == null) {
                    builder.append(rowOpen()).append(" ").append(j).append(" ").append(rowClose())
                            .append(",");
                    continue;
                }
                builder.append(rowOpen()).append(" ");
                for (int element : stateSet) {
                    builder.append(element).append(",");
                }
                builder.append(rowClose()).append(",");
            }
            builder.append(rowClose()).append(",");
        }
        return stateSet(builder);
    }

protected final String getKindForState(LexerData data) {
        if (data.getKinds() == null) {
            return noStateSet();
        }

        StringBuilder builder = new StringBuilder();
        boolean moreThanOne = false;
        for (int[] kind : data.getKinds()) {
            if (moreThanOne) {
                builder.append(",");
            }
            moreThanOne = true;
            if (kind == null) {
                builder.append(rowOpen()).append(rowClose());
            } else {
                builder.append(rowOpen()).append(" ");
                for (int element : kind) {
                    builder.append(element);
                    builder.append(",");
                }
                builder.append(rowClose());
            }
        }
        return stateSet(builder);
    }

    protected String toHexString(long value) {
        return "0x" + Long.toHexString(value) + "L";
    }

    // Assumes l != 0L
    protected static char MaxChar(long l) {
        for (int i = 64; i-- > 0; ) {
            if ((l & (1L << i)) != 0L) {
                return (char) i;
            }
        }
        return 0xffff;
    }

    private String getLohiBytes(LexerData data, int i) {
        return String.join(", ",
                toHexString(data.getLohiByte(i, 0)),
                toHexString(data.getLohiByte(i, 1)),
                toHexString(data.getLohiByte(i, 2)),
                toHexString(data.getLohiByte(i, 3)));
    }
}
