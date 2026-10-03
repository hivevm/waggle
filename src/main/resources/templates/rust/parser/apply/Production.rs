//@apply(signature)//@if(scoped) // __descriptor__//@fi
//@if(scoped)
	//@apply(scopeOpen)
		//@apply(inner)
	//@apply(scopeClose)
//@else
	//@apply(inner)
//@fi
//@if(voidResult)
	Ok(())
}

//@else
}

//@fi
