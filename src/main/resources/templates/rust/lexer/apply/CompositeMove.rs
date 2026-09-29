//@if(guarded)
//@if(elseIf)
else if __condition__ {
//@else
if __condition__ {
//@fi
//@fi
//@if(hasAccept)
//@if(guarded)
	{
//@fi
//@if(raise)
	if kind > __kind__ {
		kind = __kind__;
	}
//@else
	kind = __kind__;
//@fi
//@fi
	//@apply(next)
//@if(guarded)
//@if(hasAccept)
}
//@fi
}
//@fi
