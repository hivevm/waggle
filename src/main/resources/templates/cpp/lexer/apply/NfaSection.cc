//@if(low)
unsigned long long l = 1ULL << curChar;
(void)l;
//@fi
//@if(high)
unsigned long long l = 1ULL << (curChar & 077);
(void)l;
//@fi
//@if(other)
int hiByte = (curChar >> 8);
int i1 = hiByte >> 6;
unsigned long long l1 = 1ULL << (hiByte & 077);
int i2 = (curChar & 0xff) >> 6;
unsigned long long l2 = 1ULL << (curChar & 077);
//@fi
do {
	switch(jjstateSet[--i]) {
		//@apply(arms)
		default: {
			break;
		}
	}
} while (i != startsAt);
