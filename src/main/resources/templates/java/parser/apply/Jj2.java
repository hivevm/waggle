private boolean jj_2__name__(int xla) {
	jj_la = xla;
	jj_lastpos = jj_scanpos = token;
//@if(DEPTH_LIMIT)
	jj_depth_error = false;
//@fi
	try {
//@if(DEPTH_LIMIT)
		return (!jj_3__name__() && !jj_depth_error);
//@else
		return (!jj_3__name__());
//@fi
	} catch (LookaheadSuccess ls) {
		return true;
//@if(RECORDS_EXPECTED_TOKENS)
	} finally {
		jj_save(__saveSlot__, xla);
//@fi
	}
}

