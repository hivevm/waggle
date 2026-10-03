//@if(!production)
// __descriptor__
//@fi
//@if(factory)
__nodeClass__ __nodeVar__ = (__nodeClass__)__factory__.jjtCreate(this, NodeType.__nodeId__);
//@else
__nodeClass__ __nodeVar__ = new __nodeClass__(this, NodeType.__nodeId__);
//@fi
boolean __closedVar__ = true;
jjtree.openNodeScope(__nodeVar__);
//@if(scopeHook)
jjtreeOpenNodeScope(__nodeVar__);
//@fi
//@if(trackTokens)
__nodeVar__.jjtSetFirstToken(getToken(1));
//@fi
try {
