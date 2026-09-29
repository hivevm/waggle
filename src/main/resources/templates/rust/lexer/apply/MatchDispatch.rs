if self.jjmatched_kind != 0x7fffffff {
	if self.jjmatched_pos.wrapping_add(1) < cur_pos {
//@if(DEBUG_TOKEN_MANAGER)
		eprintln!("   Putting back {} characters into the input stream.", cur_pos - self.jjmatched_pos.wrapping_add(1));
//@fi
		self.input_stream.backup(cur_pos - self.jjmatched_pos.wrapping_add(1));
	}
//@if(DEBUG_TOKEN_MANAGER)
	eprintln!("****** FOUND A {} MATCH ({}) ******\n", TOKEN_IMAGE[self.jjmatched_kind as usize], self.input_stream.get_suffix(self.jjmatched_pos.wrapping_add(1)));
//@fi
//@if(tokenTest)
	if (JJTO_TOKEN[(self.jjmatched_kind >> 6) as usize] & (1u64 << (self.jjmatched_kind & 0o77))) != 0 {
		//@apply(token)
	}
	//@apply(skip)
	//@apply(more)
//@else
	//@apply(token)
//@fi
}
let mut error_line = self.input_stream.get_end_line();
let mut error_column = self.input_stream.get_end_column();
let eof_seen = self.input_stream.read_char().is_err();
if eof_seen {
    if self.cur_char == '\n' as u32 || self.cur_char == '\r' as u32 {
        error_line += 1;
        error_column = 0;
    } else {
        error_column += 1;
    }
} else {
    // Back over the character just read and the one that failed, as Java does.
    self.input_stream.backup(1);
    self.input_stream.backup(1);
}
let error_after = if cur_pos <= 1 {
    String::new()
} else {
    self.input_stream.get_image()
};
// A value, not a panic (ADR-0030); it used to be Token::empty(), which is <EOF>,
// so the rest of the input was silently dropped. The message is the Java lexer's.
let encountered = if eof_seen {
    String::from("<EOF> ")
} else {
    let c = char::from_u32(self.cur_char).unwrap_or(char::REPLACEMENT_CHARACTER);
    format!("\"{}\" ({}), ", add_escapes(&c.to_string()), self.cur_char)
};
return Err(LexicalError {
    line: error_line,
    column: error_column,
    message: format!(
        "Lexical error at line {}, column {}.  Encountered: {}after : \"{}\"",
        error_line, error_column, encountered, add_escapes(&error_after)
    ),
});

