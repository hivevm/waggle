//@if(hasKind)
if (__condition__) {
//@if(first)
	jjmatchedKind = __kind__;
//@fi
//@if(firstAfterEmpty)
	jjmatchedKind = __kind__;
	jjmatchedPos = 0;
//@fi
//@if(hereUnlessMatched)
	if (jjmatchedPos != __pos__)  {
		jjmatchedKind = __kind__;
		jjmatchedPos = __pos__;
	}
//@fi
//@if(here)
	jjmatchedKind = __kind__;
	jjmatchedPos = __pos__;
//@fi
//@if(earlier)
	if (jjmatchedPos < __matchedPos__) {
		jjmatchedKind = __kind__;
		jjmatchedPos = __matchedPos__;
	}
//@fi
//@if(earlierAtFirst)
	if (jjmatchedPos == 0) {
		jjmatchedKind = __kind__;
		jjmatchedPos = __matchedPos__;
	}
//@fi
	return __resume__;
}
//@else
if (__condition__)
	return __resume__;
//@fi
