//@if(accept)
if __acceptCondition__ {
	kind = __kind__;
}
//@fi
//@if(match)
if __negated__ {
	break;
}
if kind > __kind__ {
	kind = __kind__;
}
	//@apply(next)
//@fi
//@if(advance)
if __condition__ {
	//@apply(next)
}
//@fi
