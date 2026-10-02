//@if(hasSkip)
try {
	//@apply(skip)
} catch (java.io.IOException e1) { continue EOFLoop; }
//@fi
//@if(matchesEmpty)
//@if(DEBUG_TOKEN_MANAGER)
debugStream.println("   Matched the empty string as " + ParserConstants.tokenImage[__emptyMatch__] + " token.");
//@fi
jjmatchedKind = __emptyMatch__;
jjmatchedPos = -1;
curPos = 0;
//@else
jjmatchedKind = 0x7fffffff;
jjmatchedPos = 0;
//@fi
//@if(DEBUG_TOKEN_MANAGER)
//@if(SWITCH_ON_LEX_STATE)
debugStream.println("<" + lexStateNames[curLexState] + ">" + "Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@else
debugStream.println("Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@fi
//@fi
curPos = jjMoveStringLiteralDfa0___index__();
//@if(anyChar)
//@if(matchesEmpty)
if (jjmatchedPos < 0 || (jjmatchedPos == 0 && jjmatchedKind > __anyCharKind__)) {
//@else
if (jjmatchedPos == 0 && jjmatchedKind > __anyCharKind__) {
//@fi
//@if(DEBUG_TOKEN_MANAGER)
	debugStream.println("Current character matched as a " + ParserConstants.tokenImage[__anyCharKind__] + " token.");
//@fi
	jjmatchedKind = __anyCharKind__;
//@if(matchesEmpty)
	jjmatchedPos = 0;
//@fi
}
//@fi
