//@if(guarded)
//@if(elseIf)
else if (__guard__) != 0 {
//@if(startNfa)
	return __startNfaCall__;
//@fi
//@if(stopAtPos)
	return __stopAtPosCall__;
//@fi
//@if(kindAndPos)
	self.jjmatched_kind = __kind__;
	self.jjmatched_pos = __pos__;
//@fi
//@if(kindOnly)
	self.jjmatched_kind = __kind__;
//@fi
}
//@else
if (__guard__) != 0 {
//@if(startNfa)
	return __startNfaCall__;
//@fi
//@if(stopAtPos)
	return __stopAtPosCall__;
//@fi
//@if(kindAndPos)
	self.jjmatched_kind = __kind__;
	self.jjmatched_pos = __pos__;
//@fi
//@if(kindOnly)
	self.jjmatched_kind = __kind__;
//@fi
}
//@fi
//@else
//@if(startNfa)
return __startNfaCall__;
//@fi
//@if(stopAtPos)
return __stopAtPosCall__;
//@fi
//@if(kindAndPos)
self.jjmatched_kind = __kind__;
self.jjmatched_pos = __pos__;
//@fi
//@if(kindOnly)
self.jjmatched_kind = __kind__;
//@fi
//@fi
