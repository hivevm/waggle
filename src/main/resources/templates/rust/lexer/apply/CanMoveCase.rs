__hiByte__ => {
//@if(any)
	return true;
//@else
	return (JJBIT_VEC__mask__[i2] & l2) != 0;
//@fi
}
