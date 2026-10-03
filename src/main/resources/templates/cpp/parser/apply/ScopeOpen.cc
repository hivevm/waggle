//@if(!production)
// __descriptor__
//@fi
__nodeClass__ *__nodeVar__ = new __nodeClass__(__nodeId__);
bool __closedVar__ = true;
jjtree.openNodeScope(__nodeVar__);
//@if(scopeHook)
jjtreeOpenNodeScope(__nodeVar__);
//@fi
//@if(trackTokens)
__nodeVar__->jjtSetFirstToken(getToken(1));
//@fi
try {
