while (__condition__)
//@if(DEBUG_TOKEN_MANAGER)
 {
//@if(withLexState)
	debugStream.println("<" + lexStateNames[curLexState] + ">" + "Skipping character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ")");
//@else
	debugStream.println("Skipping character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ")");
//@fi
	curChar = input_stream.BeginToken();
}
//@else
curChar = input_stream.BeginToken();
//@fi
