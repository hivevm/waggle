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
//@else
         image.append(reader->getSuffix(jjimageLen));
//@fi
         jjimageLen = 0;
__code__
//@fi
         break;
       }
