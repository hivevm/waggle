// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

// Generated code: names follow the grammar and JavaCC, and not every item is used by every grammar.
#![allow(dead_code, non_snake_case, non_upper_case_globals, unused_imports, unused_mut)]
#![allow(unused_variables, unused_assignments, unused_parens, unreachable_code)]

/// A token the lexer read (ADR-0030).
///
/// The special tokens before it are its own: Java links them into a chain through `specialToken`
/// and `next`, which Rust would have to share through `Rc<RefCell<…>>`.
#[derive(Clone, Debug, Default)]
pub struct Token {
	pub kind: u32,
	pub image: String,
	/// The SPECIAL_TOKENs read before this token, in input order.
	pub special: Vec<Token>,
//@if(KEEP_LINE_COLUMN)
	pub begin_line: usize,
	pub begin_column: usize,
	pub end_line: usize,
	pub end_column: usize,
//@fi
}

impl Token {
//@if(KEEP_LINE_COLUMN)
	pub fn new(
		kind: u32,
		image: String,
		begin_line: usize,
		begin_column: usize,
		end_line: usize,
		end_column: usize,
	) -> Token {
		Token {
			kind,
			image,
			special: Vec::new(),
			begin_line,
			begin_column,
			end_line,
			end_column,
		}
	}
//@else
	pub fn new(kind: u32, image: String) -> Token {
		Token {
			kind,
			image,
			special: Vec::new(),
		}
	}
//@fi

	pub fn empty() -> Token {
		Token::default()
	}
}

/// The text with the characters a message cannot show escaped, as Java's addEscapes writes them:
/// the usual backslash escapes, and every UTF-16 unit outside printable ASCII as \uXXXX.
pub fn add_escapes(text: &str) -> String {
	let mut escaped = String::new();
	for unit in text.encode_utf16() {
		match unit {
			0x08 => escaped.push_str("\\b"),
			0x09 => escaped.push_str("\\t"),
			0x0a => escaped.push_str("\\n"),
			0x0c => escaped.push_str("\\f"),
			0x0d => escaped.push_str("\\r"),
			0x22 => escaped.push_str("\\\""),
			0x27 => escaped.push_str("\\'"),
			0x5c => escaped.push_str("\\\\"),
			0x20..=0x7e => escaped.push(unit as u8 as char),
			_ => escaped.push_str(&format!("\\u{:04x}", unit)),
		}
	}
	escaped
}
