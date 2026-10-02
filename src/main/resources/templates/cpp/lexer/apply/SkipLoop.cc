//@if(both)
while ((curChar < 64 && (__lowerMask__ & (1ULL << curChar)) != 0L) ||
          (curChar >> 6) == 1 && (__upperMask__ & (1ULL << (curChar & 077))) != 0L) {
//@elif(upperOnly)
while (curChar > 63 && curChar <= __maxChar__ && (__upperMask__ & (1ULL << (curChar & 077))) != 0L) {
//@else
while (curChar <= __maxChar__ && (__lowerMask__ & (1ULL << curChar)) != 0L) {
//@fi
//@if(DEBUG_TOKEN_MANAGER)
//@if(SWITCH_ON_LEX_STATE)
	fprintf(debugStream, "<%s>" , addUnicodeEscapes(lexStateNames[curLexState]).c_str());
//@fi
	fprintf(debugStream, "Skipping character : %c(%d)\n", curChar, (int)curChar);
//@fi
	if (reader->endOfInput()) { goto EOFLoop; }
	curChar = reader->beginToken();
}
