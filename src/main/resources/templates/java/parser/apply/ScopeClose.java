
} finally {
	if (__closedVar__) {
		//@apply(close)

//@if(scopeHook)
		if (jjtree.nodeCreated()) {
			jjtreeCloseNodeScope(__nodeVar__);
		}
//@fi
//@if(trackTokens)
		__nodeVar__.jjtSetLastToken(getToken(0));
//@fi
	}
}