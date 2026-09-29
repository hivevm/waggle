 inline bool jj_3__name__()
 {

    if (jj_done) return true;
//@if(DEPTH_LIMIT)
#define \__ERROR_RET__ true
//@fi
//@if(traced)
    //@apply(trace)

//@fi
//@apply(body)
    //@apply(result)

//@if(DEPTH_LIMIT)
#undef \__ERROR_RET__
//@fi
  }

