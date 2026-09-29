//@if(guarded)
self.jj_looking_ahead = true;
self.jj_sem_la = __semantic__;
self.jj_looking_ahead = false;
if !self.jj_sem_la || __call__ {
//@else
if __call__ {
//@fi
	//@apply(failure)

}
