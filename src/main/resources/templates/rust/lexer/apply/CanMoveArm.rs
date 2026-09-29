//@if(testHi)
if (JJBIT_VEC__hiMask__[i1] & l1) != 0 {
//@if(testLo)
	if (JJBIT_VEC__loMask__[i2] & l2) == 0 {
		return false;
	} else {
		return true;
	}
//@else
	return true;
//@fi
}
//@else
//@if(testLo)
if (JJBIT_VEC__loMask__[i2] & l2) == 0 {
	return false;
} else {
	return true;
}
//@else
return true;
//@fi
//@fi
