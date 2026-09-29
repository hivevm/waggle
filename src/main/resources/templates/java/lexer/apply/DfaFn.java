
private int jjMoveStringLiteralDfa__pos____suffix__(__params__) {
//@if(activeCheck)
	if ((__masked__) == 0L) {
		return __checkReturn__;
	}
//@fi
//@if(readChar)
//@if(DEBUG_TOKEN_MANAGER)
	//@apply(debugMatches)
//@fi
	try {
	    curChar = input_stream.readChar();
	} catch (java.io.IOException e) {
//@if(eofStartNfa)
		__stopCall__;
//@if(DEBUG_TOKEN_MANAGER)
		if (jjmatchedKind != 0 && jjmatchedKind != 0x7fffffff)
		    debugStream.println("   Currently matched the first " + (jjmatchedPos + 1) + " characters as a " + ParserConstants.tokenImage[jjmatchedKind] + " token.");
//@fi
		return __pos__;
//@else
		return __eofReturn__;
//@fi
	}
//@if(DEBUG_TOKEN_MANAGER)
//@if(withLexState)
	debugStream.println("<" + lexStateNames[curLexState] + ">" + "Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@else
	debugStream.println("Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@fi
//@fi
//@fi
	switch (curChar) {
		//@apply(cases)
		default: {
//@if(DEBUG_TOKEN_MANAGER)
			    debugStream.println("   No string literal matches possible.");
//@fi
			//@apply(otherwise)
		}
	}
//@if(tail)
//@if(tailStartNfa)
	return __tailStartCall__;
//@else
//@if(tailMoveNfa)
	return __tailMoveCall__;
//@else
	return __tailReturn__;
//@fi
//@fi
//@fi
}
