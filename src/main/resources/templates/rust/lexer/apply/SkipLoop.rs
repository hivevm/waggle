while __condition__ {
//@if(DEBUG_TOKEN_MANAGER)
//@if(withLexState)
	eprintln!("<{}>Skipping character : {}({})", LEX_STATE_NAMES[self.cur_lex_state as usize], char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char);
//@else
	eprintln!("Skipping character : {}({})", char::from_u32(self.cur_char).unwrap_or('\u{fffd}'), self.cur_char);
//@fi
//@fi
	match self.input_stream.begin_token() {
	    Ok(c) => self.cur_char = u32::from(c),
	    Err(_) => continue 'EOFLoop,
	}
}
