//@if(eofActions)
    TokenLexicalActions(matchedToken);
//@fi
    return matchedToken;
}
//@if(imageInit)
image = jjimage;
image.setLength(0);
jjimageLen = 0;
//@fi

//@if(moreLoop)
for (; ; ) {
	//@apply(body)
}
//@else
//@apply(body)
//@fi
