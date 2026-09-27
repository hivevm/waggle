// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseGenCPP.java, org/javacc/parser/ParseGen.java

// Generated code: names follow the grammar, and not every helper is used by every grammar.
#![allow(dead_code, non_snake_case, non_upper_case_globals, unused_imports, unused_mut)]
#![allow(unused_variables, unused_assignments, unused_parens, unreachable_code)]
#![allow(unreachable_patterns, clippy::all)]

use std::fmt;

use crate::__RUST_MODULE__::lexer::{Lexer, LexicalError};
use crate::__RUST_MODULE__::parserconstants::*;
use crate::__RUST_MODULE__::token::{add_escapes, Token};
//@if(USE_AST)
use crate::__RUST_MODULE__::node::{Node, new_node};
use crate::__RUST_MODULE__::treeconstants::TreeConstants;
use crate::__RUST_MODULE__::treestate::TreeState;
use std::rc::Rc;
//@fi

//@foreach(TOKEN_MASKS)
const JJ_LA1___TOKEN_MASKS_INDEX__: [u32; __MASK_INDEX__] = [__TOKEN_MASKS_VALUE__];
//@end

/// Why a parse failed (ADR-0030). The message is the Java parser's.
#[derive(Clone, Debug)]
pub enum ParseError {
	/// The lexer met input no token matches.
	Lexical(LexicalError),
	/// The next token is none of those the grammar allows here.
	UnexpectedToken {
		/// The token found.
		token: Token,
		/// The images of the tokens that were expected, sorted as Java sorts them.
		expected: Vec<String>,
		line: usize,
		column: usize,
		message: String,
	},
}

impl ParseError {
	/// The line of the error; 0 without KEEP_LINE_COLUMN.
	pub fn line(&self) -> usize {
		match self {
			ParseError::Lexical(error) => error.line,
			ParseError::UnexpectedToken { line, .. } => *line,
		}
	}

	/// The column of the error; 0 without KEEP_LINE_COLUMN.
	pub fn column(&self) -> usize {
		match self {
			ParseError::Lexical(error) => error.column,
			ParseError::UnexpectedToken { column, .. } => *column,
		}
	}
}

impl fmt::Display for ParseError {
	fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
		match self {
			ParseError::Lexical(error) => fmt::Display::fmt(error, f),
			ParseError::UnexpectedToken { message, .. } => f.write_str(message),
		}
	}
}

impl std::error::Error for ParseError {}

impl From<LexicalError> for ParseError {
	fn from(error: LexicalError) -> Self {
		ParseError::Lexical(error)
	}
}

/// Where a syntactic lookahead started, so that an error can scan it again for what it expected.
#[derive(Clone, Copy, Default)]
struct JJCall {
	generation: i32,
	first: usize,
	arg: i32,
}

pub struct Parser<'a> {
	lexer: Lexer<'a>,
	/// Every token read so far, in input order. The first stands before the input, as the Token
	/// Java's parser starts with; the one after a token is its "next".
	tokens: Vec<Token>,
	/// The token consumed last.
	token: usize,
	/// The first lexical error, reported by every step after it.
	jj_lexical_error: Option<LexicalError>,
//@if(USE_AST)
	jjtree: TreeState,
//@fi
	jj_scanpos: usize,
	jj_lastpos: usize,
	jj_la: i32,
	/// A lookahead scanned as far as it had to: Java unwinds with LookaheadSuccess, Rust returns
	/// through every routine with this set.
	jj_ls: bool,
	jj_looking_ahead: bool,
	jj_sem_la: bool,
	jj_gen: i32,
	jj_la1: [i32; __MASK_INDEX__],
	jj_kind: i32,
	jj_expentries: Vec<Vec<u32>>,
	jj_lasttokens: [u32; 100],
	jj_endpos: usize,
	jj_2_rtns: Vec<Vec<JJCall>>,
	jj_rescan: bool,
}

impl<'a> Parser<'a> {
	pub fn new(text: &'a str) -> Self {
		Parser::from_lexer(Lexer::new(text))
	}

	pub fn from_lexer(lexer: Lexer<'a>) -> Self {
		let mut parser = Parser {
			lexer,
			tokens: vec![Token::empty()],
			token: 0,
			jj_lexical_error: None,
//@if(USE_AST)
			jjtree: TreeState::new(),
//@fi
			jj_scanpos: 0,
			jj_lastpos: 0,
			jj_la: 0,
			jj_ls: false,
			jj_looking_ahead: false,
			jj_sem_la: false,
			jj_gen: 0,
			jj_la1: [-1; __MASK_INDEX__],
			jj_kind: -1,
			jj_expentries: Vec::new(),
			jj_lasttokens: [0; 100],
			jj_endpos: 0,
			jj_2_rtns: vec![vec![JJCall::default()]; __JJ2_INDEX__],
			jj_rescan: false,
		};
//@if(CACHE_TOKENS)
		// Java reads the first token when the parser is made; an error is reported by the first step.
		let _ = parser.jj_fetch(1);
//@fi
		parser
	}
//@if(USE_AST)

	pub fn root_node(&self) -> Option<&Rc<dyn Node>> {
		self.jjtree.root_node()
	}
//@fi

	//@invoke(DUMP_NORMALPRODUCTIONS)
	//@invoke(DUMP_LOOKAHEADS)
	//@invoke(DUMP_EXPANSIONS)
	/// Reads tokens until the one at `index` is there.
	fn jj_fetch(&mut self, index: usize) -> Result<(), LexicalError> {
		if let Some(error) = &self.jj_lexical_error {
			return Err(error.clone());
		}
		while self.tokens.len() <= index {
			match self.lexer.get_next_token() {
				Ok(token) => self.tokens.push(token),
				Err(error) => {
					self.jj_lexical_error = Some(error.clone());
					return Err(error);
				}
			}
		}
		Ok(())
	}

	/// The kind of the next token.
	fn jj_ntk(&mut self) -> Result<u32, ParseError> {
		let next = self.token + 1;
		self.jj_fetch(next)?;
		Ok(self.tokens[next].kind)
	}

	fn jj_consume_token(&mut self, kind: u32) -> Result<Token, ParseError> {
		let next = self.token + 1;
		self.jj_fetch(next)?;
//@if(CACHE_TOKENS)
		self.jj_fetch(next + 1)?;
//@fi
		if self.tokens[next].kind == kind {
			self.token = next;
			self.jj_gen += 1;
			return Ok(self.tokens[next].clone());
		}
		self.jj_kind = kind as i32;
		Err(self.jj_parse_error())
	}

	/// What a choice reports when none of its alternatives matched: Java's jj_consume_token(-1).
	fn jj_no_alternative(&mut self) -> ParseError {
		let next = self.token + 1;
		if let Err(error) = self.jj_fetch(next) {
			return error.into();
		}
//@if(CACHE_TOKENS)
		if let Err(error) = self.jj_fetch(next + 1) {
			return error.into();
		}
//@fi
		self.jj_kind = -1;
		self.jj_parse_error()
	}

	/// Scans one token of a lookahead. True means stop: the token is another kind, or the
	/// lookahead is decided and `jj_ls` is set.
	fn jj_scan_token(&mut self, kind: u32) -> bool {
		if self.jj_scanpos == self.jj_lastpos {
			self.jj_la -= 1;
			let next = self.jj_scanpos + 1;
			if self.jj_fetch(next).is_err() {
				// Reported by the jj_2 routine that started the scan.
				self.jj_ls = true;
				return true;
			}
			self.jj_scanpos = next;
			self.jj_lastpos = next;
		} else {
			self.jj_scanpos += 1;
		}
//@if(ERROR_REPORTING)
		if self.jj_rescan && self.jj_scanpos >= self.token {
			self.jj_add_error_token(kind, self.jj_scanpos - self.token);
		}
//@fi
		if self.tokens[self.jj_scanpos].kind != kind {
			return true;
		}
		if self.jj_la == 0 && self.jj_scanpos == self.jj_lastpos {
			self.jj_ls = true;
			return true;
		}
		false
	}

	/// Consumes the next token, whatever its kind.
	pub fn get_next_token(&mut self) -> Result<&Token, ParseError> {
		let next = self.token + 1;
		self.jj_fetch(next)?;
//@if(CACHE_TOKENS)
		self.jj_fetch(next + 1)?;
//@fi
		self.token = next;
		self.jj_gen += 1;
		Ok(&self.tokens[next])
	}

	/// The token `index` places after the current one; `get_token(0)` is the current one. Inside a
	/// semantic lookahead the current token is the one the lookahead has reached. A lexical error
	/// yields the last token read, and the next step reports the error.
	pub fn get_token(&mut self, index: usize) -> &Token {
		let start = if self.jj_looking_ahead { self.jj_scanpos } else { self.token };
		let mut at = start + index;
		if self.jj_fetch(at).is_err() {
			at = self.tokens.len() - 1;
		}
		&self.tokens[at]
	}

	/// The lexer, for a lexical action that switches its state.
	pub fn lexer(&mut self) -> &mut Lexer<'a> {
		&mut self.lexer
	}
//@if(ERROR_REPORTING)

	fn jj_add_error_token(&mut self, kind: u32, pos: usize) {
		if pos >= 100 {
			return;
		}
		if pos == self.jj_endpos + 1 {
			self.jj_lasttokens[self.jj_endpos] = kind;
			self.jj_endpos += 1;
		} else if self.jj_endpos != 0 {
			let entry = self.jj_lasttokens[..self.jj_endpos].to_vec();
			// As in Java, which adds the sequence only when it is there already.
			if self.jj_expentries.iter().any(|old| *old == entry) {
				self.jj_expentries.push(entry);
			}
			if pos != 0 {
				self.jj_endpos = pos;
				self.jj_lasttokens[pos - 1] = kind;
			}
		}
	}

	/// The error at the next token, with what the grammar expected there.
	fn jj_parse_error(&mut self) -> ParseError {
		if let Err(error) = self.jj_fetch(self.token + 1) {
			return error.into();
		}
		self.jj_expentries.clear();
		let mut la1tokens = [false; __TOKEN_COUNT__];
		if self.jj_kind >= 0 {
			la1tokens[self.jj_kind as usize] = true;
			self.jj_kind = -1;
		}
		for i in 0..__MASK_INDEX__ {
			if self.jj_la1[i] == self.jj_gen {
				for j in 0..32 {
//@foreach(TOKEN_MASKS_LA1)
					if (JJ_LA1___TOKEN_MASKS_LA1_INDEX__[i] & (1u32 << j)) != 0 {
						la1tokens[__TOKEN_MASKS_LA1_VALUE__j] = true;
					}
//@end
				}
			}
		}
		for i in 0..__TOKEN_COUNT__ {
			if la1tokens[i] {
				self.jj_expentries.push(vec![i as u32]);
			}
		}
//@if(JJ2_INDEX)
		self.jj_endpos = 0;
		self.jj_rescan_token();
		self.jj_add_error_token(0, 0);
//@fi
		let expected = std::mem::take(&mut self.jj_expentries);
		self.jj_unexpected_token(expected)
	}

	/// The Java ParseException's message for the next token and the sequences expected there.
	fn jj_unexpected_token(&self, sequences: Vec<Vec<u32>>) -> ParseError {
		let mut max_size = 0;
		let mut expected: Vec<String> = Vec::new();
		for sequence in &sequences {
			max_size = max_size.max(sequence.len());
			for kind in sequence {
				expected.push(TOKEN_IMAGE[*kind as usize].to_string());
			}
		}
		// A Java TreeSet of strings: ordered by UTF-16 unit, without duplicates.
		expected.sort_by(|a, b| a.encode_utf16().cmp(b.encode_utf16()));
		expected.dedup();

		let found = self.token + 1;
		let mut message = String::from("Encountered unexpected token:");
		let mut at = found;
		for i in 0..max_size {
			if at >= self.tokens.len() {
				break;
			}
			let token = &self.tokens[at];
			if i != 0 {
				message.push(' ');
			}
			if token.kind == 0 {
				message.push_str(TOKEN_IMAGE[0]);
				break;
			}
			message.push_str(&format!(" \"{}\" {}", add_escapes(&token.image), TOKEN_IMAGE[token.kind as usize]));
			at += 1;
		}
//@if(KEEP_LINE_COLUMN)
		let (line, column) = (self.tokens[found].begin_line, self.tokens[found].begin_column);
		message.push_str(&format!("\n    at line {}, column {}", line, column));
//@else
		let (line, column) = (0, 0);
//@fi
		message.push_str(".\n");
		if !sequences.is_empty() {
			message.push_str(if sequences.len() == 1 {
				"\nWas expecting:\n\n"
			} else {
				"\nWas expecting one of:\n\n"
			});
			for image in &expected {
				message.push_str("    ");
				message.push_str(image);
				message.push('\n');
			}
		}
		ParseError::UnexpectedToken {
			token: self.tokens[found].clone(),
			expected,
			line,
			column,
			message,
		}
	}
//@else

	/// The error at the next token. Without ERROR_REPORTING nothing records what was expected.
	fn jj_parse_error(&mut self) -> ParseError {
		let found = self.token + 1;
		if let Err(error) = self.jj_fetch(found) {
			return error.into();
		}
		let token = self.tokens[found].clone();
		let mess = if token.kind == 0 { TOKEN_IMAGE[0].to_string() } else { token.image.clone() };
//@if(KEEP_LINE_COLUMN)
		let (line, column) = (token.begin_line, token.begin_column);
		let message = format!("Parse error at line {}, column {}.  Encountered: {}", line, column, mess);
//@else
		let (line, column) = (0, 0);
		let message = format!("Parse error at <unknown location>.  Encountered: {}", mess);
//@fi
		ParseError::UnexpectedToken {
			token,
			expected: Vec::new(),
			line,
			column,
			message,
		}
	}
//@fi
//@if(JJ2_INDEX)
//@if(ERROR_REPORTING)

	/// Runs the syntactic lookaheads that are still current again, recording what they expected.
	fn jj_rescan_token(&mut self) {
		self.jj_rescan = true;
		for i in 0..__JJ2_INDEX__ {
			for k in 0..self.jj_2_rtns[i].len() {
				let p = self.jj_2_rtns[i][k];
				if p.generation > self.jj_gen {
					self.jj_la = p.arg;
					self.jj_lastpos = p.first;
					self.jj_scanpos = p.first;
					match i {
//@foreach(JJ2_OFFSET)
						__JJ2_OFFSET_INDEX__ => {
							self.jj_3___JJ2_OFFSET_VALUE__();
						}
//@end
						_ => {}
					}
					if self.jj_ls {
						// Java's LookaheadSuccess leaves the rest of this routine's calls.
						self.jj_ls = false;
						break;
					}
				}
			}
		}
		self.jj_rescan = false;
	}

	fn jj_save(&mut self, index: usize, xla: i32) {
		let current = self.jj_gen;
		let call = JJCall {
			generation: current.wrapping_add(xla).wrapping_sub(self.jj_la),
			first: self.token,
			arg: xla,
		};
		let calls = &mut self.jj_2_rtns[index];
		match calls.iter().position(|p| p.generation <= current) {
			Some(k) => calls[k] = call,
			None => calls.push(call),
		}
	}
//@fi
//@fi
//@if(USE_AST)

	pub fn jjtree_open_node_scope(&self, node: &dyn Node) {
	}

	pub fn jjtree_close_node_scope(&self, node: &dyn Node) {
	}
//@fi
}
