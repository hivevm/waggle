
fn jj_move_nfa__suffix__(&mut self, start_state: usize, mut cur_pos: usize) -> usize {
//@if(mixed)
	let str_kind = self.jjmatched_kind;
	let str_pos = self.jjmatched_pos;
	let seen_upto: usize = cur_pos + 1;
	self.input_stream.backup(seen_upto);
	let result = self.input_stream.read_char();
	if result.is_err() {
	    panic!("Internal Error");
	}
	self.cur_char = u32::from(result.unwrap());
	let mut cur_pos: usize = 0;
//@fi
	let mut starts_at: usize = 0;
	self.jjnew_state_cnt = __generatedStates__;
	let mut i: usize = 1;
	self.jjstate_set[0] = start_state;
//@if(DEBUG_TOKEN_MANAGER)
	eprintln!("   Starting NFA to match one of : {}", self.jj_kinds_for_state_vector(self.cur_lex_state as usize, &self.jjstate_set, 0, 1));
//@if(SWITCH_ON_LEX_STATE)
	eprintln!("<{}>Current character : {}({}) at line {} column {}", LEX_STATE_NAMES[self.cur_lex_state as usize], char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char, self.input_stream.get_end_line(), self.input_stream.get_end_column());
//@else
	eprintln!("Current character : {}({}) at line {} column {}", char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char, self.input_stream.get_end_line(), self.input_stream.get_end_column());
//@fi
//@fi
	let mut kind: u32 = 0x7fffffff;
	loop {
		self.jjround += 1;
		if self.jjround == 0x7fffffff {
		    self.re_init_rounds();
		}
		if self.cur_char < 64 {
			//@apply(low)
		} else if self.cur_char < 128 {
			//@apply(high)
		} else {
			//@apply(other)
		}
		if kind != 0x7fffffff {
		   self.jjmatched_kind = kind;
		   self.jjmatched_pos = cur_pos;
		   kind = 0x7fffffff;
		}
		cur_pos += 1;
//@if(DEBUG_TOKEN_MANAGER)
		if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x7fffffff {
		   eprintln!("   Currently matched the first {} characters as a {} token.", self.jjmatched_pos.wrapping_add(1), TOKEN_IMAGE[self.jjmatched_kind as usize]);
		}
//@fi
		i = self.jjnew_state_cnt;
		self.jjnew_state_cnt = starts_at;
		starts_at = __generatedStates__ - self.jjnew_state_cnt;
		if i == starts_at {
//@if(mixed)
		    break;
//@else
		    return cur_pos;
//@fi
		}
//@if(DEBUG_TOKEN_MANAGER)
		eprintln!("   Possible kinds of longer matches : {}", self.jj_kinds_for_state_vector(self.cur_lex_state as usize, &self.jjstate_set, starts_at, i));
//@fi
		let result = self.input_stream.read_char();
		if result.is_err() {
//@if(mixed)
		    break;
//@else
		    return cur_pos;
//@fi
		}
		self.cur_char = u32::from(result.unwrap());
//@if(DEBUG_TOKEN_MANAGER)
//@if(SWITCH_ON_LEX_STATE)
		eprintln!("<{}>Current character : {}({}) at line {} column {}", LEX_STATE_NAMES[self.cur_lex_state as usize], char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char, self.input_stream.get_end_line(), self.input_stream.get_end_column());
//@else
		eprintln!("Current character : {}({}) at line {} column {}", char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char, self.input_stream.get_end_line(), self.input_stream.get_end_column());
//@fi
//@fi
	}
//@if(mixed)
	if self.jjmatched_pos > str_pos {
	   return cur_pos;
	}

	let to_ret = cmp::max(cur_pos, seen_upto);
	if cur_pos < to_ret {
	    let mut i = to_ret - cmp::min(cur_pos, seen_upto); // as in Java and C++
	    while i > 0 {
	        let result = self.input_stream.read_char();
	        if result.is_err() {
	            panic!("Internal Error : Please send a bug report.");
	        }
	        self.cur_char = u32::from(result.unwrap());
	        i -= 1;
	    }
	}
	if self.jjmatched_pos < str_pos {
	    self.jjmatched_kind = str_kind;
	    self.jjmatched_pos = str_pos;
	} else if self.jjmatched_pos == str_pos && self.jjmatched_kind > str_kind {
	    self.jjmatched_kind = str_kind;
	}

	to_ret
//@fi
}
