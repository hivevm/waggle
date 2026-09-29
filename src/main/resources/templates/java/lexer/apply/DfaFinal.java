//@if(guarded)
//@if(elseIf)
//@if(startNfa)
else if ((__guard__) != 0L)return __startNfaCall__;
//@fi
//@if(stopAtPos)
else if ((__guard__) != 0L)return __stopAtPosCall__;
//@fi
//@if(kindAndPos)
else if ((__guard__) != 0L) {
	jjmatchedKind = __kind__;
	jjmatchedPos = __pos__;
}
//@fi
//@if(kindOnly)
else if ((__guard__) != 0L)jjmatchedKind = __kind__;
//@fi
//@else
//@if(startNfa)
if ((__guard__) != 0L)return __startNfaCall__;
//@fi
//@if(stopAtPos)
if ((__guard__) != 0L)return __stopAtPosCall__;
//@fi
//@if(kindAndPos)
if ((__guard__) != 0L) {
	jjmatchedKind = __kind__;
	jjmatchedPos = __pos__;
}
//@fi
//@if(kindOnly)
if ((__guard__) != 0L)jjmatchedKind = __kind__;
//@fi
//@fi
//@else
//@if(startNfa)
return __startNfaCall__;
//@fi
//@if(stopAtPos)
return __stopAtPosCall__;
//@fi
//@if(kindAndPos)
 {
	jjmatchedKind = __kind__;
	jjmatchedPos = __pos__;
}
//@fi
//@if(kindOnly)
jjmatchedKind = __kind__;
//@fi
//@fi
