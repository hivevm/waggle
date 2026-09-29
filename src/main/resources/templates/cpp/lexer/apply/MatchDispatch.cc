if (jjmatchedKind != 0x7fffffff) {
	if (jjmatchedPos + 1 < curPos) {
//@if(DEBUG_TOKEN_MANAGER)
		fprintf(debugStream, "   Putting back %d characters into the input stream.\n", (curPos - jjmatchedPos - 1));
//@fi
		reader->backup(curPos - jjmatchedPos - 1);
	}
//@if(DEBUG_TOKEN_MANAGER)
	fprintf(debugStream, "****** FOUND A %d(%s) MATCH (%s) ******\n", jjmatchedKind, addUnicodeEscapes(tokenImages[jjmatchedKind]).c_str(), addUnicodeEscapes(reader->getSuffix(jjmatchedPos + 1)).c_str());
//@fi
//@if(tokenTest)
	if ((jjtoToken[jjmatchedKind >> 6] & (1ULL << (jjmatchedKind & 077))) != 0L) {
		//@apply(token)
	}
	//@apply(skip)
	//@apply(more)
//@else
	//@apply(token)
//@fi
}
//@if(moreBranch)
break;
//@fi
