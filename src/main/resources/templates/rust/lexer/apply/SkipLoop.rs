//@if(both)
while (self.cur_char < 64 && (__lowerMask__u64 & (1u64 << self.cur_char)) != 0) || ((self.cur_char >> 6) == 1 && (__upperMask__u64 & (1u64 << (self.cur_char & 0o77))) != 0) {
//@elif(upperOnly)
while self.cur_char > 63 && self.cur_char <= __maxChar__ && (__upperMask__u64 & (1u64 << (self.cur_char & 0o77))) != 0 {
//@else
while self.cur_char <= __maxChar__ && (__lowerMask__u64 & (1u64 << self.cur_char)) != 0 {
//@fi
//@if(DEBUG_TOKEN_MANAGER)
//@if(SWITCH_ON_LEX_STATE)
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
