# ADR-0030: The shape of the generated Rust parser

- **Status:** 🟢 Accepted <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-24
- **Deciders:** Markus Brigl
- **Supersedes:** —
- **Superseded by:** —

## Context

[ADR-0004](0004-multi-target-code-generation.md) makes Rust one of the three targets. The
specification's last non-goal requires that generated code stand on its own toolchain, with no
Waggle runtime library ([SPECIFICATION §3](../SPECIFICATION.md)).

Since 45359e91 the generated Rust **lexer** compiles, runs, and reads the same tokens as the Java
lexer. The generated Rust **parser** does not compile. Its template, `templates/rust/parser/parser.rs`,
is a partial port with these problems:

- **Java left in the output.** Java survives in comments and in code: `jj_consume_token` begins with
  `Token old_token; if ((old_token = token).next != null)`, and the switch on the next token is
  `match (jj_ntk==-1)?jj_ntk_f():jj_ntk)`.
- **Errors are not modelled.** They travel as `std::io::Error` with the text `"generateParseException"`.
- **Failures do not stop a production.** A production collects them in a `try_catch` variable and
  carries on: after a failed `jj_consume_token` the next one still runs.
- **Borrow problems.** Tokens are a linked list of `Rc<RefCell<Token>>`, the Java structure, so every
  step clones and borrows at run time.
- **Parameters in the wrong order.** They come out in Java order: a production `value(int depth)`
  becomes `fn value(&mut selfint depth)`. The grammar reads a parameter as a type followed by a name
  (`FormalParameters` in `Waggle.waggle`), and the back end copies the tokens.
- **A lexical error panics.** The lexer does not return it as a value. That was a stopgap in 45359e91
  so that an error no longer looked like end of input.

Porting the template decides what a Rust user of a generated parser works with: the error type, how
tokens are held, and how a production is declared. Those are the public interface of the generated
code, so they are recorded here before the port (AGENTS.md §3.1).

**State of the art.** The established Rust parser and lexer generators all report failure as a value:

- **LALRPOP.** A generated parser returns `Result<T, ParseError<L, T, E>>`. The variants
  `UnrecognizedToken` and `UnrecognizedEof` carry the tokens that were `expected`, and a lexer's or
  user's error travels in `ParseError::User`
  ([lalrpop_util::ParseError](https://docs.rs/lalrpop-util/latest/lalrpop_util/enum.ParseError.html)).
- **pest.** `Parser::parse` returns `Result<Pairs<'_, R>, Error<R>>`
  ([pest::Parser](https://docs.rs/pest/latest/pest/trait.Parser.html)).
- **Logos.** A lexer is an iterator with `type Item = Result<Token, Error>`
  ([logos::Lexer](https://docs.rs/logos/latest/logos/struct.Lexer.html)).

None of them panics on bad input, and none links its tokens with `Rc<RefCell<…>>`.

## Decision

We will generate a Rust parser that is idiomatic Rust rather than a transliteration of the Java one.

1. **Failure is a value.**
   - The lexer's `get_next_token` returns `Result<Token, LexicalError>`.
   - Every production returns `Result<T, ParseError>`, where `T` is its declared result type, or
     `()` when it has none.
   - `ParseError` is an enum with two variants. `Lexical(LexicalError)` wraps a lexer failure.
     `UnexpectedToken` carries the token found and the images of the tokens that were expected, the
     same set the Java `ParseException` reports.
   - Both error types carry line and column and implement `std::fmt::Display` and
     `std::error::Error`. The message is the Java one.
   - Errors propagate with `?`. Nothing in the generated code panics on input, only on a broken
     invariant of its own.
2. **Tokens are values in one buffer.**
   - The parser owns the tokens it has read in a `Vec<Token>`, and the lookahead routines scan it by
     index. A token owns the special tokens before it (`special: Vec<Token>`).
   - The Java linked list with `next` and `specialToken` becomes indices into that buffer, and
     `get_token(n)` returns `&Token`.
3. **Productions are methods.**
   - A production becomes `pub fn name(&mut self, params) -> Result<T, ParseError>`.
   - A parameter that the grammar writes as type followed by name (`i32 depth`) is written by the Rust
     back end as `depth: i32`. The grammar syntax stays one for all targets, and only the rendering
     is Rust.
   - An action that returns a value writes `return Ok(value);`, since actions are target code
     (ADR-0004).
4. **Scope.** Tree building, `DEPTH_LIMIT` and the parser traces stay rejected for Rust, as today
   (README, known limitations). They are ported separately.
5. **Evidence.** `RustCompilesTest` compiles and runs the parser. It checks accept and reject, and
   the line and column of the first error, against the Java parser for the grammars the Java tests
   run: plain LL(1), syntactic and semantic lookahead, a production with a parameter and a result.

## Consequences

- **A Rust caller handles bad input like any other Rust input error**, with `match` or `?`, and gets
  the expected tokens as the Java caller does. Nothing needs `catch_unwind`.
- **The lexer API changes** from `Token` to `Result<Token, LexicalError>`. The stopgap panic of
  45359e91 goes away; the lexer tests switch from checking a panic to checking the error value.
- **Buffering tokens costs memory:** the parser keeps every token until it is dropped. That matches
  what the Java parser keeps reachable through `token.next`, and the input is already held in memory
  by the character stream.
- **Rust grammars differ from Java grammars** in their parameter and result types and in their
  actions, as they already differ in their actions. The syntax of the grammar is unchanged.
- **The port is a sizeable piece of work.** It touches the parser template, `RustParserGenerator`,
  `RustParserSyntax` and the lookahead emitters' Rust spelling. It lands in steps, each tested
  against the Java parser.

## Alternatives considered

**Finish the transliteration: `Rc<RefCell<Token>>` and the Java control flow.** This is the least
new code, but every token access borrows at run time and can panic on a second borrow. The Java
statement order around `jj_consume_token` does not survive Rust's ownership rules without a clone per
step. It is the path the current template took and stalled on.

**Keep panicking on bad input.** A panic is not an error value: a caller has to catch unwinding to
report a syntax error, `catch_unwind` does not work with `panic = "abort"`, and none of the generators
above does it. It was acceptable only as a stopgap for the lexer.

**Generate code against a parser library** (for example emit a pest grammar or use nom). This
contradicts the specification: generated code must not need a runtime dependency. It would also
change the parsing algorithm away from the LL(k) that Waggle's lookahead analysis plans for.
