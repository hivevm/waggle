//@if(moreBranch)
else if (JJTO_SKIP[(self.jjmatched_kind >> 6) as usize] & (1u64 << (self.jjmatched_kind & 0o77))) != 0 {
//@else
else {
//@fi
//@if(special)
	if (JJTO_SPECIAL[(self.jjmatched_kind >> 6) as usize] & (1u64 << (self.jjmatched_kind & 0o77))) != 0 {
		let token = self.jj_fill_token();
//@if(skipActions)
		self.skip_lexical_actions(Some(&token))?;
//@fi
		special_tokens.push(token);
//@if(skipActions)
	} else {
	    self.skip_lexical_actions(None)?;
	}
//@else
	}
//@fi
//@else
//@if(skipActions)
	self.skip_lexical_actions(None)?;
//@fi
//@fi
//@if(newLexState)
	if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {
	   self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];
	}
//@fi
	continue 'EOFLoop;
}
