
int __PARSER_NAME__TokenManager::jjStopStringLiteralDfa__suffix__(int pos, __activeParams__) {
//@if(DEBUG_TOKEN_MANAGER)
	fprintf(debugStream, "   No more string literal token matches are possible.");
//@fi
	switch (pos) {
		//@apply(positions)
		default:
			return __noState__;
	}
}

int __PARSER_NAME__TokenManager::jjStartNfa__suffix__(int pos, __activeParams__) {
    return jjMoveNfa__suffix__(jjStopStringLiteralDfa__suffix__(pos, __arguments__), pos + 1);
}
