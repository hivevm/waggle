// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.gradle.internal.impldep.org.bouncycastle.oer.its.ieee1609dot2.basetypes.RectangularRegion;

import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.Lookahead;
import org.hivevm.cc.model.NonTerminal;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.RChoice;
import org.hivevm.cc.model.REndOfFile;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RJustName;
import org.hivevm.cc.model.ROneOrMore;
import org.hivevm.cc.model.RRepetitionRange;
import org.hivevm.cc.model.RSequence;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.model.RZeroOrMore;
import org.hivevm.cc.model.RZeroOrOne;
import org.hivevm.cc.model.RegularExpression;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.TokenProduction;
import org.hivevm.cc.model.ZeroOrMore;
import org.hivevm.cc.model.ZeroOrOne;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.ParseException;
import org.hivevm.cc.parser.RegExprSpec;

public class Semanticize {

    private final SemanticRequest request;
    private final SemanticContext context;

    private long    generationIndex;
    private int     laLimit;
    private boolean considerSemanticLA;


    private       ArrayList<MatchInfo>    sizeLimitedMatches;
    private final List<List<RegExprSpec>> removeList;
    private final List<Object>            itemList;

    private RExpression other;
    // The string in which the following methods store information.
    private String      loopString;

    /**
     * A mapping of ordinal values (represented as objects of type "Integer") to the corresponding
     * RegularExpression's.
     */
    private final Map<Integer, RExpression> rexps_of_tokens    = new HashMap<>();
    /**
     * This is a symbol table that contains all named tokens (those that are defined with a label).
     * The index to the table is the image of the label and the contents of the table are of type
     * "RegularExpression".
     */
    private final Map<String, RExpression>  named_tokens_table = new HashMap<>();


    /**
     * Constructs an instance of {@link Semanticize}.
     */
    private Semanticize(SemanticRequest request, SemanticContext context) {
        this.request = request;
        this.context = context;

        this.generationIndex = 1;
        this.laLimit = 0;

        this.considerSemanticLA = false;
        this.sizeLimitedMatches = null;

        this.removeList = new ArrayList<>();
        this.itemList = new ArrayList<>();
        this.other = null;
        this.loopString = null;
    }

    private SemanticContext getContext() {
        return this.context;
    }

    final long nextGenerationIndex() {
        return this.generationIndex++;
    }

    final int laLimit() {
        return this.laLimit;
    }

    final boolean considerSemanticLA() {
        return this.considerSemanticLA;
    }

    final void setLaLimit(int limit) {
        this.laLimit = limit;
    }

    final void setConsiderSemanticLA(boolean considerSemanticLA) {
        this.considerSemanticLA = considerSemanticLA;
    }

    final void initSizeLimitedMatches() {
        this.sizeLimitedMatches = new ArrayList<>();
    }

    final List<MatchInfo> getSizeLimitedMatches() {
        return this.sizeLimitedMatches;
    }

    final RExpression getRegularExpression(int index) {
        return this.rexps_of_tokens.get(index);
    }

    public static void semanticize(SemanticRequest request, Options options) throws ParseException {
        SemanticContext context = new SemanticContext(options);
        if (context.hasErrors())
            throw new ParseException();

        if ((context.getLookahead() > 1) && !context.isForceLaCheck() && context.isSanityCheck())
            context.onWarning("Lookahead adequacy checking not being performed since option LOOKAHEAD "
                    + "is more than 1.  Set option FORCE_LA_CHECK to true to force checking.");

        Semanticize semanticize = new Semanticize(request, context);

        /*
         * The following walks the entire parse tree to convert all LOOKAHEAD's that are not at choice
         * points (but at beginning of sequences) and converts them to trivial choices. This way, their
         * semantic lookahead specification can be evaluated during other lookahead evaluations.
         */
        for (NormalProduction bnfproduction : request.getNormalProductions()) {
            TreeWalker.walk(bnfproduction.getExpansion(), semanticize.new LookaheadFixer(), true);
        }

        /*
         * The following loop populates "production_table"
         */
        for (NormalProduction p : request.getNormalProductions()) {
            if (request.setProductionTable(p) != null)
                context.onSemanticError(p,
                        p.getLhs() + " occurs on the left hand side of more than one production.");
        }

        /*
         * The following walks the entire parse tree to make sure that all non-terminals on RHS's are
         * defined on the LHS.
         */
        for (NormalProduction bnfproduction : request.getNormalProductions()) {
            TreeWalker.walk((bnfproduction).getExpansion(), semanticize.new ProductionDefinedChecker(),
                    false);
        }

        /*
         * The following loop ensures that all target lexical states are defined. Also piggybacking on
         * this loop is the detection of <EOF> and <name> in token productions. After reporting an
         * error, these entries are removed. Also checked are definitions on inline private regular
         * expressions. This loop works slightly differently when is set to true. In this case, <name>
         * occurrences are OK, while regular expression specs generate a warning.
         */
        for (TokenProduction tp : request.getTokenProductions()) {
            List<RegExprSpec> respecs = tp.getRespecs();
            for (RegExprSpec res : respecs) {
                if ((res.nextState != null) && (request.getStateIndex(res.nextState) == null))
                    context.onSemanticError(res.nsTok,
                            "Lexical state \"" + res.nextState + "\" has not been defined.");
                if (res.rexp instanceof REndOfFile) {
                    // context.onSemanticError(res.rexp, "Badly placed <EOF>.");
                    if (tp.getLexStates() != null)
                        context.onSemanticError(res.rexp,
                                "EOF action/state change must be specified for all states, " + "i.e., <*>TOKEN:.");
                    if (tp.getKind() != TokenProduction.Kind.TOKEN)
                        context.onSemanticError(res.rexp,
                                "EOF action/state change can be specified only in a " + "TOKEN specification.");
                    if ((request.getNextStateForEof() != null) || (request.getActionForEof() != null))
                        context.onSemanticError(res.rexp,
                                "Duplicate action/state change specification for <EOF>.");
                    request.setActionForEof(res.act);
                    request.setNextStateForEof(res.nextState);
                    semanticize.prepareToRemove(respecs, res);
                }
                else if (tp.isExplicit() && (res.rexp instanceof RJustName)) {
                    context.onWarning(res.rexp,
                            "Ignoring free-standing regular expression reference.  "
                                    + "If you really want this, you must give it a different label as <NEWLABEL:<"
                                    + res.rexp.getLabel()
                                    + ">>.");
                    semanticize.prepareToRemove(respecs, res);
                }
                else if (!tp.isExplicit() && res.rexp.isPrivateExp())
                    context.onSemanticError(res.rexp,
                            "Private (#) regular expression cannot be defined within " + "grammar productions.");
            }
        }

        semanticize.removePreparedItems();

        /*
         * The following loop inserts all names of regular expressions into "named_tokens_table" and
         * "ordered_named_tokens". Duplications are flagged as errors.
         */
        for (TokenProduction tokenProduction : request.getTokenProductions()) {
            List<RegExprSpec> respecs = (tokenProduction).getRespecs();
            for (RegExprSpec respec : respecs) {
                if (!(respec.rexp instanceof RJustName) && !respec.rexp.getLabel().isEmpty()) {
                    String s = respec.rexp.getLabel();
                    Object obj = semanticize.named_tokens_table.put(s, respec.rexp);
                    if (obj != null)
                        context.onSemanticError(respec.rexp,
                                "Multiply defined lexical token name \"" + s + "\".");
                    else
                        request.addOrderedNamedToken(respec.rexp);
                    if (request.getStateIndex(s) != null)
                        context.onSemanticError(respec.rexp,
                                "Lexical token name \"" + s + "\" is the same as " + "that of a lexical state.");
                }
            }
        }

        /*
         * The following code merges multiple uses of the same string in the same lexical state and
         * produces error messages when there are multiple explicit occurrences (outside the BNF) of the
         * string in the same lexical state, or when within BNF occurrences of a string are duplicates
         * of those that occur as non-TOKEN's (SKIP, MORE, SPECIAL_TOKEN) or private regular
         * expressions. While doing this, this code also numbers all regular expressions (by setting
         * their ordinal values), and populates the table "names_of_tokens".
         */

        request.unsetTokenCount();
        for (TokenProduction tokenProduction : request.getTokenProductions()) {
            List<RegExprSpec> respecs = (tokenProduction).getRespecs();
            if ((tokenProduction).getLexStates() == null) {
                (tokenProduction).setLexStates(new String[request.getStateNames().size()]);
                int i = 0;
                for (String stateName : request.getStateNames()) {
                    (tokenProduction).setLexState(stateName, i++);
                }
            }
            Hashtable<String, Hashtable<String, RExpression>>[] table = new Hashtable[(tokenProduction).getLexStates().length];
            for (int i = 0; i < (tokenProduction).getLexStates().length; i++) {
                table[i] = request.getSimpleTokenTable((tokenProduction).getLexStates()[i]);
            }
            for (RegExprSpec respec : respecs) {
                if (respec.rexp instanceof RStringLiteral sl) {
                    // This loop performs the checks and actions with respect to each lexical state.
                    for (int i = 0; i < table.length; i++) {
                        // Get table of all case variants of "sl.image" into table2.
                        Hashtable<String, RExpression> table2 = table[i].get(sl.getImage().toUpperCase());
                        if (table2 == null) {
                            // There are no case variants of "sl.image" earlier than the current one.
                            // So go ahead and insert this item.
                            if (sl.getOrdinal() == 0)
                                sl.setOrdinal(request.addTokenCount());
                            table2 = new Hashtable<>();
                            table2.put(sl.getImage(), sl);
                            table[i].put(sl.getImage().toUpperCase(), table2);
                        }
                        else if (semanticize.hasIgnoreCase(table2, sl.getImage())) { // hasIgnoreCase
                            // sets
                            // "other"
                            // if it is found.
                            // Since IGNORE_CASE version exists, current one is useless and bad.
                            if (!sl.getTpContext().isExplicit()) {
                                // inline BNF string is used earlier with an IGNORE_CASE.
                                context.onSemanticError(sl,
                                        "String \"" + sl.getImage() + "\" can never be matched "
                                                + "due to presence of more general (IGNORE_CASE) regular expression "
                                                + "at line "
                                                + semanticize.other.getLine() + ", column " + semanticize.other.getColumn()
                                                + ".");
                            }
                            else
                                // give the standard error message.
                                context.onSemanticError(sl,
                                        "Duplicate definition of string token \"" + sl.getImage() + "\" "
                                                + "can never be matched.");
                        }
                        else if (sl.getTpContext().isIgnoreCase()) {
                            // This has to be explicit. A warning needs to be given with respect
                            // to all previous strings.
                            StringBuilder pos = new StringBuilder();
                            int count = 0;
                            for (Enumeration<RExpression> enum2 = table2.elements(); enum2.hasMoreElements(); ) {
                                RExpression rexp = (enum2.nextElement());
                                if (count != 0)
                                    pos.append(",");
                                pos.append(" line ").append(rexp.getLine());
                                count++;
                            }
                            if (count == 1)
                                context.onWarning(sl,
                                        "String with IGNORE_CASE is partially superseded by string at" + pos + ".");
                            else
                                context.onWarning(sl,
                                        "String with IGNORE_CASE is partially superseded by strings at" + pos + ".");
                            // This entry is legitimate. So insert it.
                            if (sl.getOrdinal() == 0)
                                sl.setOrdinal(request.addTokenCount());
                            table2.put(sl.getImage(), sl);
                            // The above "put" may override an existing entry (that is not IGNORE_CASE) and that's
                            // the desired behavior.
                        }
                        else {
                            // The rest of the cases do not involve IGNORE_CASE.
                            RExpression re = table2.get(sl.getImage());
                            if (re == null) {
                                if (sl.getOrdinal() == 0)
                                    sl.setOrdinal(request.addTokenCount());
                                table2.put(sl.getImage(), sl);
                            }
                            else if ((tokenProduction).isExplicit()) {
                                // This is an error even if the first occurrence was implicit.
                                if ((tokenProduction).getLexStates()[i].equals("DEFAULT")) {
                                    context.onSemanticError(sl,
                                            "Duplicate definition of string token \"" + sl.getImage() + "\".");
                                }
                                else {
                                    context.onSemanticError(sl,
                                            "Duplicate definition of string token \"" + sl.getImage()
                                                    + "\" in lexical state \"" + (tokenProduction).getLexStates()[i] + "\".");
                                }
                            }
                            else if (re.getTpContext().getKind() != TokenProduction.Kind.TOKEN) {
                                context.onSemanticError(sl,
                                        "String token \"" + sl.getImage() + "\" has been defined as a \""
                                                + re.getTpContext().getKind().name() + "\" token.");
                            }
                            else if (re.isPrivateExp()) {
                                context.onSemanticError(sl,
                                        "String token \"" + sl.getImage()
                                                + "\" has been defined as a private regular expression.");
                            }
                            else {
                                // This is now a legitimate reference to an existing RStringLiteral.
                                // So we assign it a number and take it out of "rexprlist".
                                // Therefore, if all is OK (no errors), then there will be only unequal
                                // string literals in each lexical state. Note that the only way
                                // this can be legal is if this is a string declared inline within the
                                // BNF. Hence, it belongs to only one lexical state - namely "DEFAULT".
                                sl.setOrdinal(re.getOrdinal());
                                semanticize.prepareToRemove(respecs, respec);
                            }
                        }
                    }
                }
                else if (!(respec.rexp instanceof RJustName))
                    respec.rexp.setOrdinal(request.addTokenCount());
                if (!(respec.rexp instanceof RJustName) && !respec.rexp.getLabel().isEmpty())
                    request.setNamesOfToken(respec.rexp);
                if (!(respec.rexp instanceof RJustName))
                    semanticize.rexps_of_tokens.put(respec.rexp.getOrdinal(), respec.rexp);
            }
        }

        semanticize.removePreparedItems();

        /*
         * The following code performs a tree walk on all regular expressions attaching links to
         * "RJustName"s. Error messages are given if undeclared names are used, or if "RJustNames" refer
         * to private regular expressions or to regular expressions of any kind other than TOKEN. In
         * addition, this loop also removes top level "RJustName"s from "rexprlist". This code is not
         * executed if Options.getUserTokenManager() is set to true. Instead the following block of code
         * is executed.
         */

        FixRJustNames frjn = semanticize.new FixRJustNames();
        for (TokenProduction tokenProduction : request.getTokenProductions()) {
            List<RegExprSpec> respecs = (tokenProduction).getRespecs();
            for (RegExprSpec respec : respecs) {
                frjn.root = respec.rexp;
                TreeWalker.walk(respec.rexp, frjn, false);
                if (respec.rexp instanceof RJustName)
                    semanticize.prepareToRemove(respecs, respec);
            }
        }

        semanticize.removePreparedItems();
        semanticize.removePreparedItems();

        if (context.hasErrors())
            throw new ParseException();

        // The following code sets the value of the "emptyPossible" field of NormalProduction
        // nodes. This field is initialized to false, and then the entire list of
        // productions is processed. This is repeated as long as at least one item
        // got updated from false to true in the pass.
        boolean emptyUpdate = true;
        while (emptyUpdate) {
            emptyUpdate = false;
            for (NormalProduction prod : request.getNormalProductions()) {
                if (Semanticize.emptyExpansionExists(prod.getExpansion()) && !prod.isEmptyPossible())
                    emptyUpdate = prod.setEmptyPossible(true);
            }
        }

        if (context.isSanityCheck() && !context.hasErrors()) {

            // The following code checks that all ZeroOrMore, ZeroOrOne, and OneOrMore nodes
            // do not contain expansions that can expand to the empty token list.
            for (NormalProduction bnfproduction : request.getNormalProductions()) {
                TreeWalker.walk(bnfproduction.getExpansion(), semanticize.new EmptyChecker(), false);
            }

            // The following code goes through the productions and adds pointers to other
            // productions that it can expand to without consuming any tokens. Once this is
            // done, a left-recursion check can be performed.
            for (NormalProduction prod : request.getNormalProductions()) {
                semanticize.addLeftMost(prod, prod.getExpansion());
            }

            // Now the following loop calls a recursive walk routine that searches for
            // actual left recursions. The way the algorithm is coded, once a node has
            // been determined to participate in a left recursive loop, it is not tried
            // in any other loop.
            for (NormalProduction prod : request.getNormalProductions()) {
                if (prod.getWalkStatus() == 0)
                    semanticize.prodWalk(prod);
            }

            for (TokenProduction tp : request.getTokenProductions()) {
                List<RegExprSpec> respecs = tp.getRespecs();
                for (RegExprSpec res : respecs) {
                    RExpression rexp = res.rexp;
                    if (rexp.getWalkStatus() == 0) {
                        rexp.setWalkStatus(-1);
                        if (semanticize.rexpWalk(rexp)) {
                            semanticize.loopString =
                                    "..." + rexp.getLabel() + "... --> " + semanticize.loopString;
                            context.onSemanticError(rexp,
                                    "Loop in regular expression detected: \"" + semanticize.loopString + "\"");
                        }
                        rexp.setWalkStatus(1);
                    }
                }
            }

            /*
             * The following code performs the lookahead ambiguity checking.
             */
            if (!context.hasErrors()) {
                for (NormalProduction bnfproduction : request.getNormalProductions()) {
                    TreeWalker.walk(bnfproduction.getExpansion(),
                            semanticize.new LookaheadChecker(semanticize), false);
                }
            }

        } // matches "if (Options.getSanityCheck()) {"

        if (context.hasErrors())
            throw new ParseException();
    }

    // returns true if "exp" can expand to the empty string, returns false otherwise.
    public static boolean emptyExpansionExists(Expansion exp) {
        if (exp instanceof NonTerminal e)
            return e.getProd().isEmptyPossible();
        else if (exp instanceof Action)
            return true;
        else if (exp instanceof RegularExpression)
            return false;
        else if (exp instanceof OneOrMore)
            return Semanticize.emptyExpansionExists(((OneOrMore) exp).getExpansion());
        else if ((exp instanceof ZeroOrMore) || (exp instanceof ZeroOrOne))
            return true;
        else if (exp instanceof Lookahead)
            return true;
        else if (exp instanceof Choice choice) {
            for (var object : choice.getChoices()) {
                if (Semanticize.emptyExpansionExists(object))
                    return true;
            }
            return false;
        }
        else if (exp instanceof Sequence sequence) {
            for (var object : sequence.getUnits()) {
                if (!Semanticize.emptyExpansionExists((Expansion) object))
                    return false;
            }
            return true;
        }
        else
            return false; // This should be dead code.
    }

    // Checks to see if the "str" is superseded by another equal (except case) string
    // in table.
    private boolean hasIgnoreCase(Hashtable<String, RExpression> table, String str) {
        RExpression rexp;
        rexp = (table.get(str));
        if ((rexp != null) && !rexp.getTpContext().isIgnoreCase())
            return false;
        for (Enumeration<RExpression> enumeration = table.elements(); enumeration.hasMoreElements(); ) {
            rexp = (enumeration.nextElement());
            if (rexp.getTpContext().isIgnoreCase()) {
                this.other = rexp;
                return true;
            }
        }
        return false;
    }

    // Updates prod.leftExpansions based on a walk of exp.
    private void addLeftMost(NormalProduction prod, Expansion exp) {
        if (exp instanceof NonTerminal) {
            for (int i = 0; i < prod.leIndex; i++) {
                if (prod.getLeftExpansions()[i] == ((NonTerminal) exp).getProd())
                    return;
            }
            if (prod.leIndex == prod.getLeftExpansions().length) {
                NormalProduction[] newle = new NormalProduction[prod.leIndex * 2];
                System.arraycopy(prod.getLeftExpansions(), 0, newle, 0, prod.leIndex);
                prod.setLeftExpansions(newle);
            }
            prod.getLeftExpansions()[prod.leIndex++] = ((NonTerminal) exp).getProd();
        }
        else if (exp instanceof OneOrMore e)
            addLeftMost(prod, e.getExpansion());
        else if (exp instanceof ZeroOrMore e)
            addLeftMost(prod, e.getExpansion());
        else if (exp instanceof ZeroOrOne e)
            addLeftMost(prod, e.getExpansion());
        else if (exp instanceof Choice choice) {
            for (var object : choice.getChoices()) {
                addLeftMost(prod, object);
            }
        }
        else if (exp instanceof Sequence sequence) {
            for (Object object : sequence.getUnits()) {
                Expansion e = (Expansion) object;
                addLeftMost(prod, e);
                if (!Semanticize.emptyExpansionExists(e))
                    break;
            }
        }
    }


    // Returns true to indicate an unraveling of a detected left recursion loop,
    // and returns false otherwise.
    private boolean prodWalk(NormalProduction prod) {
        prod.setWalkStatus(-1);
        for (int i = 0; i < prod.leIndex; i++) {
            if (prod.getLeftExpansions()[i].getWalkStatus() == -1) {
                prod.getLeftExpansions()[i].setWalkStatus(-2);
                this.loopString = prod.getLhs() + "... --> " + prod.getLeftExpansions()[i].getLhs() + "...";
                if (prod.getWalkStatus() == -2) {
                    prod.setWalkStatus(1);
                    this.context.onSemanticError(prod,
                            "Left recursion detected: \"" + this.loopString + "\"");
                    return false;
                }
                else {
                    prod.setWalkStatus(1);
                    return true;
                }
            }
            else if ((prod.getLeftExpansions()[i].getWalkStatus() == 0) && prodWalk(
                    prod.getLeftExpansions()[i])) {
                this.loopString = prod.getLhs() + "... --> " + this.loopString;
                if (prod.getWalkStatus() == -2) {
                    prod.setWalkStatus(1);
                    this.context.onSemanticError(prod,
                            "Left recursion detected: \"" + this.loopString + "\"");
                    return false;
                }
                else {
                    prod.setWalkStatus(1);
                    return true;
                }
            }
        }
        prod.setWalkStatus(1);
        return false;
    }

    // Returns true to indicate an unraveling of a detected loop,
    // and returns false otherwise.
    private boolean rexpWalk(RExpression rexp) {
        if (rexp instanceof RJustName jn) {
            if (jn.getRegexpr().getWalkStatus() == -1) {
                jn.getRegexpr().setWalkStatus(-2);
                this.loopString = "..." + jn.getRegexpr().getLabel() + "...";
                // Note: Only the regexpr's of RJustName nodes and the top leve
                // regexpr's can have labels. Hence it is only in these cases that
                // the labels are checked for to be added to the loopString.
                return true;
            }
            else if (jn.getRegexpr().getOrdinal() == 0) {
                jn.getRegexpr().setOrdinal(-1);
                if (rexpWalk(jn.getRegexpr())) {
                    this.loopString = "..." + jn.getRegexpr().getLabel() + "... --> " + this.loopString;
                    if (jn.getRegexpr().getOrdinal() == -2) {
                        jn.getRegexpr().setWalkStatus(1);
                        this.context.onSemanticError(jn.getRegexpr(),
                                "Loop in regular expression detected: \"" + this.loopString + "\"");
                        return false;
                    }
                    else {
                        jn.getRegexpr().setWalkStatus(1);
                        return true;
                    }
                }
                else {
                    jn.getRegexpr().setWalkStatus(1);
                    return false;
                }
            }
        }
        else if (rexp instanceof RChoice choice) {
            for (var object : choice.getChoices()) {
                if (rexpWalk(object))
                    return true;
            }
            return false;
        }
        else if (rexp instanceof RSequence rSequence) {
            for (var object : rSequence.getUnits()) {
                if (rexpWalk(object))
                    return true;
            }
            return false;
        }
        else if (rexp instanceof ROneOrMore re)
            return rexpWalk(re.getRegexpr());
        else if (rexp instanceof RZeroOrMore re)
            return rexpWalk(re.getRegexpr());
        else if (rexp instanceof RZeroOrOne re)
            return rexpWalk(re.getRegexpr());
        else if (rexp instanceof RRepetitionRange re)
            return rexpWalk(re.getRegexpr());
        return false;
    }

    private void prepareToRemove(List<RegExprSpec> vec, Object item) {
        this.removeList.add(vec);
        this.itemList.add(item);
    }

    private void removePreparedItems() {
        for (int i = 0; i < this.removeList.size(); i++) {
            List<RegExprSpec> list = this.removeList.get(i);
            list.remove(this.itemList.get(i));
        }
        this.removeList.clear();
        this.itemList.clear();
    }

    /**
     * Objects of this class are created from class Semanticize to work on references to regular
     * expressions from RJustName's.
     */
    private class FixRJustNames implements TreeWalker {

        private RExpression root;

        @Override
        public boolean goDeeper(Expansion e) {
            return true;
        }

        @Override
        public void action(Expansion e) {
            if (e instanceof RJustName jn) {
                RExpression rexp = Semanticize.this.named_tokens_table.get(jn.getLabel());
                if (rexp == null)
                    getContext().onSemanticError(e,
                            "Undefined lexical token name \"" + jn.getLabel() + "\".");
                else if ((jn == this.root) && !jn.getTpContext().isExplicit() && rexp.isPrivateExp())
                    getContext().onSemanticError(e,
                            "Token name \"" + jn.getLabel() + "\" refers to a private "
                                    + "(with a #) regular expression.");
                else if ((jn == this.root) && !jn.getTpContext().isExplicit()
                        && (rexp.getTpContext().getKind() != TokenProduction.Kind.TOKEN))
                    getContext().onSemanticError(e,
                            "Token name \"" + jn.getLabel() + "\" refers to a non-token "
                                    + "(SKIP, MORE, IGNORE_IN_BNF) regular expression.");
                else {
                    jn.setOrdinal(rexp.getOrdinal());
                    jn.setRegexpr(rexp);
                }
            }
        }

    }

    private class LookaheadFixer implements TreeWalker {

        @Override
        public boolean goDeeper(Expansion e) {
            return !(e instanceof RegularExpression);
        }

        @Override
        public void action(Expansion e) {
            if (e instanceof Sequence seq) {
                if ((e.parent() instanceof Choice) || (e.parent() instanceof ZeroOrMore)
                        || (e.parent() instanceof OneOrMore)
                        || (e.parent() instanceof ZeroOrOne))
                    return;

                Lookahead la = (Lookahead) (seq.getUnits().getFirst());
                if (!la.isExplicit())
                    return;
                // Create a singleton choice with an empty action.
                Choice ch = new Choice();
                ch.setLocation(la);
                ch.setParent(seq);
                Sequence seq1 = new Sequence();
                seq1.setLocation(la);
                seq1.setParent(ch);
                seq1.getUnits().add(la);
                la.setParent(seq1);
                Action act = new Action();
                act.setLocation(la);
                act.setParent(seq1);
                seq1.getUnits().add(act);
                ch.getChoices().add(seq1);
                if (la.getAmount() != 0) {
                    if (!la.getActionTokens().isEmpty())
                        Semanticize.this.context.onWarning(la,
                                "Encountered LOOKAHEAD(...) at a non-choice location.  "
                                        + "Only semantic lookahead will be considered here.");
                    else
                        Semanticize.this.context.onWarning(la,
                                "Encountered LOOKAHEAD(...) at a non-choice location.  This will be ignored.");
                }
                // Now we have moved the lookahead into the singleton choice. Now create
                // a new dummy lookahead node to replace this one at its original location.
                Lookahead la1 = new Lookahead();
                la1.setExplicit(false);
                la1.setLocation(la);
                la1.setParent(seq);
                // Now set the la_expansion field of la and la1 with a dummy expansion (we use EOF).
                la.setLaExpansion(new REndOfFile());
                la1.setLaExpansion(new REndOfFile());
                seq.getUnits().set(0, la1);
                seq.getUnits().add(1, ch);
            }
        }

    }

    private class ProductionDefinedChecker implements TreeWalker {

        @Override
        public boolean goDeeper(Expansion e) {
            return !(e instanceof RegularExpression);
        }

        @Override
        public void action(Expansion e) {
            if (e instanceof NonTerminal nt) {
                if ((nt.setProd(Semanticize.this.request.getProductionTable(nt.getName()))) == null)
                    getContext().onSemanticError(e,
                            "Non-terminal " + nt.getName() + " has not been defined.");
                else
                    nt.getProd().getParents().add(nt);
            }
        }

    }

    private class EmptyChecker implements TreeWalker {

        @Override
        public boolean goDeeper(Expansion e) {
            return !(e instanceof RegularExpression);
        }

        @Override
        public void action(Expansion e) {
            if (e instanceof OneOrMore oneOrMore) {
                if (Semanticize.emptyExpansionExists(oneOrMore.getExpansion()))
                    getContext().onSemanticError(e,
                            "Expansion within \"(...)+\" can be matched by empty string.");
            }
            else if (e instanceof ZeroOrMore zeroOrMore) {
                if (Semanticize.emptyExpansionExists(zeroOrMore.getExpansion()))
                    Semanticize.this.context.onSemanticError(e,
                            "Expansion within \"(...)*\" can be matched by empty string.");
            }
            else if ((e instanceof ZeroOrOne zeroOrOne) && Semanticize.emptyExpansionExists(
                    zeroOrOne.getExpansion()))
                getContext().onSemanticError(e,
                        "Expansion within \"(...)?\" can be matched by empty string.");
        }

    }

    private class LookaheadChecker implements TreeWalker {

        private final Semanticize data;

        private LookaheadChecker(Semanticize data) {
            this.data = data;
        }

        @Override
        public boolean goDeeper(Expansion e) {
            return !(e instanceof RegularExpression) && !(e instanceof Lookahead);
        }

        @Override
        public void action(Expansion e) {
            if (e instanceof Choice choice) {
                if ((getContext().getLookahead() == 1) || getContext().isForceLaCheck())
                    LookaheadCalc.choiceCalc(choice, this.data, getContext());
            }
            else if (e instanceof OneOrMore exp) {
                if (getContext().isForceLaCheck() || (implicitLA(exp.getExpansion()) && (
                        getContext().getLookahead() == 1)))
                    LookaheadCalc.ebnfCalc(exp, exp.getExpansion(), this.data, getContext());
            }
            else if (e instanceof ZeroOrMore exp) {
                if (getContext().isForceLaCheck() || (implicitLA(exp.getExpansion()) && (
                        getContext().getLookahead() == 1)))
                    LookaheadCalc.ebnfCalc(exp, exp.getExpansion(), this.data, getContext());
            }
            else if (e instanceof ZeroOrOne exp) {
                if (getContext().isForceLaCheck() || (implicitLA(exp.getExpansion()) && (
                        getContext().getLookahead() == 1)))
                    LookaheadCalc.ebnfCalc(exp, exp.getExpansion(), this.data, getContext());
            }
        }

        private boolean implicitLA(Expansion exp) {
            if (!(exp instanceof Sequence seq))
                return true;
            Object obj = seq.getUnits().getFirst();
            if (obj instanceof Lookahead lookahead)
                return !lookahead.isExplicit();
            return true;
        }
    }
}
