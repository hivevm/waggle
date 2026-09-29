matched_token = self.jj_fill_token();
//@if(special)
matched_token.special = std::mem::take(&mut special_tokens);
//@fi
//@if(tokenActions)
self.token_lexical_actions(&mut matched_token)?;
//@fi
//@if(newLexState)
if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {
   self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];
}
//@fi
return Ok(matched_token);
