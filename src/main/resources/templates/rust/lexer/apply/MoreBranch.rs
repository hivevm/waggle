//@if(moreActions)
self.more_lexical_actions()?;
//@else
//@if(moreImageLen)
self.jjimage_len += self.jjmatched_pos.wrapping_add(1);
//@fi
//@fi
//@if(newLexState)
if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {
   self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];
}
//@fi
cur_pos = 0;
self.jjmatched_kind = 0x7fffffff;
match self.input_stream.read_char() {
	Ok(c) => {
	    self.cur_char = u32::from(c);
	    continue;
	}
	Err(_) => {}
}
