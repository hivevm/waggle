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
if (!reader->endOfInput()) {
    curChar = reader->read(); // UTF8: Support Unicode
//@if(DEBUG_TOKEN_MANAGER)
fprintf(debugStream, "<%s>Current character : %c(%d) at line %d column %d\n",addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, reader->getEndLine(), reader->getEndColumn());
//@fi
    continue;
}
