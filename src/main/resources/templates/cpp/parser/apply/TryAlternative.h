//@if(guarded)
    jj_lookingAhead = true;
    jj_semLA = __semantic__;
    jj_lookingAhead = false;
    if (!jj_semLA || __call__) {
//@else
    if (__call__) {
//@fi
    jj_scanpos = xsp;
//@apply(rest)
    }
