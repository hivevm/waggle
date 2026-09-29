      case __kind__ : {
//@if(loopCheck)
         if (jjmatchedPos == -1)
         {
            if (jjbeenHere[__lexState__] &&
                jjemptyLineNo[__lexState__] == reader->getBeginLine() &&
                jjemptyColNo[__lexState__] == reader->getBeginColumn())
               loopDetected();
            jjemptyLineNo[__lexState__] = reader->getBeginLine();
            jjemptyColNo[__lexState__] = reader->getBeginColumn();
            jjbeenHere[__lexState__] = true;
         }
//@fi
//@if(hasCode)
//@if(literal)
         image.append(jjstrLiteralImages[__kind__]);
        lengthOfMatch = jjstrLiteralImages[__kind__].length();
//@else
         image.append(reader->getSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));
//@fi
__code__
//@fi
         break;
       }
