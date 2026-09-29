//@if(testHi)
         if ((jjbitVec__hiMask__[i1] & l1) != 0L)
//@fi
//@if(testLo)
            if ((jjbitVec__loMask__[i2] & l2) == 0L)
               return false;
            else
//@fi
            return true;
