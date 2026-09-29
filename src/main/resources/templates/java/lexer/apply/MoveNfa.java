
private int jjMoveNfa__suffix__(int startState, int curPos) {
//@if(mixed)
	int strKind = jjmatchedKind;
	int strPos = jjmatchedPos;
	int seenUpto;
	input_stream.backup(seenUpto = curPos + 1);
	try {
	    curChar = input_stream.readChar();
	} catch (java.io.IOException e) {
	    throw new Error("Internal Error");
	}
	curPos = 0;
//@fi
	int startsAt = 0;
	jjnewStateCnt = __generatedStates__;
	int i = 1;
	jjstateSet[0] = startState;
//@if(debug)
	debugStream.println("   Starting NFA to match one of : " + jjKindsForStateVector(curLexState, jjstateSet, 0, 1));
//@if(withLexState)
	debugStream.println("<" + lexStateNames[curLexState] + ">" + "Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@else
	debugStream.println("Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@fi
//@fi
	int kind = 0x7fffffff;
	for (; ; ) {
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
//@if(debug)
		if (jjmatchedKind != 0 && jjmatchedKind != 0x7fffffff)
		    debugStream.println("   Currently matched the first " + (jjmatchedPos + 1) + " characters as a " + ParserConstants.tokenImage[jjmatchedKind] + " token.");
//@fi
		if ((i = jjnewStateCnt) == (startsAt = __generatedStates__ - (jjnewStateCnt = startsAt)))
//@if(mixed)
			break;
//@else
			return curPos;
//@fi
//@if(debug)
		debugStream.println("   Possible kinds of longer matches : " + jjKindsForStateVector(curLexState, jjstateSet, startsAt, i));
//@fi
		try {
		    curChar = input_stream.readChar();
		} catch (java.io.IOException e) {
//@if(mixed)
			break;
//@else
			return curPos;
//@fi
		}
//@if(debug)
//@if(withLexState)
		debugStream.println("<" + lexStateNames[curLexState] + ">" + "Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@else
		debugStream.println("Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@fi
//@fi
	}
//@if(mixed)
	if (jjmatchedPos > strPos)
	    return curPos;

	int toRet = Math.max(curPos, seenUpto);
	if (curPos < toRet)
	    for (i = toRet - Math.min(curPos, seenUpto); i-- > 0; )
	        try {
	            curChar = input_stream.readChar();
	        } catch (java.io.IOException e) {
	            throw new Error("Internal Error : Please send a bug report.");
	        }

	if (jjmatchedPos < strPos) {
	    jjmatchedKind = strKind;
	    jjmatchedPos = strPos;
	} else if (jjmatchedPos == strPos && jjmatchedKind > strKind)
	    jjmatchedKind = strKind;

	return toRet;
//@fi
}
