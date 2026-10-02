//@if(moreActions)
MoreLexicalActions();
//@else
//@if(moreImageLen)
jjimageLen += jjmatchedPos + 1;
//@fi
//@fi
//@if(newLexState)
if (jjnewLexState[jjmatchedKind] != -1)
	curLexState = jjnewLexState[jjmatchedKind];
//@fi
curPos = 0;
jjmatchedKind = 0x7fffffff;
try {
	curChar = input_stream.readChar();
//@if(DEBUG_TOKEN_MANAGER)
//@if(SWITCH_ON_LEX_STATE)
	debugStream.println("<" + lexStateNames[curLexState] + ">" + "Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@else
	debugStream.println("Current character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ") at line " + input_stream.getEndLine() + " column " + input_stream.getEndColumn());
//@fi
//@fi
	continue;
} catch (java.io.IOException e1) {
}
