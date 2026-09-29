//@if(eofActions)
	      TokenLexicalActions(matchedToken);
//@fi
	return matchedToken;
}
curChar = reader->beginToken();
//@if(imageInit)
image.clear();
jjimageLen = 0;
//@fi

//@if(moreLoop)
for (;;) {
	//@apply(body)
}
//@else
//@apply(body)
//@fi
