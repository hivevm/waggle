//@apply(signature)
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
