// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

// Generated code: names follow the grammar and JavaCC, and not every item is used by every grammar.
#![allow(dead_code, non_snake_case, non_upper_case_globals, unused_imports, unused_mut)]
#![allow(unused_variables, unused_assignments, unused_parens, unreachable_code)]

pub const EOF: u32 = 0;

// RegularExpression Ids
//@foreach(TOKENS)
pub const __TOKENS_LABEL__: u32 = __TOKENS_ORDINAL__;
//@end

// Lexical states
//@foreach(STATES)
pub const __STATES_NAME__: i8 = __STATES_INDEX__; // as Lexer::switch_to takes it
//@end

pub const TOKEN_IMAGE: [&str; __REXPRESSION_COUNT__] = [
//@foreach(REXPRESSION_COUNT)
	__REXPRESSION_LABEL__
//@end
];