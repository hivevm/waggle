//@if(traced)
{ //@if(rescan)
if (!jj_rescan) //@fi
trace_return("__production__(LOOKAHEAD SUCCEEDED)"); return false; }//@else
return false;//@fi