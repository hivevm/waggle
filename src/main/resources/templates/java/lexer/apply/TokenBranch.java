matchedToken = jjFillToken();
//@if(special)
matchedToken.specialToken = specialToken;
//@fi
//@if(tokenActions)
TokenLexicalActions(matchedToken);
//@fi
//@if(newLexState)
if (jjnewLexState[jjmatchedKind] != -1)
	curLexState = jjnewLexState[jjmatchedKind];
//@fi
return matchedToken;
