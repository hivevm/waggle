
int __PARSER_NAME__TokenManager::jjStartNfaWithStates__suffix__(int pos, int kind, int state){
	jjmatchedKind = kind;
	jjmatchedPos = pos;
//@if(DEBUG_TOKEN_MANAGER)
	fprintf(debugStream, "No more string literal token matches are possible.");
	fprintf(debugStream, "Currently matched the first %d characters as a \"%s\" token.\n",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImages[jjmatchedKind]).c_str());
//@fi
	if (reader->endOfInput()) { return pos + 1; }
	curChar = reader->read(); // UTF8: Support Unicode
//@if(DEBUG_TOKEN_MANAGER)
	fprintf(debugStream, "<%s>Current character : %c(%d) at line %d column %d\n",addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, reader->getEndLine(), reader->getEndColumn());
//@fi
	return jjMoveNfa__suffix__(state, pos + 1);
}
