while (__condition__) {
//@if(DEBUG_TOKEN_MANAGER)
//@if(withLexState)
	fprintf(debugStream, "<%s>" , addUnicodeEscapes(lexStateNames[curLexState]).c_str());
//@fi
	fprintf(debugStream, "Skipping character : %c(%d)\n", curChar, (int)curChar);
//@fi
	if (reader->endOfInput()) { goto EOFLoop; }
	curChar = reader->beginToken();
}
