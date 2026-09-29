
private final int jjStopStringLiteralDfa__suffix__(int pos, __activeParams__) {
//@if(DEBUG_TOKEN_MANAGER)
	debugStream.println("   No more string literal token matches are possible.");
//@fi
	switch (pos) {
		//@apply(positions)
		default:
			return __noState__;
	}
}

private final int jjStartNfa__suffix__(int pos, __activeParams__) {
    return jjMoveNfa__suffix__(jjStopStringLiteralDfa__suffix__(pos, __arguments__), pos + 1);
}
