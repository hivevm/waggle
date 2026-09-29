// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

// Generated code: names follow the grammar and JavaCC, and not every item is used by every grammar.
#![allow(dead_code, non_snake_case, non_upper_case_globals, unused_imports, unused_mut)]
#![allow(unused_variables, unused_assignments, unused_parens, unreachable_code, unused_labels)]

use crate::__RUST_MODULE__::charstream::CharStream;
use crate::__RUST_MODULE__::token::{add_escapes, Token};
//@if(DEBUG_TOKEN_MANAGER)
use crate::__RUST_MODULE__::parserconstants::TOKEN_IMAGE;
//@fi
use std::cmp;
use std::fmt;

/// Input no token matches, or a lexer that went round an empty match for ever (ADR-0030). The
/// message is the Java lexer's.
#[derive(Clone, Debug)]
pub struct LexicalError {
	pub line: usize,
	pub column: usize,
	pub message: String,
}

impl fmt::Display for LexicalError {
	fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
		f.write_str(&self.message)
	}
}

impl std::error::Error for LexicalError {}

//@foreach(LOHI_BYTES)
const JJBIT_VEC__LOHI_BYTES_INDEX__: [u64; __LOHI_BYTES_LENGTH__] = [__LOHI_BYTES_VALUE__];
//@end

pub const LEX_STATE_NAMES: [&str; __STATE_NAMES_LENGTH__] = [
//@foreach(STATE_NAMES)
	"__STATE_NAMES_VALUE__",
//@end
];

pub const JJSTR_LITERAL_IMAGES: [&str; __LITERAL_IMAGES_LENGTH__] = [
//@foreach(LITERAL_IMAGES)
	"__LITERAL_IMAGE_NAME__",
//@end
];


//@apply(LEX_STATE_TABLE)
//@apply(KIND_VECTORS)

//@apply(NEXT_STATES)

//@if(DEBUG_TOKEN_MANAGER)
const STATES_FOR_STATE: &[&[&[usize]]] = &[__STATES_FOR_STATE__];
const KIND_FOR_STATE: &[&[usize]] = &[__KIND_FOR_STATE__];
//@fi

pub struct Lexer<'a> {
	cur_lex_state: i8,
	default_lex_state: i8,
	jjnew_state_cnt: usize,
	jjround: usize,
	jjmatched_pos: usize,
	jjmatched_kind: u32,

	jjrounds: [usize; __STATE_SET_SIZE__],
	jjstate_set: [usize; __STATE_SET_SIZE_2__],
	jjimage_len: usize,
	length_of_match: usize,
	/// What the lexical actions of MORE and SKIP append to, and TOKEN reads back.
	image: String,
	cur_char: u32,
	input_stream: CharStream<'a>,
//@if(HAS_LOOP)
	jjemptyLineNo: [usize; __MAX_LEX_STATES__],
	jjemptyColNo: [usize; __MAX_LEX_STATES__],
	jjbeenHere: [bool; __MAX_LEX_STATES__],
//@fi
}

impl<'a> Lexer<'a> {
	pub fn new(text: &'a str) -> Self {
		Lexer {
			cur_lex_state: __DEFAULT_LEX_STATE__,
			default_lex_state: __DEFAULT_LEX_STATE__,
			jjnew_state_cnt: 0,
			jjround: 0,
			jjmatched_pos: 0,
			jjmatched_kind: 0,
			jjrounds: [0; __STATE_SET_SIZE__],
			jjstate_set: [0; __STATE_SET_SIZE_2__],
			jjimage_len: 0,
			length_of_match: 0,
			image: String::new(),
			cur_char: 0,
			input_stream: CharStream::new(text),
//@if(HAS_LOOP)
			jjemptyLineNo: [0; __MAX_LEX_STATES__],
			jjemptyColNo: [0; __MAX_LEX_STATES__],
			jjbeenHere: [false; __MAX_LEX_STATES__],
//@fi
		}
	}

	pub fn new_from_state(text: &'a str, lex_state: i8) -> Self {
		let mut lexer = Self::new(text);
		lexer.default_lex_state = lex_state;
		lexer.re_init_rounds();
		lexer.switch_to(lex_state);
		lexer
	}

	pub fn switch_to(&mut self, lex_state: i8) {
		if lex_state >= __STATE_COUNT__ {
			panic!(
				"Error: Ignoring invalid lexical state : {}. State unchanged.",
				lex_state
			);
		} else {
			self.cur_lex_state = lex_state;
		}
	}

	fn re_init_rounds(&mut self) {
		let mut i: usize = __STATE_SET_SIZE__;
		self.jjround = 0x80000001;
		while i > 0 {
			i -= 1;
			self.jjrounds[i] = 0x80000000;
		}
	}

	fn jj_fill_token(&self) -> Token {
//@if(KEEP_LINE_COLUMN)
		let mut begin_line: usize = 0;
		let mut end_line: usize = 0;
		let mut begin_column: usize = 0;
		let mut end_column: usize = 0;
//@fi
//@if(HAS_EMPTY_MATCH)
		let cur_token_image: String;
		// usize::MAX is what "matched nothing yet" looks like in Rust; Java writes -1.
		if self.jjmatched_pos == usize::MAX {
			cur_token_image = self.image.clone();
//@if(KEEP_LINE_COLUMN)
			begin_line = self.input_stream.get_end_line();
			end_line = begin_line;
			begin_column = self.input_stream.get_end_column();
			end_column = begin_column;
//@fi
		} else {
			// No literal is empty, so "" stands for "none" here, except for <EOF>: its image is
			// empty, as in Java. Reading it from the input gave the text before it.
			let im: &str = JJSTR_LITERAL_IMAGES[self.jjmatched_kind as usize];
			cur_token_image = if im.is_empty() && self.jjmatched_kind != 0 {
				self.input_stream.get_image()
			} else {
				im.to_string()
			};
//@if(KEEP_LINE_COLUMN)
			begin_line = self.input_stream.get_begin_line();
			begin_column = self.input_stream.get_begin_column();
			end_line = self.input_stream.get_end_line();
			end_column = self.input_stream.get_end_column();
//@fi
		}
//@else
		// No literal is empty, so "" stands for "none" here, except for <EOF>: its image is empty,
		// as in Java. Reading it from the input gave the text before it.
		let im: &str = JJSTR_LITERAL_IMAGES[self.jjmatched_kind as usize];
		let cur_token_image = if im.is_empty() && self.jjmatched_kind != 0 {
			self.input_stream.get_image()
		} else {
			im.to_string()
		};
//@if(KEEP_LINE_COLUMN)
		begin_line = self.input_stream.get_begin_line();
		begin_column = self.input_stream.get_begin_column();
		end_line = self.input_stream.get_end_line();
		end_column = self.input_stream.get_end_column();
//@fi
//@fi
//@if(KEEP_LINE_COLUMN)
		Token::new(
			self.jjmatched_kind,
			cur_token_image,
			begin_line,
			begin_column,
			end_line,
			end_column,
		)
//@else
		Token::new(self.jjmatched_kind, cur_token_image)
//@fi
	}

	pub fn get_next_token(&mut self) -> Result<Token, LexicalError> {
//@if(HAS_SPECIAL)
		// The special tokens read before the next token, which owns them.
		let mut special_tokens: Vec<Token> = Vec::new();
//@fi
		let mut matched_token: Token;
		let mut cur_pos: usize = 0;

		'EOFLoop: loop {
			let result = self.input_stream.begin_token();
			if !result.is_err() {
				self.cur_char = u32::from(result.unwrap());
			} else {
//@if(DEBUG_TOKEN_MANAGER)
				eprintln!("Returning the <EOF> token.");
//@fi
				self.jjmatched_kind = 0;
				self.jjmatched_pos = usize::MAX;
				matched_token = self.jj_fill_token();
//@if(HAS_SPECIAL)
				matched_token.special = std::mem::take(&mut special_tokens);
//@fi
			//@apply(GET_NEXT_TOKEN)
		}
	}

	// The actions fail only when an empty match repeats for ever, which Java reports as a
	// TokenMgrError out of the same place.
	fn skip_lexical_actions(&mut self, matched_token: Option<&Token>) -> Result<(), LexicalError> {
		match self.jjmatched_kind {
			//@apply(SKIP_ACTIONS)
			_ => {}
		}
		Ok(())
	}

	fn more_lexical_actions(&mut self) -> Result<(), LexicalError> {
		self.length_of_match = self.jjmatched_pos.wrapping_add(1);
		self.jjimage_len += self.length_of_match;
		match self.jjmatched_kind {
			//@apply(MORE_ACTIONS)
			_ => {}
		}
		Ok(())
	}

	fn token_lexical_actions(&mut self, matched_token: &mut Token) -> Result<(), LexicalError> {
		match self.jjmatched_kind {
			//@apply(TOKEN_ACTIONS)
			_ => {}
		}
		Ok(())
	}

//@if(DEBUG_TOKEN_MANAGER)
	/// The token kinds a bit vector of the string-literal DFA still allows.
	fn jj_kinds_for_bit_vector(&self, i: usize, vec: u64, kind_cnt: &mut usize) -> String {
		let mut names = String::new();
		if i == 0 {
			*kind_cnt = 0;
		}
		for j in 0..64 {
			if (vec & (1u64 << j)) != 0 {
				if *kind_cnt > 0 {
					names.push_str(", ");
				}
				*kind_cnt += 1;
				if (*kind_cnt % 5) == 0 {
					names.push_str("\n     ");
				}
				names.push_str(TOKEN_IMAGE[(i * 64) + j]);
			}
		}
		names
	}

	/// The token kinds the NFA states in "vec" can still lead to.
	fn jj_kinds_for_state_vector(&self, lex_state: usize, vec: &[usize], start: usize, end: usize)
		-> String {
		let mut done = vec![false; TOKEN_IMAGE.len()];
		let mut names = String::new();
		let mut cnt = 0;
		for i in start..end {
			if vec[i] == usize::MAX {
				continue;
			}
			for state in STATES_FOR_STATE[lex_state][vec[i]] {
				let kind = KIND_FOR_STATE[lex_state][*state];
				if !done[kind] {
					done[kind] = true;
					if cnt > 0 {
						names.push_str("\n     ");
					}
					cnt += 1;
					names.push_str(TOKEN_IMAGE[kind]);
				}
			}
		}
		if cnt == 0 {
			return "{  }".to_string();
		}
		format!("{{ {} }}", names)
	}
//@fi

	fn jj_check_n_add(&mut self, state: usize) {
		if self.jjrounds[state] != self.jjround {
			self.jjstate_set[self.jjnew_state_cnt] = state;
			self.jjnew_state_cnt += 1;
			self.jjrounds[state] = self.jjround;
		}
	}

	fn jj_add_states(&mut self, start: usize, end: usize) {
		let mut do_while = true;
		let mut index = start;
		while do_while {
			self.jjstate_set[self.jjnew_state_cnt] = JJNEXT_STATES[index];
			self.jjnew_state_cnt += 1;
			do_while = index != end;
			index += 1;
		}
	}

	fn jj_check_n_add_two_states(&mut self, state1: usize, state2: usize) {
		self.jj_check_n_add(state1);
		self.jj_check_n_add(state2);
	}
//@if(CHECK_NADD_STATES_DUAL_NEEDED)

	fn jj_check_n_add_states(&mut self, start: usize, end: usize) {
		let mut do_while = true;
		let mut index = start;
		while do_while {
			self.jj_check_n_add(JJNEXT_STATES[index]);
			do_while = index != end;
			index += 1;
		}
	}
//@fi
//@if(CHECK_NADD_STATES_UNARY_NEEDED)

	// Java and C++ overload jjCheckNAddStates; Rust cannot, so the two-state form has its own name.
	fn jj_check_n_add_state_pair(&mut self, start: usize) {
		self.jj_check_n_add(JJNEXT_STATES[start]);
		self.jj_check_n_add(JJNEXT_STATES[start + 1]);
	}
//@fi

	//@apply(LEX_STATES)
}

//@apply(NON_ASCII_TABLE)
