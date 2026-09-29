//@if(add)
self.jjstate_set[self.jjnew_state_cnt] = __first__;
self.jjnew_state_cnt += 1;
//@fi
//@if(checkAdd)
self.jj_check_n_add(__first__);
//@fi
//@if(checkAddTwo)
self.jj_check_n_add_two_states(__first__, __second__);
//@fi
//@if(addStates)
self.jj_add_states(__first__, __second__);
//@fi
//@if(checkAddStates)
//@if(isRange)
self.jj_check_n_add_states(__first__, __second__);
//@else
self.jj_check_n_add_state_pair(__first__);
//@fi
//@fi
