//@if(switchOnLexState)
switch(curLexState) {
	//@apply(states)
}
//@else
//@apply(states)
//@fi
//@if(noLexState)
jjmatchedKind = 0x7fffffff;
//@fi
//@apply(dispatch)
