//@if(testHi)
if ((jjbitVec__hiMask__[i1] & l1) != 0L)
//@if(testLo)
	if ((jjbitVec__loMask__[i2] & l2) == 0L)
		return false;
	else
		return true;
//@else
	return true;
//@fi
//@else
//@if(testLo)
if ((jjbitVec__loMask__[i2] & l2) == 0L)
	return false;
else
	return true;
//@else
return true;
//@fi
//@fi
