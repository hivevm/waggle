      case __kind__ :
//@if(loopCheck)
         if (jjmatchedPos == -1)
         {
            if (jjbeenHere[__lexState__] &&
                jjemptyLineNo[__lexState__] == input_stream.getBeginLine() &&
                jjemptyColNo[__lexState__] == input_stream.getBeginColumn())
               throw new TokenException(("Error: Bailing out of infinite loop caused by repeated empty string matches at line " + input_stream.getBeginLine() + ", column " + input_stream.getBeginColumn() + "."), TokenException.LOOP_DETECTED);
            jjemptyLineNo[__lexState__] = input_stream.getBeginLine();
            jjemptyColNo[__lexState__] = input_stream.getBeginColumn();
            jjbeenHere[__lexState__] = true;
         }
//@fi
//@if(hasCode)
//@if(literal)
         image.append(jjstrLiteralImages[__kind__]);
        lengthOfMatch = jjstrLiteralImages[__kind__].length();
//@else
         image.append(input_stream.GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));
//@fi
__code__
//@fi
         break;
