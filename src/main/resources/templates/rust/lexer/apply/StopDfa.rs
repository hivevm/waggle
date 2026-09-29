
fn jjStopStringLiteralDfa__suffix__(&mut self, pos: usize, __activeParams__) -> usize {
//@if(DEBUG_TOKEN_MANAGER)
	eprintln!("   No more string literal token matches are possible.");
//@fi
	match pos {
		//@apply(positions)
		_ => return __noState__,
	}
}

fn jjStartNfa__suffix__(&mut self, pos: usize, __activeParams__) -> usize {
    let state = self.jjStopStringLiteralDfa__suffix__(pos, __arguments__);
    return self.jj_move_nfa__suffix__(state, pos + 1);
}
