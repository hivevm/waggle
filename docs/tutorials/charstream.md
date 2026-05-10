# The Character Input

Before the [token manager](token-manager.md) can group characters into tokens, something has to feed
it characters. In HiveVM CC (Java target) that something is a **`Provider`** — a small interface the
generated parser reads from. This page covers the built-in providers, character encodings, and
Unicode.

## The `Provider` interface

The generated code defines a single input abstraction:

```java
public interface Provider extends Closeable {
    int read(char[] buffer, int offset, int length) throws IOException;
}
```

It mirrors `java.io.Reader`: fill `buffer` with up to `length` characters starting at `offset`, and
return the count, or `-1` at end of input. Because the parser talks only to this interface, you can
feed it from a string, a stream, a file, or a source of your own.

## Built-in providers

Two implementations are generated for you.

### `StringProvider` — parse a string

```java
Parser parser = new Parser(new StringProvider("{{}}"));
parser.Input();
```

`StringProvider` wraps an in-memory `String`. It is the simplest way to parse text you already hold,
and the right choice in restricted environments (e.g. GWT) where streams are unavailable.

### `StreamProvider` — parse a stream

`StreamProvider` adapts a `Reader` or an `InputStream`, with an optional charset:

```java
// From standard input, platform default decoding:
new StreamProvider(System.in);

// From a file with an explicit encoding:
new StreamProvider(new FileInputStream("input.txt"), "UTF-8");

// From any Reader you already have:
new StreamProvider(myReader);
```

When you construct it from a raw `InputStream`, bytes are decoded to characters with the charset you
name (or the platform default if you name none). **Decode at the boundary**: pick the encoding here,
so the token manager only ever deals with characters, never bytes.

## Encodings and Unicode

The token manager works on Java `char`s, so it handles full Unicode input. Character classes in token
definitions may use Unicode escapes and ranges directly, which is how identifier tokens admit
non-ASCII letters:

```
TOKEN =
  < IDENTIFIER: <LETTER> (<LETTER> | <DIGIT>)* >
| < #LETTER: ["a"-"z", "A"-"Z", "_", "À"-"ÿ"] >
| < #DIGIT:  ["0"-"9"] >
;
```

Two rules of thumb:

- Choose the **encoding once**, when you build the `StreamProvider`. Everything downstream is
  characters.
- Write patterns against **characters**, using `\uXXXX` escapes for the ranges you need; do not try to
  match raw bytes.

## Re-parsing and reuse

A `Provider` is a one-shot character source: once consumed, create a fresh provider (and a fresh
parser) for the next input. Providers are `Closeable`; close them (or use try-with-resources) so the
underlying stream is released:

```java
try (StreamProvider in = new StreamProvider(new FileInputStream(path), "UTF-8")) {
    new Parser(in).Input();
}
```

## Other targets

The `Provider` shown here is the **Java** target's input abstraction. The C++ and Rust back ends ship
their own equivalent character-source and reader types in their template sets, following the same
idea — decode to characters at the edge, then feed the token manager. The concepts on this page carry
over; the concrete class names differ per target.

Next: [Error Handling](error-handling.md).
</content>
