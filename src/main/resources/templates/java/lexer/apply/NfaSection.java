//@if(low)
long l = 1L << curChar;
//@fi
//@if(high)
long l = 1L << (curChar & 077);
//@fi
//@if(other)
int hiByte = (curChar >> 8);
int i1 = hiByte >> 6;
long l1 = 1L << (hiByte & 077);
int i2 = (curChar & 0xff) >> 6;
long l2 = 1L << (curChar & 077);
//@fi
do {
	switch (jjstateSet[--i]) {
		//@apply(arms)
		default: {
			break;
		}
	}
} while (i != startsAt);
