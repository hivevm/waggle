
int __parserName__TokenManager::jjMoveNfa__suffix__(int startState, int curPos) {
//@if(mixed)
	int strKind = jjmatchedKind;
	int strPos = jjmatchedPos;
	int seenUpto;
	reader->backup(seenUpto = curPos + 1);
	assert(!reader->endOfInput());
	curChar = reader->read(); // UTF8: Support Unicode
	curPos = 0;
//@fi
	int startsAt = 0;
	jjnewStateCnt = __generatedStates__;
	int i = 1;
	jjstateSet[0] = startState;
//@if(DEBUG_TOKEN_MANAGER)
	fprintf(debugStream, "   Starting NFA to match one of : %s\n", jjKindsForStateVector(curLexState, jjstateSet, 0, 1).c_str());
	fprintf(debugStream, "<%s>Current character : %c(%d) at line %d column %d\n",addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, reader->getEndLine(), reader->getEndColumn());
//@fi
	int kind = 0x7fffffff;
	for (;;) {
		if (++jjround == 0x7fffffff)
		    ReInitRounds();
		if (curChar < 64) {
			//@apply(low)
		} else if (curChar < 128) {
			//@apply(high)
		} else {
			//@apply(other)
		}
		if (kind != 0x7fffffff) {
		    jjmatchedKind = kind;
		    jjmatchedPos = curPos;
		    kind = 0x7fffffff;
		}
		curPos++;
//@if(DEBUG_TOKEN_MANAGER)
		if (jjmatchedKind != 0 && jjmatchedKind != 0x7fffffff)
		    fprintf(debugStream, "   Currently matched the first %d characters as a \"%s\" token.\n", (jjmatchedPos + 1),  addUnicodeEscapes(tokenImages[jjmatchedKind]).c_str());
//@fi
		if ((i = jjnewStateCnt), (jjnewStateCnt = startsAt), (i == (startsAt = __generatedStates__ - startsAt)))
//@if(mixed)
			break;
//@else
			return curPos;
//@fi
//@if(DEBUG_TOKEN_MANAGER)
		fprintf(debugStream, "   Possible kinds of longer matches : %s\n", jjKindsForStateVector(curLexState, jjstateSet, startsAt, i).c_str());
//@fi
//@if(mixed)
		if (reader->endOfInput()) { break; }
//@else
		if (reader->endOfInput()) { return curPos; }
//@fi
		curChar = reader->read(); // UTF8: Support Unicode
//@if(DEBUG_TOKEN_MANAGER)
		fprintf(debugStream, "<%s>Current character : %c(%d) at line %d column %d\n",addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, reader->getEndLine(), reader->getEndColumn());
//@fi
	}
//@if(mixed)
	if (jjmatchedPos > strPos)
	    return curPos;

	int toRet = MAX(curPos, seenUpto);
	if (curPos < toRet)
	    for (i = toRet - MIN(curPos, seenUpto); i-- > 0; ) {
	        assert(!reader->endOfInput());
	        curChar = reader->read();
	    } // UTF8: Support Unicode

	if (jjmatchedPos < strPos) {
	    jjmatchedKind = strKind;
	    jjmatchedPos = strPos;
	} else if (jjmatchedPos == strPos && jjmatchedKind > strKind)
	    jjmatchedKind = strKind;

	return toRet;
//@fi
}
