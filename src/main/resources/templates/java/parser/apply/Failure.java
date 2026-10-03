//@if(traced)
//@if(rescan)
{ if (!jj_rescan) trace_return("__production__(LOOKAHEAD FAILED)"); return true; }
//@else
{ trace_return("__production__(LOOKAHEAD FAILED)"); return true; }
//@fi
//@else
return true;
//@fi
