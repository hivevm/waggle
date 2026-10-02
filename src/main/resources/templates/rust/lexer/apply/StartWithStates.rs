
fn jjStartNfaWithStates__suffix__(&mut self, pos: usize, kind: u32, state: usize) -> usize {
	self.jjmatched_kind = kind;
	self.jjmatched_pos = pos;
//@if(DEBUG_TOKEN_MANAGER)
	eprintln!("No more string literal token matches are possible.");
	eprintln!("   Currently matched the first {} characters as a {} token.", self.jjmatched_pos.wrapping_add(1), TOKEN_IMAGE[self.jjmatched_kind as usize]);
//@fi
	match self.input_stream.read_char() {
	    Ok(c) => self.cur_char = u32::from(c),
	    Err(_) => return pos + 1,
	}
//@if(DEBUG_TOKEN_MANAGER)
//@if(SWITCH_ON_LEX_STATE)
	eprintln!("<{}>Current character : {}({}) at line {} column {}", LEX_STATE_NAMES[self.cur_lex_state as usize], char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char, self.input_stream.get_end_line(), self.input_stream.get_end_column());
//@else
	eprintln!("Current character : {}({}) at line {} column {}", char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char, self.input_stream.get_end_line(), self.input_stream.get_end_column());
//@fi
//@fi
	return self.jj_move_nfa__suffix__(state, pos + 1);
}
