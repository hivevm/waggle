//@if(accept)
	if __acceptCondition__ {
		kind = __kind__;
	}
//@fi
//@if(match)
//@if(guarded)
	if __negated__ {
		break;
	}
//@fi
//@if(raise)
	if kind > __kind__ {
		kind = __kind__;
	}
//@else
	kind = __kind__;
//@fi
	//@apply(next)
//@fi
//@if(advance)
//@if(guarded)
	if __condition__ {
		//@apply(next)
	}
//@else
	//@apply(next)
//@fi
//@fi
