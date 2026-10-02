//@if(both)
while ((curChar < 64 && (__lowerMask__ & (1L << curChar)) != 0L) ||
          (curChar >> 6) == 1 && (__upperMask__ & (1L << (curChar & 077))) != 0L)
//@elif(upperOnly)
while (curChar > 63 && curChar <= __maxChar__ && (__upperMask__ & (1L << (curChar & 077))) != 0L)
//@else
while (curChar <= __maxChar__ && (__lowerMask__ & (1L << curChar)) != 0L)
//@fi
//@if(DEBUG_TOKEN_MANAGER)
 {
//@if(SWITCH_ON_LEX_STATE)
	debugStream.println("<" + lexStateNames[curLexState] + ">" + "Skipping character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ")");
//@else
	debugStream.println("Skipping character : " + TokenException.addEscapes(String.valueOf((char) curChar)) + " (" + (int)curChar + ")");
//@fi
	curChar = input_stream.BeginToken();
}
//@else
curChar = input_stream.BeginToken();
//@fi
