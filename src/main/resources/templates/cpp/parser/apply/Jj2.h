  inline bool jj_2__name__(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    jj_done = false;
//@if(DEPTH_LIMIT)
    return (!jj_3__name__() || jj_done) && !jj_depth_error;
//@else
    return (!jj_3__name__() || jj_done);
//@fi
  }

