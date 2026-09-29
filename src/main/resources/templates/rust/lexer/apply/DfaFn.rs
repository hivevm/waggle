fn jj_move_string_literal_dfa__pos____suffix__(&mut self__params__) -> usize {
//@if(activeCheck)
	//@apply(lets)
	if (__activeTest__) == 0 {
		return __checkReturn__;
	}
//@fi
//@if(readChar)
//@if(DEBUG_TOKEN_MANAGER)
	//@apply(debugMatches)
//@fi
	let result = self.input_stream.read_char();
	if result.is_err() {
//@if(eofStartNfa)
		__stopCall__;
//@if(DEBUG_TOKEN_MANAGER)
		if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x7fffffff {
		   eprintln!("   Currently matched the first {} characters as a {} token.", self.jjmatched_pos.wrapping_add(1), TOKEN_IMAGE[self.jjmatched_kind as usize]);
		}
//@fi
		return __pos__;
//@else
		return __eofReturn__;
//@fi
	}
	self.cur_char = u32::from(result.unwrap());

//@if(DEBUG_TOKEN_MANAGER)
//@if(withLexState)
	eprintln!("<{}>Current character : {}({}) at line {} column {}", LEX_STATE_NAMES[self.cur_lex_state as usize], char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char, self.input_stream.get_end_line(), self.input_stream.get_end_column());
//@else
	eprintln!("Current character : {}({}) at line {} column {}", char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char, self.input_stream.get_end_line(), self.input_stream.get_end_column());
//@fi
//@fi
//@fi
	match self.cur_char {
		//@apply(cases)
		_ => {
//@if(DEBUG_TOKEN_MANAGER)
			eprintln!("No string literal matches possible.");
//@fi
			//@apply(otherwise)
		}
	}
//@if(tail)
//@if(tailStartNfa)
	__tailStartCall__
//@else
//@if(tailMoveNfa)
	__tailMoveCall__
//@else
	return __tailReturn__
//@fi
//@fi
//@fi
}

