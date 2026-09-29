//@if(eofActions)
    self.token_lexical_actions(&mut matched_token)?;
//@fi
    return Ok(matched_token);
}
//@if(imageInit)
self.image.clear();
self.jjimage_len = 0;
//@fi

//@if(moreLoop)
loop {
	//@apply(body)
}
//@else
//@apply(body)
//@fi
