//@if(hasSkip)
//@apply(skip)
//@fi
//@if(matchesEmpty)
//@if(DEBUG_TOKEN_MANAGER)
fprintf(debugStream, "   Matched the empty string as %s token.\n", addUnicodeEscapes(tokenImages[__emptyMatch__]).c_str());
//@fi
jjmatchedKind = __emptyMatch__;
jjmatchedPos = -1;
curPos = 0;
//@else
jjmatchedKind = 0x7fffffff;
jjmatchedPos = 0;
//@fi
//@if(DEBUG_TOKEN_MANAGER)
fprintf(debugStream, "<%s>Current character : %c(%d) at line %d column %d\n",addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, reader->getEndLine(), reader->getEndColumn());
//@fi
curPos = jjMoveStringLiteralDfa0___index__();
//@if(anyChar)
//@if(matchesEmpty)
if (jjmatchedPos < 0 || (jjmatchedPos == 0 && jjmatchedKind > __anyCharKind__)) {
//@else
if (jjmatchedPos == 0 && jjmatchedKind > __anyCharKind__) {
//@fi
//@if(DEBUG_TOKEN_MANAGER)
	fprintf(debugStream, "   Current character matched as a %s token.\n", addUnicodeEscapes(tokenImages[__anyCharKind__]).c_str());
//@fi
	jjmatchedKind = __anyCharKind__;
//@if(matchesEmpty)
	jjmatchedPos = 0;
//@fi
}
//@fi
