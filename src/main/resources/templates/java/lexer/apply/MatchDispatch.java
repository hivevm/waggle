if (jjmatchedKind != 0x7fffffff) {
	if (jjmatchedPos + 1 < curPos) {
//@if(DEBUG_TOKEN_MANAGER)
		debugStream.println("   Putting back " + (curPos - jjmatchedPos - 1) + " characters into the input stream.");
//@fi
		input_stream.backup(curPos - jjmatchedPos - 1);
	}
//@if(DEBUG_TOKEN_MANAGER)
	debugStream.println("****** FOUND A " + ParserConstants.tokenImage[jjmatchedKind] + " MATCH (" + TokenException.addEscapes(new String(input_stream.GetSuffix(jjmatchedPos + 1))) + ") ******\n");
//@fi
//@if(tokenTest)
	if ((jjtoToken[jjmatchedKind >> 6] & (1L << (jjmatchedKind & 077))) != 0L) {
		//@apply(token)
	}
	//@apply(skip)
	//@apply(more)
//@else
	//@apply(token)
//@fi
}
int error_line = input_stream.getEndLine();
int error_column = input_stream.getEndColumn();
String error_after = null;
boolean EOFSeen = false;
try {
    input_stream.readChar();
    input_stream.backup(1);
} catch (java.io.IOException e1) {
    EOFSeen = true;
    error_after = curPos <= 1 ? "" : input_stream.GetImage();
    if (curChar == '\n' || curChar == '\r') {
        error_line++;
        error_column = 0;
    } else
        error_column++;
}
if (!EOFSeen) {
    input_stream.backup(1);
    error_after = curPos <= 1 ? "" : input_stream.GetImage();
}
throw new TokenException(EOFSeen, curLexState, error_line, error_column, error_after, curChar, TokenException.LEXICAL_ERROR);
