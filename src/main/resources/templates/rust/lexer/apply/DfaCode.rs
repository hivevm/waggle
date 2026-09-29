//@if(trivial)

fn jj_move_string_literal_dfa0__suffix__(&mut self) -> usize {
	return __trivialReturn__;
}
//@else
//@if(stopAtPos)
fn jj_stop_at_pos(&mut self, pos: usize, kind: u32) -> usize {
	self.jjmatched_kind = kind;
	self.jjmatched_pos = pos;
//@if(DEBUG_TOKEN_MANAGER)
	eprintln!("No more string literal token matches are possible.");
	eprintln!("   Currently matched the first {} characters as a {} token.", self.jjmatched_pos.wrapping_add(1), TOKEN_IMAGE[self.jjmatched_kind as usize]);
//@fi
	pos + 1
}
//@fi
//@apply(positions)
//@apply(startWithStates)
//@fi
