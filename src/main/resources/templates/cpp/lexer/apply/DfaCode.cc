//@if(trivial)

int __PARSER_NAME__TokenManager::jjMoveStringLiteralDfa0__suffix__() {return __trivialReturn__;
}
//@else
//@if(stopAtPos)
int __PARSER_NAME__TokenManager::jjStopAtPos(int pos, int kind) {
	jjmatchedKind = kind;
	jjmatchedPos = pos;
//@if(DEBUG_TOKEN_MANAGER)
	fprintf(debugStream, "No more string literal token matches are possible.");
	fprintf(debugStream, "Currently matched the first %d characters as a \"%s\" token.\n",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImages[jjmatchedKind]).c_str());
//@fi
	return pos + 1;
}
//@fi
//@apply(positions)
//@apply(startWithStates)
//@fi
