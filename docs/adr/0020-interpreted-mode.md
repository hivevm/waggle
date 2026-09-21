# ADR-0020: A grammar can be run without generating code

- **Status:** 🟡 Proposed
- **Date:** 2026-09-21
- **Deciders:** HiveVM Waggle maintainers
- **Supersedes:** —
- **Superseded by:** —

## Context

Trying a grammar out means generating sources, compiling them and loading the result. For a
question as small as "does this token definition match what I think it matches", that is a
generate-compile-load cycle and a target language one may not even want.

JavaCC had an answer. This repository's own history carries it: commit `4ace1ec8`, "Initial version
of interpreted token manager", added a `JavaCCInterpreter` that ran a lexical specification over
input directly. It had to invent a structure to do so — `TokenizerData`, which `LexGen` filled as a
side product when asked to `generateDataOnly` — because the lexer stage produced code and nothing
else. By the version this project forked, the class had been hollowed out to a `main` that read two
files and printed a timing.

What survived the fork into Waggle was a stub: `ParserInterpreter.tokenize` read a character,
computed a state key, discarded it, and never advanced `curPos`. It looped forever on any non-empty
input, and nothing called it — `ParserBuilder.interpret` had no caller either. It was removed as
dead code, which narrowed a supported entry point ([SPECIFICATION §3](../SPECIFICATION.md) names
`ParserBuilder` as one) to remove something that never worked.

The reason to bring it back is that the obstacle is gone. [ADR-0012](0012-lexer-owns-dfa-construction.md)
made stage 4 the owner of NFA/DFA construction and the generators pure renderers of a finished
model. The automaton JavaCC had to export into a parallel structure is now simply the output of a
stage, and the back ends are one reader of it rather than the only one.

## Decision

1. **An interpreter runs the automaton instead of rendering it.**
   `lexer.LexerInterpreter` simulates the NFA that stage 4 produced: the epsilon closure of the
   current lexical state's start, advanced per character, longest match, and the lowest token kind
   among the states accepting at that position — the grammar's declaration order is its precedence.
   SKIP is dropped and MORE folded into the token that follows, as the generated token manager does.
2. **`api.ParserInterpreter` is the entry point**, and `ParserBuilder.interpret(text)` returns. It
   runs the pipeline up to the finished automaton and stops before the back end.
3. **It builds with `NO_DFA`.** The string-literal DFA and the NFA are two renderings of one
   specification; with `NO_DFA` the whole specification is in the NFA, which is the one thing to
   simulate. It changes how the automaton is built, never what it accepts.
4. **It is a second reader of the model, not a second model.** The interpreter adds nothing to
   `LexerData` and changes no generated output. If it ever needs the model to grow, that is a
   decision about the model.

Scope: this interprets the **lexical** specification. Interpreting the grammar itself — walking
productions and lookahead at run time — is a different and much larger thing, and is not decided
here.

## Consequences

- A grammar can be tried out from a test or a tool in one call, in any project, with no target
  language involved.
- The lexer model gains a consumer that is not a code generator, which is a useful pressure: a model
  that only a renderer can read is not the language-independent artifact ADR-0012 claims.
- The interpreter is a second implementation of the matching rules, so the two can disagree. It is
  tested on the rules that make a tokenizer right rather than plausible — longest match, declaration
  order breaking a tie, SKIP, an unmatchable character — but agreement with the generated lexer is
  not proved by construction.
- Interpreting is slower than a generated token manager by a wide margin, and is not meant to
  replace one.
- Lexical **actions** are not run. JavaCC's interpreter printed "Actions not implemented (yet) in
  interpreted mode"; here they are simply not reached, because an action is host-language code that
  only the generated lexer can execute.

## Alternatives considered

- **Leave it removed.** Rejected: the entry point is part of the supported API, the capability is
  genuinely useful, and the only reason it did not work was an unfinished port.
- **Restore JavaCC's design — export a `TokenizerData` from the lexer stage.** Rejected: it exists
  because JavaCC's lexer stage produced code. Waggle's produces the automaton, so a parallel
  structure would be a copy that can drift, which is what ADR-0012 removed.
- **Interpret the DFA the generators render instead of the NFA.** Rejected: the string-literal DFA
  and the NFA together are two halves of one specification, and simulating both means implementing
  the generated token manager twice over. `NO_DFA` puts everything in one automaton.
- **Interpret the parser too, at the same time.** Rejected as a separate decision: it needs the
  lookahead plan, the productions and the semantic actions, and the last of those is host-language
  code. Deciding it alongside the tokenizer would decide it badly.
