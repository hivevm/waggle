// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import org.hivevm.cc.model.*;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.ParseException;
import org.hivevm.cc.parser.RegExprSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class Semanticize {

    private final SemanticRequest request;
    private final SemanticContext context;

    private long generationIndex;
    private int laLimit;
    private boolean considerSemanticLA;


    private ArrayList<MatchInfo> sizeLimitedMatches;
    private final List<List<RegExprSpec>> removeList;
    private final List<Object> itemList;

    private RExpression other;
    // The string in which the following methods store information.
    private String loopString;

    /**
     * A mapping of ordinal values (represented as objects of type "Integer") to the corresponding
     * RegularExpression's.
     */
    private final Map<Integer, RExpression> rexps_of_tokens = new HashMap<>();
    /**
     * This is a symbol table that contains all named tokens (those that are defined with a label).
     * The index to the table is the image of the label and the contents of the table are of type
     * "RegularExpression".
     */
    private final Map<String, RExpression> named_tokens_table = new HashMap<>();


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
        var context = new SemanticContext(options);
        if (JavaCCErrors.hasError())
            throw new ParseException();

        if ((context.getLookahead() > 1) && !context.isForceLaCheck() && context.isSanityCheck())
            context.onWarning(
                    "Lookahead adequacy checking not being performed since option LOOKAHEAD "
                            + "is more than 1.  Set option FORCE_LA_CHECK to true to force checking.");

        new Semanticize(request, context).run();
    }

    /**
     * The semantic phases, in order. Each was an inline block of the one 333-line method this
     * replaces; the order between them is significant, so they stay a straight sequence.
     */
    private void run() throws ParseException {
        liftLookaheadsToChoices();
        buildProductionTable();
        checkNonTerminalsAreDefined();

        checkTokenProductions();
        removePreparedItems();

        collectNamedTokens();
        mergeDuplicateStringLiterals();
        removePreparedItems();

        resolveRegularExpressionNames();
        removePreparedItems();
        removePreparedItems();

        if (this.context.hasErrors())
            throw new ParseException();

        computeEmptyPossible();

        if (this.context.isSanityCheck() && !this.context.hasErrors()) {
            checkNoEmptyRepetitions();
            collectLeftMostProductions();
            checkLeftRecursion();
            checkRegularExpressionLoops();

            if (!this.context.hasErrors()) {
                checkLookaheadAmbiguity();
            }
        }

        if (this.context.hasErrors())
            throw new ParseException();
    }

    /**
     * Converts every LOOKAHEAD that is not at a choice point into a trivial choice, so that its
     * semantic lookahead can be evaluated along with the others.
     */
    private void liftLookaheadsToChoices() {
        for (var bnfproduction : this.request.getNormalProductions()) {
            TreeWalker.walk(bnfproduction.getExpansion(), new LookaheadFixer(), true);
        }
    }

    /**
     * Registers every production, reporting the ones defined twice.
     */
    private void buildProductionTable() {
        for (var p : this.request.getNormalProductions()) {
            if (this.request.setProductionTable(p) != null)
                this.context.onSemanticError(p,
                        p.getLhs() + " occurs on the left hand side of more than one production.");
        }
    }

    /**
     * Checks that every non-terminal used on a right-hand side has a production.
     */
    private void checkNonTerminalsAreDefined() {
        for (var bnfproduction : this.request.getNormalProductions()) {
            TreeWalker.walk((bnfproduction).getExpansion(),
                    new ProductionDefinedChecker(),
                    false);
        }
    }

    /**
     * Checks the token productions: target lexical states exist, <EOF> and free-standing <name>
     * references are placed legally, and private (#) expressions are not defined inside the BNF.
     */
    private void checkTokenProductions() {
        for (var tp : this.request.getTokenProductions()) {
            var respecs = tp.getRespecs();
            for (var res : respecs) {
                if ((res.nextState != null) && (this.request.getStateIndex(res.nextState) == null))
                    this.context.onSemanticError(res.nsTok,
                            "Lexical state \"" + res.nextState + "\" has not been defined.");
                if (res.rexp instanceof REndOfFile) {
                    // this.context.onSemanticError(res.rexp, "Badly placed <EOF>.");
                    if (tp.getLexStates() != null)
                        this.context.onSemanticError(res.rexp,
                                "EOF action/state change must be specified for all states, "
                                        + "i.e., <*>TOKEN:.");
                    if (tp.getKind() != TokenKind.TOKEN)
                        this.context.onSemanticError(res.rexp,
                                "EOF action/state change can be specified only in a "
                                        + "TOKEN specification.");
                    if ((this.request.getNextStateForEof() != null) || (this.request.getActionForEof()
                            != null))
                        this.context.onSemanticError(res.rexp,
                                "Duplicate action/state change specification for <EOF>.");
                    this.request.setActionForEof(res.act);
                    this.request.setNextStateForEof(res.nextState);
                    this.prepareToRemove(respecs, res);
                } else if (tp.isExplicit() && (res.rexp instanceof RJustName)) {
                    this.context.onWarning(res.rexp,
                            "Ignoring free-standing regular expression reference.  "
                                    + "If you really want this, you must give it a different label as <NEWLABEL:<"
                                    + res.rexp.getLabel()
                                    + ">>.");
                    this.prepareToRemove(respecs, res);
                } else if (!tp.isExplicit() && res.rexp.isPrivateExp())
                    this.context.onSemanticError(res.rexp,
                            "Private (#) regular expression cannot be defined within "
                                    + "grammar productions.");
            }
        }

        this.removePreparedItems();
    }

    /**
     * Collects the labels of all named tokens, reporting duplicates and clashes with a lexical state.
     */
    private void collectNamedTokens() {
        for (var tokenProduction : this.request.getTokenProductions()) {
            for (var respec : tokenProduction.getRespecs()) {
                if (!(respec.rexp instanceof RJustName) && !respec.rexp.getLabel().isEmpty()) {
                    String s = respec.rexp.getLabel();
                    Object obj = this.named_tokens_table.put(s, respec.rexp);
                    if (obj != null)
                        this.context.onSemanticError(respec.rexp,
                                "Multiply defined lexical token name \"" + s + "\".");
                    else
                        this.request.addOrderedNamedToken(respec.rexp);
                    if (this.request.getStateIndex(s) != null)
                        this.context.onSemanticError(respec.rexp,
                                "Lexical token name \"" + s + "\" is the same as "
                                        + "that of a lexical state.");
                }
            }
        }
    }

    /**
     * Merges repeated uses of the same string literal within a lexical state, numbers every regular
     * expression (its ordinal), and fills the token-name table.
     */
    private void mergeDuplicateStringLiterals() {

        this.request.unsetTokenCount();
        for (var tokenProduction : this.request.getTokenProductions()) {
            var respecs = tokenProduction.getRespecs();
            if (tokenProduction.getLexStates() == null) {
                tokenProduction.setLexStates(new String[this.request.getStateNames().size()]);
                int i = 0;
                for (var stateName : this.request.getStateNames()) {
                    tokenProduction.setLexState(stateName, i++);
                }
            }
            Hashtable<String, Hashtable<String, RExpression>>[] table = new Hashtable[tokenProduction.getLexStates().length];
            for (int i = 0; i < tokenProduction.getLexStates().length; i++) {
                table[i] = this.request.getSimpleTokenTable(tokenProduction.getLexStates()[i]);
            }
            for (var respec : respecs) {
                if (respec.rexp instanceof RStringLiteral sl) {
                    // This loop performs the checks and actions with respect to each lexical state.
                    for (int i = 0; i < table.length; i++) {
                        // Get table of all case variants of "sl.image" into table2.
                        Hashtable<String, RExpression> table2 = table[i].get(sl.getImage().toUpperCase());
                        if (table2 == null) {
                            // There are no case variants of "sl.image" earlier than the current one.
                            // So go ahead and insert this item.
                            if (sl.getOrdinal() == 0)
                                sl.setOrdinal(this.request.addTokenCount());
                            table2 = new Hashtable<>();
                            table2.put(sl.getImage(), sl);
                            table[i].put(sl.getImage().toUpperCase(), table2);
                        } else if (this.hasIgnoreCase(table2,
                                sl.getImage())) { // hasIgnoreCase
                            // sets
                            // "other"
                            // if it is found.
                            // Since IGNORE_CASE version exists, current one is useless and bad.
                            if (!sl.isExplicit()) {
                                // inline BNF string is used earlier with an IGNORE_CASE.
                                this.context.onSemanticError(sl,
                                        "String \"" + sl.getImage() + "\" can never be matched "
                                                + "due to presence of more general (IGNORE_CASE) regular expression "
                                                + "at line "
                                                + this.other.getLine() + ", column "
                                                + this.other.getColumn()
                                                + ".");
                            } else
                                // give the standard error message.
                                this.context.onSemanticError(sl,
                                        "Duplicate definition of string token \"" + sl.getImage()
                                                + "\" "
                                                + "can never be matched.");
                        } else if (sl.isIgnoreCase()) {
                            // This has to be explicit. A warning needs to be given with respect
                            // to all previous strings.
                            var pos = new StringBuilder();
                            int count = 0;
                            for (var rexp : table2.values()) {
                                if (count != 0)
                                    pos.append(",");
                                pos.append(" line ").append(rexp.getLine());
                                count++;
                            }
                            if (count == 1)
                                this.context.onWarning(sl,
                                        "String with IGNORE_CASE is partially superseded by string at"
                                                + pos + ".");
                            else
                                this.context.onWarning(sl,
                                        "String with IGNORE_CASE is partially superseded by strings at"
                                                + pos + ".");
                            // This entry is legitimate. So insert it.
                            if (sl.getOrdinal() == 0)
                                sl.setOrdinal(this.request.addTokenCount());
                            table2.put(sl.getImage(), sl);
                            // The above "put" may override an existing entry (that is not IGNORE_CASE) and that's
                            // the desired behavior.
                        } else {
                            // The rest of the cases do not involve IGNORE_CASE.
                            var re = table2.get(sl.getImage());
                            if (re == null) {
                                if (sl.getOrdinal() == 0)
                                    sl.setOrdinal(this.request.addTokenCount());
                                table2.put(sl.getImage(), sl);
                            } else if ((tokenProduction).isExplicit()) {
                                // This is an error even if the first occurrence was implicit.
                                if ((tokenProduction).getLexStates()[i].equals("DEFAULT")) {
                                    this.context.onSemanticError(sl,
                                            "Duplicate definition of string token \"" + sl.getImage()
                                                    + "\".");
                                } else {
                                    this.context.onSemanticError(sl,
                                            "Duplicate definition of string token \"" + sl.getImage()
                                                    + "\" in lexical state \""
                                                    + (tokenProduction).getLexStates()[i] + "\".");
                                }
                            } else if (re.getTokenKind() != TokenKind.TOKEN) {
                                this.context.onSemanticError(sl,
                                        "String token \"" + sl.getImage()
                                                + "\" has been defined as a \""
                                                + re.getTokenKind().name() + "\" token.");
                            } else if (re.isPrivateExp()) {
                                this.context.onSemanticError(sl,
                                        "String token \"" + sl.getImage()
                                                + "\" has been defined as a private regular expression.");
                            } else {
                                // This is now a legitimate reference to an existing RStringLiteral.
                                // So we assign it a number and take it out of "rexprlist".
                                // Therefore, if all is OK (no errors), then there will be only unequal
                                // string literals in each lexical state. Note that the only way
                                // this can be legal is if this is a string declared inline within the
                                // BNF. Hence, it belongs to only one lexical state - namely "DEFAULT".
                                sl.setOrdinal(re.getOrdinal());
                                this.prepareToRemove(respecs, respec);
                            }
                        }
                    }
                } else if (!(respec.rexp instanceof RJustName))
                    respec.rexp.setOrdinal(this.request.addTokenCount());
                if (!(respec.rexp instanceof RJustName) && !respec.rexp.getLabel().isEmpty())
                    this.request.setNamesOfToken(respec.rexp);
                if (!(respec.rexp instanceof RJustName))
                    this.rexps_of_tokens.put(respec.rexp.getOrdinal(), respec.rexp);
            }
        }

        this.removePreparedItems();
    }

    /**
     * Attaches each RJustName to the regular expression it names, and drops the top-level ones.
     */
    private void resolveRegularExpressionNames() {
        var frjn = new FixRJustNames();
        for (var tokenProduction : this.request.getTokenProductions()) {
            var respecs = tokenProduction.getRespecs();
            for (RegExprSpec respec : respecs) {
                frjn.root = respec.rexp;
                TreeWalker.walk(respec.rexp, frjn, false);
                if (respec.rexp instanceof RJustName)
                    this.prepareToRemove(respecs, respec);
            }
        }

        this.removePreparedItems();
        this.removePreparedItems();
    }

    /**
     * Marks the productions that can match the empty token sequence. Repeated until nothing changes,
     * because emptiness propagates through references.
     */
    private void computeEmptyPossible() {
        boolean emptyUpdate = true;
        while (emptyUpdate) {
            emptyUpdate = false;
            for (var prod : this.request.getNormalProductions()) {
                if (Semanticize.emptyExpansionExists(prod.getExpansion()) && !prod.isEmptyPossible())
                    emptyUpdate = prod.setEmptyPossible(true);
            }
        }
    }

    /** Checks that no ZeroOrMore, ZeroOrOne or OneOrMore wraps an expansion that can be empty. */
    private void checkNoEmptyRepetitions() {
        for (var production : this.request.getNormalProductions()) {
            TreeWalker.walk(production.getExpansion(), new EmptyChecker(), false);
        }
    }

    /** Records, per production, which productions it can reach without consuming a token. */
    private void collectLeftMostProductions() {
        for (var production : this.request.getNormalProductions()) {
            addLeftMost(production, production.getExpansion());
        }
    }

    /**
     * Reports left recursion. Once a production is known to take part in a loop it is not examined
     * again, so each loop is reported once.
     */
    private void checkLeftRecursion() {
        for (var production : this.request.getNormalProductions()) {
            if (production.getWalkStatus() == 0) {
                prodWalk(production);
            }
        }
    }

    /** Reports regular expressions that refer to themselves. */
    private void checkRegularExpressionLoops() {
        for (var tokenProduction : this.request.getTokenProductions()) {
            for (RegExprSpec respec : tokenProduction.getRespecs()) {
                var rexp = respec.rexp;
                if (rexp.getWalkStatus() != 0) {
                    continue;
                }

                rexp.setWalkStatus(-1);
                if (rexpWalk(rexp)) {
                    this.loopString = "..." + rexp.getLabel() + "... --> " + this.loopString;
                    this.context.onSemanticError(rexp,
                            "Loop in regular expression detected: \"" + this.loopString + "\"");
                }
                rexp.setWalkStatus(1);
            }
        }
    }

    /** The lookahead ambiguity checking. */
    private void checkLookaheadAmbiguity() {
        for (var production : this.request.getNormalProductions()) {
            TreeWalker.walk(production.getExpansion(), new LookaheadChecker(this), false);
        }
    }


    /** Whether "exp" can expand to the empty string. */
    public static boolean emptyExpansionExists(Expansion exp) {
        return switch (exp) {
            case NonTerminal nonTerminal -> nonTerminal.getProd().isEmptyPossible();
            case Action action -> true;
            case Lookahead lookahead -> true;
            case ZeroOrMore zeroOrMore -> true;
            case ZeroOrOne zeroOrOne -> true;

            case RegularExpression regexp -> false;
            case OneOrMore oneOrMore -> Semanticize.emptyExpansionExists(oneOrMore.getExpansion());

            // A choice is empty-able as soon as one alternative is.
            case Choice choice -> choice.getChoices().stream()
                    .anyMatch(Semanticize::emptyExpansionExists);

            // A sequence only when every unit is.
            case Sequence sequence -> sequence.getUnits().stream()
                    .allMatch(Semanticize::emptyExpansionExists);

            // A production never appears as an expansion of itself. This was the branch the old
            // if/else chain ended in, commented "This should be dead code" — the sealed hierarchy
            // now names the one type that reached it.
            case NormalProduction production -> false;
        };
    }

    // Checks to see if the "str" is superseded by another equal (except case) string
    // in table.
    private boolean hasIgnoreCase(Hashtable<String, RExpression> table, String str) {
        var rexp = table.get(str);
        if ((rexp != null) && !rexp.isIgnoreCase())
            return false;
        for (var re : table.values()) {
            if (re.isIgnoreCase()) {
                this.other = re;
                return true;
            }
        }
        return false;
    }

    /** Records, in prod.leftExpansions, the non-terminals "exp" can reach without consuming a token. */
    private void addLeftMost(NormalProduction prod, Expansion exp) {
        switch (exp) {
            case NonTerminal nonTerminal -> {
                var target = nonTerminal.getProd();
                if (!prod.getLeftExpansions().contains(target)) {
                    prod.getLeftExpansions().add(target);
                }
            }

            case OneOrMore oneOrMore -> addLeftMost(prod, oneOrMore.getExpansion());
            case ZeroOrMore zeroOrMore -> addLeftMost(prod, zeroOrMore.getExpansion());
            case ZeroOrOne zeroOrOne -> addLeftMost(prod, zeroOrOne.getExpansion());

            case Choice choice -> choice.getChoices().forEach(o -> addLeftMost(prod, o));

            // Walk the sequence only as far as its units can still be empty.
            case Sequence sequence -> {
                for (var unit : sequence.getUnits()) {
                    addLeftMost(prod, unit);
                    if (!Semanticize.emptyExpansionExists(unit)) {
                        break;
                    }
                }
            }

            // Nothing to the left of these.
            case Action a -> { }
            case Lookahead l -> { }
            case NormalProduction p -> { }
            case RegularExpression r -> { }
        }
    }


    // Returns true to indicate an unraveling of a detected left recursion loop,
    // and returns false otherwise.
    /**
     * Ends the walk of a production in which a left-recursion loop was found. The loop is reported
     * only at the production that opened it (walk status -2); elsewhere it is handed back up so the
     * caller can extend the loop string. Both call sites in {@link #prodWalk} held this verbatim.
     */
    private boolean unravel(NormalProduction prod) {
        boolean isLoopOwner = prod.getWalkStatus() == -2;
        prod.setWalkStatus(1);
        if (isLoopOwner) {
            this.context.onSemanticError(prod,
                    "Left recursion detected: \"" + this.loopString + "\"");
            return false;
        }
        return true;
    }

    private boolean prodWalk(NormalProduction prod) {
        prod.setWalkStatus(-1);
        for (var leftExpansion : prod.getLeftExpansions()) {
            if (leftExpansion.getWalkStatus() == -1) {
                leftExpansion.setWalkStatus(-2);
                this.loopString = prod.getLhs() + "... --> " + leftExpansion.getLhs() + "...";
                return unravel(prod);
            }

            if ((leftExpansion.getWalkStatus() == 0) && prodWalk(leftExpansion)) {
                this.loopString = prod.getLhs() + "... --> " + this.loopString;
                return unravel(prod);
            }
        }
        prod.setWalkStatus(1);
        return false;
    }

    // Returns true to indicate an unraveling of a detected loop,
    // and returns false otherwise.
    private boolean rexpWalk(RExpression rexp) {
        return switch (rexp) {
            case RJustName justName -> rexpWalkJustName(justName);

            case RChoice choice -> choice.getChoices().stream().anyMatch(this::rexpWalk);
            case RSequence sequence -> sequence.getUnits().stream().anyMatch(this::rexpWalk);

            case ROneOrMore oneOrMore -> rexpWalk(oneOrMore.getRegexpr());
            case RZeroOrMore zeroOrMore -> rexpWalk(zeroOrMore.getRegexpr());
            case RZeroOrOne zeroOrOne -> rexpWalk(zeroOrOne.getRegexpr());
            case RRepetitionRange range -> rexpWalk(range.getRegexpr());

            // Leaves: a loop cannot run through them.
            case RCharacterList c -> false;
            case REndOfFile e -> false;
            case RStringLiteral s -> false;
        };
    }

    /**
     * Only the regular expressions behind an RJustName, and the top-level ones, carry a label — so
     * only here is a label worth adding to the loop string.
     */
    private boolean rexpWalkJustName(RJustName justName) {
        var referenced = justName.getRegexpr();

        if (referenced.getWalkStatus() == -1) {
            referenced.setWalkStatus(-2);
            this.loopString = "..." + referenced.getLabel() + "...";
            return true;
        }

        if (referenced.getOrdinal() != 0) {
            return false;
        }

        referenced.setOrdinal(-1);
        if (!rexpWalk(referenced)) {
            referenced.setWalkStatus(1);
            return false;
        }

        this.loopString = "..." + referenced.getLabel() + "... --> " + this.loopString;
        boolean isLoopOwner = referenced.getOrdinal() == -2;
        referenced.setWalkStatus(1);
        if (isLoopOwner) {
            this.context.onSemanticError(referenced,
                    "Loop in regular expression detected: \"" + this.loopString + "\"");
            return false;
        }
        return true;
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
                var rexp = Semanticize.this.named_tokens_table.get(jn.getLabel());
                if (rexp == null)
                    getContext().onSemanticError(e, "Undefined lexical token name \"" + jn.getLabel() + "\".");
                else if ((jn == this.root) && !jn.isExplicit() && rexp.isPrivateExp())
                    getContext().onSemanticError(e,
                            "Token name \"" + jn.getLabel() + "\" refers to a private "
                                    + "(with a #) regular expression.");
                else if ((jn == this.root) && !jn.isExplicit() && (rexp.getTokenKind() != TokenKind.TOKEN))
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

                var la = (Lookahead) seq.getUnits().getFirst();
                if (!la.isExplicit())
                    return;
                // Create a singleton choice with an empty action.
                var ch = new Choice();
                ch.setLocation(la);
                ch.setParent(seq);

                var seq1 = new Sequence();
                seq1.setLocation(la);
                seq1.setParent(ch);
                seq1.getUnits().add(la);
                la.setParent(seq1);

                var act = new Action();
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
                var la1 = new Lookahead();
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
                    getContext().onSemanticError(e, "Non-terminal " + nt.getName() + " has not been defined.");
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
            } else if (e instanceof ZeroOrMore zeroOrMore) {
                if (Semanticize.emptyExpansionExists(zeroOrMore.getExpansion()))
                    Semanticize.this.context.onSemanticError(e,
                            "Expansion within \"(...)*\" can be matched by empty string.");
            } else if ((e instanceof ZeroOrOne zeroOrOne) && Semanticize.emptyExpansionExists(zeroOrOne.getExpansion()))
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
            } else if (e instanceof OneOrMore exp) {
                if (getContext().isForceLaCheck() || (implicitLA(exp.getExpansion()) && (
                        getContext().getLookahead() == 1)))
                    LookaheadCalc.ebnfCalc(exp, exp.getExpansion(), this.data, getContext());
            } else if (e instanceof ZeroOrMore exp) {
                if (getContext().isForceLaCheck() || (implicitLA(exp.getExpansion()) && (
                        getContext().getLookahead() == 1)))
                    LookaheadCalc.ebnfCalc(exp, exp.getExpansion(), this.data, getContext());
            } else if (e instanceof ZeroOrOne exp) {
                if (getContext().isForceLaCheck() || (implicitLA(exp.getExpansion()) && (
                        getContext().getLookahead() == 1)))
                    LookaheadCalc.ebnfCalc(exp, exp.getExpansion(), this.data, getContext());
            }
        }

        private boolean implicitLA(Expansion exp) {
            if (!(exp instanceof Sequence seq))
                return true;
            Expansion obj = seq.getUnits().getFirst();
            if (obj instanceof Lookahead lookahead)
                return !lookahead.isExplicit();
            return true;
        }
    }
}
