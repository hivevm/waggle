if __condition__ {
//@if(hasAccept)
{
//@if(raise)
	if kind > __kind__ {
		kind = __kind__;
	}
//@else
	kind = __kind__;
//@fi
	//@apply(next)
}
//@else
//@apply(next)
//@fi
}
