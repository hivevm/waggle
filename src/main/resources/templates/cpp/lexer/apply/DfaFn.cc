
int __PARSER_NAME__TokenManager::jjMoveStringLiteralDfa__pos____suffix__(__params__) {
//@if(activeCheck)
	if ((__masked__) == 0L) {
		return __checkReturn__;
	}
//@fi
//@if(readChar)
//@if(DEBUG_TOKEN_MANAGER)
	//@apply(debugMatches)
//@fi
	if (reader->endOfInput()) {
//@if(eofStartNfa)
		__stopCall__;
//@if(DEBUG_TOKEN_MANAGER)
		if (jjmatchedKind != 0 && jjmatchedKind != 0x7fffffff)
		    fprintf(debugStream, "   Currently matched the first %d characters as a \"%s\" token.\n", (jjmatchedPos + 1),  addUnicodeEscapes(tokenImages[jjmatchedKind]).c_str());
//@fi
		return __pos__;
//@else
		return __eofReturn__;
//@fi
	}
	   curChar = reader->read(); // UTF8: as the NFA reads it
//@if(DEBUG_TOKEN_MANAGER)
	fprintf(debugStream, "<%s>Current character : %c(%d) at line %d column %d\n",addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, reader->getEndLine(), reader->getEndColumn());
//@fi
//@fi
	switch(curChar) {
		//@apply(cases)
		default: {
//@if(DEBUG_TOKEN_MANAGER)
			    fprintf(debugStream, "   No string literal matches possible.");
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
