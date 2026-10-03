//@if(traced)
//@if(rescan)
{ if (!jj_rescan) trace_return("__production__(LOOKAHEAD SUCCEEDED)"); return false; }
//@else
{ trace_return("__production__(LOOKAHEAD SUCCEEDED)"); return false; }
//@fi
//@else
return false;
//@fi
