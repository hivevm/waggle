//@if(moreBranch)
else if ((jjtoSkip[jjmatchedKind >> 6] & (1ULL << (jjmatchedKind & 077))) != 0L) {
//@else
else {
//@fi
//@if(special)
	if ((jjtoSpecial[jjmatchedKind >> 6] & (1ULL << (jjmatchedKind & 077))) != 0L) {
		matchedToken = jjFillToken();
		if (specialToken == nullptr)
		    specialToken = matchedToken;
		else {
		    matchedToken->specialToken() = specialToken;
		    specialToken = (specialToken->next() = matchedToken);
		}
//@if(skipActions)
		SkipLexicalActions(matchedToken);
//@fi
	}
//@if(skipActions)
	else
	    SkipLexicalActions(nullptr);
//@fi
//@else
//@if(skipActions)
	SkipLexicalActions(nullptr);
//@fi
//@fi
//@if(newLexState)
	if (jjnewLexState[jjmatchedKind] != -1)
		curLexState = jjnewLexState[jjmatchedKind];
//@fi
	goto EOFLoop;
}
