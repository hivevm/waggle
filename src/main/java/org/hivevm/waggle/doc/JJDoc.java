// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjdoc/JJDoc.java, org/javacc/jjdoc/Generator.java,
// org/javacc/jjdoc/BNFGenerator.java, org/javacc/jjdoc/TextGenerator.java

package org.hivevm.waggle.doc;

import org.hivevm.waggle.api.Encoding;
import org.hivevm.waggle.api.GenerationException;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.grammar.GrammarData;
import org.hivevm.waggle.model.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * Writes the BNF productions of a grammar as BNF, one per paragraph. Token productions are not
 * written, and neither are actions and lookaheads.
 */
final class JJDoc {

    private final PrintWriter out;

    private JJDoc(PrintWriter out) {
        this.out = out;
    }

    /**
     * Writes the BNF of {@code grammar}, read from {@code inputFile}, and returns where it went: the
     * OUTPUT_FILE option, else the input file with the extension {@code .bnf}, else standard output.
     */
    static String write(GrammarData grammar, String inputFile) {
        String outputFile = grammar.options().stringValue(Waggle.OUTPUT_FILE);
        if (outputFile.isEmpty()) {
            outputFile = JJDoc.outputFileFor(inputFile);
        }

        PrintWriter out = null;
        if (outputFile != null) {
            try {
                out = new PrintWriter(new FileWriter(outputFile, StandardCharsets.UTF_8));
            } catch (IOException e) {
                grammar.diagnostics().warning("JJDoc: can't open output stream on file "
                        + outputFile + ".  Using standard output.");
            }
        }
        boolean standardOutput = (out == null);
        if (standardOutput) {
            outputFile = "standard output";
            out = new PrintWriter(new OutputStreamWriter(System.out));
        }

        var doc = new JJDoc(out);
        for (NormalProduction np : grammar.getNormalProductions()) {
            if (np instanceof BNFProduction) {
                doc.production(np);
            }
        }

        // Standard output is not ours to close: the command line still reports after this.
        if (standardOutput) {
            out.flush();
        } else {
            out.close();
        }
        return outputFile;
    }

    /** The input file with the extension {@code .bnf}, or {@code null} for standard input. */
    private static String outputFileFor(String inputFile) {
        if (inputFile.equals("standard input")) {
            return null;
        }
        String ext = ".bnf";
        int i = inputFile.lastIndexOf('.');
        if ((i == -1) || inputFile.substring(i).equals(ext)) {
            return inputFile + ext;
        }
        return inputFile.substring(0, i) + ext;
    }

    private void production(NormalProduction np) {
        this.out.print("\n");
        this.out.print(np.getLhs() + " ::= ");
        expansion(np.getExpansion());
        this.out.print("\n");
    }

    private void expansion(Expansion exp) {
        switch (exp) {
            case Action action -> {
            }
            case Choice choice -> choice(choice);
            case Lookahead lookahead -> {
            }
            case NonTerminal nonTerminal -> this.out.print(nonTerminal.getName());
            case OneOrMore oneOrMore -> unit(oneOrMore.getExpansion(), " )+");
            case RExpression re -> regularExpression(re);
            case Sequence sequence -> sequence(sequence);
            case ZeroOrMore zeroOrMore -> unit(zeroOrMore.getExpansion(), " )*");
            case ZeroOrOne zeroOrOne -> unit(zeroOrOne.getExpansion(), " )?");
            case null, default -> throw new GenerationException("Unknown expansion type: " + exp);
        }
    }

    private void choice(Choice c) {
        for (Iterator<Expansion> it = c.getChoices().iterator(); it.hasNext(); ) {
            expansion(it.next());
            if (it.hasNext()) {
                this.out.print(" | ");
            }
        }
    }

    private void unit(Expansion e, String close) {
        this.out.print("( ");
        expansion(e);
        this.out.print(close);
    }

    /**
     * A named token and a character list are not written in a BNF production, as in JavaCC's BNF
     * generator.
     */
    private void regularExpression(RExpression r) {
        if (!(r instanceof RJustName) && !(r instanceof RCharacterList)) {
            this.out.print(JJDoc.emitRE(r));
        }
    }

    private void sequence(Sequence s) {
        boolean firstUnit = true;
        for (Expansion e : s.getUnits()) {
            if ((e instanceof Lookahead) || (e instanceof Action)) {
                continue;
            }
            if (!firstUnit) {
                this.out.print(" ");
            }
            boolean needParens = (e instanceof Choice) || (e instanceof Sequence);
            if (needParens) {
                this.out.print("( ");
            }
            expansion(e);
            if (needParens) {
                this.out.print(" )");
            }
            firstUnit = false;
        }
    }

    private static String emitRE(RExpression re) {
        StringBuilder returnString = new StringBuilder();
        boolean hasLabel = !re.getLabel().isEmpty();
        boolean justName = re instanceof RJustName;
        boolean eof = re instanceof REndOfFile;
        boolean isString = re instanceof RStringLiteral;
        boolean toplevelRE = (re.getTokenKind() != null);
        boolean needBrackets = justName || eof || hasLabel || (!isString && toplevelRE);
        if (needBrackets) {
            returnString.append("<");
            if (!justName) {
                if (re.isPrivateExp()) {
                    returnString.append("#");
                }
                if (hasLabel) {
                    returnString.append(re.getLabel());
                    returnString.append(": ");
                }
            }
        }
        switch (re) {
            case RCharacterList cl -> {
                if (cl.isNegated_list()) {
                    returnString.append("~");
                }
                returnString.append("[");
                for (Iterator<Object> it = cl.getDescriptors().iterator(); it.hasNext(); ) {
                    Object o = it.next();
                    if (o instanceof SingleCharacter c) {
                        returnString.append("\"");
                        returnString.append(Encoding.escape(String.valueOf(c.getChar())));
                        returnString.append("\"");
                    } else if (o instanceof CharacterRange range) {
                        returnString.append("\"");
                        returnString.append(Encoding.escape(String.valueOf(range.getLeft())));
                        returnString.append("\"-\"");
                        returnString.append(Encoding.escape(String.valueOf(range.getRight())));
                        returnString.append("\"");
                    } else {
                        throw new GenerationException(
                                "Unknown character list element type: " + o);
                    }
                    if (it.hasNext())
                        returnString.append(",");
                }
                returnString.append("]");
            }
            case RChoice c -> {
                for (Iterator<RExpression> it = c.getChoices().iterator(); it.hasNext(); ) {
                    RExpression sub = it.next();
                    returnString.append(JJDoc.emitRE(sub));
                    if (it.hasNext())
                        returnString.append(" | ");
                }
            }
            case REndOfFile rEndOfFile -> returnString.append("EOF");
            case RJustName jn -> returnString.append(jn.getLabel());
            case ROneOrMore om -> returnString.append(JJDoc.group(om.getRegexpr(), "+"));
            case RSequence s -> {
                for (Iterator<RExpression> it = s.getUnits().iterator(); it.hasNext(); ) {
                    RExpression sub = it.next();
                    boolean needParens = sub instanceof RChoice;
                    if (needParens) {
                        returnString.append("(");
                    }
                    returnString.append(JJDoc.emitRE(sub));
                    if (needParens) {
                        returnString.append(")");
                    }
                    if (it.hasNext()) {
                        returnString.append(" ");
                    }
                }
            }
            case RStringLiteral sl -> returnString.append("\"").append(Encoding.escape(sl.getImage())).append("\"");
            case RZeroOrMore zm -> returnString.append(JJDoc.group(zm.getRegexpr(), "*"));
            case RZeroOrOne zo -> returnString.append(JJDoc.group(zo.getRegexpr(), "?"));
            case RRepetitionRange zo -> {
                returnString.append(JJDoc.group(zo.getRegexpr(), "{"));
                returnString.append(zo.getMin());
                if (zo.hasMax()) {
                    returnString.append(",");
                    returnString.append(zo.getMax());
                }
                returnString.append("}");
            }
            default -> throw new GenerationException("Unknown regular expression type: " + re);
        }
        if (needBrackets) {
            returnString.append(">");
        }
        return returnString.toString();
    }

    /** {@code re} in parentheses, followed by {@code suffix}. */
    private static String group(RExpression re, String suffix) {
        return "(" + JJDoc.emitRE(re) + ")" + suffix;
    }
}
