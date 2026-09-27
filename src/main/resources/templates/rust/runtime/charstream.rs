// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: src/main/resources/templates/JavaCharStream.template

// Generated code: names follow the grammar and JavaCC, and not every item is used by every grammar.
#![allow(dead_code, non_snake_case, non_upper_case_globals, unused_imports, unused_mut)]
#![allow(unused_variables, unused_assignments, unused_parens, unreachable_code)]

use std::marker::PhantomData;

/// The input of the lexer, read one character at a time.
///
/// The input is a string in memory, so it is decoded once and read through an index. This used
/// to be a port of the Java ring buffer that never saw the end of its input, read the input again
/// from the start after 4096 characters and cut token images at the wrong places.
pub struct CharStream<'a> {
	chars: Vec<char>,
	/// The character read last, -1 before the first.
	pos: isize,
	token_begin: usize,
	tab_size: usize,
//@if(KEEP_LINE_COLUMN)
	/// Line and column of every character read so far, 1-based.
	lines: Vec<usize>,
	columns: Vec<usize>,
	line: usize,
	column: usize,
	prev_char_is_cr: bool,
	prev_char_is_lf: bool,
//@fi
	input: PhantomData<&'a str>,
}

impl<'a> CharStream<'a> {
	pub fn new(text: &'a str) -> Self {
		CharStream {
			chars: text.chars().collect(),
			pos: -1,
			token_begin: 0,
			tab_size: 1,
//@if(KEEP_LINE_COLUMN)
			lines: Vec::new(),
			columns: Vec::new(),
			line: 1,
			column: 0,
			prev_char_is_cr: false,
			prev_char_is_lf: false,
//@fi
			input: PhantomData,
		}
	}

	/// Reads the first character of a token.
	pub fn begin_token(&mut self) -> Result<char, std::io::Error> {
		match self.read_char() {
			Ok(c) => {
				self.token_begin = self.pos as usize;
				Ok(c)
			}
			Err(error) => {
				// <EOF> begins at the last character, as in Java.
				if self.pos >= 0 {
					self.token_begin = self.pos as usize;
				}
				Err(error)
			}
		}
	}

	/// Reads the next character, or fails at the end of the input, where the position stays.
	pub fn read_char(&mut self) -> Result<char, std::io::Error> {
		let next = (self.pos + 1) as usize;
		if next >= self.chars.len() {
			return Err(std::io::Error::new(std::io::ErrorKind::UnexpectedEof, "end of input"));
		}
		self.pos += 1;
		let c = self.chars[next];
//@if(KEEP_LINE_COLUMN)
		if next == self.lines.len() {
			self.update_line_column(c);
		}
//@fi
		// The automaton knows the characters up to U+FFFF: its tables are indexed by the high byte
		// of a 16-bit character, and a code point beyond read past their end. It is read as U+FFFD,
		// as in C++; the image keeps the character.
		Ok(if (c as u32) > 0xffff { '\u{fffd}' } else { c })
	}

	/// Steps back by `amount` characters, to be read again.
	pub fn backup(&mut self, amount: usize) {
		self.pos -= amount as isize;
	}

	/// The characters of the current token.
	pub fn get_image(&self) -> String {
		if self.pos < self.token_begin as isize {
			return String::new();
		}
		self.chars[self.token_begin..=(self.pos as usize)].iter().collect()
	}

	/// The last `len` characters read.
	pub fn get_suffix(&self, len: usize) -> String {
		let end = (self.pos + 1) as usize;
		self.chars[(end - len)..end].iter().collect()
	}

	pub fn get_tab_size(&self) -> usize {
		self.tab_size
	}

	pub fn set_tab_size(&mut self, size: usize) {
		self.tab_size = size;
	}
//@if(KEEP_LINE_COLUMN)

	fn update_line_column(&mut self, c: char) {
		self.column += 1;

		if self.prev_char_is_lf {
			self.prev_char_is_lf = false;
			self.column = 1;
			self.line += 1;
		} else if self.prev_char_is_cr {
			self.prev_char_is_cr = false;
			if c == '\n' {
				self.prev_char_is_lf = true;
			} else {
				self.column = 1;
				self.line += 1;
			}
		}

		match c {
			'\r' => self.prev_char_is_cr = true,
			'\n' => self.prev_char_is_lf = true,
			'\t' => {
				self.column -= 1;
				self.column += self.tab_size - (self.column % self.tab_size);
			}
			_ => {}
		}

		self.lines.push(self.line);
		self.columns.push(self.column);
	}

	pub fn get_begin_line(&self) -> usize {
		if self.lines.is_empty() { self.line } else { self.lines[self.token_begin] }
	}

	pub fn get_begin_column(&self) -> usize {
		if self.columns.is_empty() { self.column } else { self.columns[self.token_begin] }
	}
//@fi

	/// Line of the character read last; line 1 before any was read, as in Java. Without
	/// KEEP_LINE_COLUMN there is none: 0, where Java has -1. The lexical error reports it.
	pub fn get_end_line(&self) -> usize {
//@if(KEEP_LINE_COLUMN)
		if self.pos < 0 { self.line } else { self.lines[self.pos as usize] }
//@else
		0
//@fi
	}

	/// Column of the character read last; column 0 before any was read, as in Java.
	pub fn get_end_column(&self) -> usize {
//@if(KEEP_LINE_COLUMN)
		if self.pos < 0 { self.column } else { self.columns[self.pos as usize] }
//@else
		0
//@fi
	}
}
