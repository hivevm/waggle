if (__condition__) {
//@if(first)
	self.jjmatched_kind = __kind__;
//@fi
//@if(firstAfterEmpty)
	self.jjmatched_kind = __kind__;
	self.jjmatched_pos = 0;
//@fi
//@if(hereUnlessMatched)
	if (self.jjmatched_pos != __pos__)  {
		self.jjmatched_kind = __kind__;
		self.jjmatched_pos = __pos__;
	}
//@fi
//@if(here)
	self.jjmatched_kind = __kind__;
	self.jjmatched_pos = __pos__;
//@fi
//@if(earlier)
	if (self.jjmatched_pos < __matchedPos__) {
		self.jjmatched_kind = __kind__;
		self.jjmatched_pos = __matchedPos__;
	}
//@fi
//@if(earlierAtFirst)
	if (self.jjmatched_pos == 0) {
		self.jjmatched_kind = __kind__;
		self.jjmatched_pos = __matchedPos__;
	}
//@fi
	return __resume__;
}
