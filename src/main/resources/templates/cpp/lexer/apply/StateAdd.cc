//@if(add)
jjstateSet[jjnewStateCnt++] = __first__;
//@fi
//@if(checkAdd)
jjCheckNAdd(__first__);
//@fi
//@if(checkAddTwo)
jjCheckNAddTwoStates(__first__, __second__);
//@fi
//@if(addStates)
jjAddStates(__first__, __second__);
//@fi
//@if(checkAddStates)
//@if(isRange)
jjCheckNAddStates(__first__, __second__);
//@else
jjCheckNAddStates(__first__);
//@fi
//@fi
