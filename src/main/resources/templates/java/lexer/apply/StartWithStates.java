
private int jjStartNfaWithStates__suffix__(int pos, int kind, int state) {
	jjmatchedKind = kind;
	jjmatchedPos = pos;
//@if(DEBUG_TOKEN_MANAGER)
	debugStream.println("No more string literal token matches are possible.");
	debugStream.println("Currently matched the first " + (jjmatchedPos + 1) + " characters as a " + ParserConstants.tokenImage[jjmatchedKind] + " token.");
//@fi
	try {
	    curChar = input_stream.readChar();
	} catch (java.io.IOException e) {
	    return pos + 1;
	}
//@if(DEBUG_TOKEN_MANAGER)
//@if(withLexState)
	debugStream.println("<" + lexStateNames[curLexState] + ">" + "Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@else
	debugStream.println("Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@fi
//@fi
	return jjMoveNfa__suffix__(state, pos + 1);
}
