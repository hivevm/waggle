//@if(switchOnLexState)
match self.cur_lex_state {
	//@apply(states)
	_ => {}
}
//@else
//@apply(states)
//@fi
//@if(noLexState)
self.jjmatched_kind = 0x7fffffff;
//@fi
//@apply(dispatch)
