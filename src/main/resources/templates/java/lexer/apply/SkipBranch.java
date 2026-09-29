//@if(moreBranch)
else if ((jjtoSkip[jjmatchedKind >> 6] & (1L << (jjmatchedKind & 077))) != 0L) {
//@else
else {
//@fi
//@if(special)
	if ((jjtoSpecial[jjmatchedKind >> 6] & (1L << (jjmatchedKind & 077))) != 0L) {
		matchedToken = jjFillToken();
		if (specialToken == null)
			specialToken = matchedToken;
		else {
			matchedToken.specialToken = specialToken;
			specialToken = (specialToken.next = matchedToken);
		}
//@if(skipActions)
		SkipLexicalActions(matchedToken);
	} else
		SkipLexicalActions(null);
//@else
	}
//@fi
//@else
//@if(skipActions)
	SkipLexicalActions(null);
//@fi
//@fi
//@if(newLexState)
	if (jjnewLexState[jjmatchedKind] != -1)
		curLexState = jjnewLexState[jjmatchedKind];
//@fi
	continue EOFLoop;
}
