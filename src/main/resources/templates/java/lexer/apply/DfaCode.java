//@if(trivial)

private int jjMoveStringLiteralDfa0__suffix__() {
	return __trivialReturn__;
}
//@else
//@if(stopAtPos)

private int jjStopAtPos(int pos, int kind) {
	jjmatchedKind = kind;
	jjmatchedPos = pos;
//@if(DEBUG_TOKEN_MANAGER)
	debugStream.println("No more string literal token matches are possible.");
	debugStream.println("Currently matched the first " + (jjmatchedPos + 1) + " characters as a " + ParserConstants.tokenImage[jjmatchedKind] + " token.");
//@fi
	return pos + 1;
}
//@fi
//@apply(positions)
//@apply(startWithStates)
//@fi
