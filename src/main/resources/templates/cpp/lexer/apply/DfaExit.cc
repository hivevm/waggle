//@if(callNext)
return __callNext__;
//@fi
//@if(moveNfa)
return __moveCall__;
//@fi
//@if(leave)
break;
//@fi
//@if(ret)
return __returnPos__;
//@fi
