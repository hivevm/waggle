//@if(hasSkip)
//@apply(skip)
//@fi
//@if(matchesEmpty)
//@if(DEBUG_TOKEN_MANAGER)
eprintln!("   Matched the empty string as {} token.", TOKEN_IMAGE[__emptyMatch__]);
//@fi
self.jjmatched_kind = __emptyMatch__;
self.jjmatched_pos = usize::MAX;
cur_pos = 0;
//@else
self.jjmatched_kind = 0x7fffffff;
self.jjmatched_pos = 0;
//@fi
//@if(DEBUG_TOKEN_MANAGER)
//@if(withLexState)
eprintln!("<{}>Current character : {}({}) at line {} column {}", LEX_STATE_NAMES[self.cur_lex_state as usize], char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char, self.input_stream.get_end_line(), self.input_stream.get_end_column());
//@else
eprintln!("Current character : {}({}) at line {} column {}", char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char, self.input_stream.get_end_line(), self.input_stream.get_end_column());
//@fi
//@fi
cur_pos = self.jj_move_string_literal_dfa0___index__();
//@if(anyChar)
//@if(matchesEmpty)
if self.jjmatched_pos == usize::MAX || (self.jjmatched_pos == 0 && self.jjmatched_kind > __anyCharKind__) {
//@else
if self.jjmatched_pos == 0 && self.jjmatched_kind > __anyCharKind__ {
//@fi
//@if(DEBUG_TOKEN_MANAGER)
	eprintln!("   Current character matched as a {} token.", TOKEN_IMAGE[__anyCharKind__]);
//@fi
	self.jjmatched_kind = __anyCharKind__;
//@if(matchesEmpty)
	self.jjmatched_pos = 0;
//@fi
}
//@fi
