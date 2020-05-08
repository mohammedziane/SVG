package com.example.svg;

/**
 * Parse a SVG/CSS 'integer' or hex number from a String.
 *  Assumes maxPos will not be greater than input.length().
 */

class IntegerParser
{

    private long     value;
    private int      pos;

    IntegerParser(long value, int pos)
    {
        this.value = value;
        this.pos = pos;
    }



    int  getEndPos()
    {
        return this.pos;
    }


   //Integer


    static IntegerParser  parseInt(String input, int startpos, int len, boolean includeSign)
    {
        int      pos = startpos;
        boolean  isNegative = false;
        long     value = 0;
        char     ch;

        if (pos >= len)
            return null;

        if (includeSign)
        {
            ch = input.charAt(pos);
            switch (ch) {
                case '-': isNegative = true;

                case '+': pos++;
            }
        }
        int  sigStart = pos;

        while (pos < len)
        {
            ch = input.charAt(pos);
            if (ch >= '0' && ch <= '9')
            {
                if (isNegative) {
                    value = value * 10 - ((int)ch - (int)'0'); //le -(int)'0' est juste pour eviter une erreur
                    if (value < Integer.MIN_VALUE)
                        return null;
                } else {
                    value = value * 10 + ((int)ch - (int)'0'); //meme chose
                    if (value > Integer.MAX_VALUE)
                        return null;
                }
            }
            else
                break;
            pos++;
        }


        if (pos == sigStart) {
            return null;
        }

        return new IntegerParser(value, pos);
    }



    public int  value()
    {
        return (int)value;
    }


 //Hex integer  meme principe juste à base hexadicimale

    static IntegerParser  parseHex(String input, int startpos, int len)
    {
        int   pos = startpos;
        long  value = 0;
        char  ch;


        if (pos >= len)
            return null;

        while (pos < len)
        {
            ch = input.charAt(pos);
            if (ch >= '0' && ch <= '9')
            {
                value = value * 16 + ((int)ch - (int)'0');
            }
            else if (ch >= 'A' && ch <= 'F')
            {
                value = value * 16 + ((int)ch - (int)'A') + 10;
            }
            else if (ch >= 'a' && ch <= 'f')
            {
                value = value * 16 + ((int)ch - (int)'a') + 10;
            }
            else
                break;

            if (value > 0xffffffffL)
                return null;

            pos++;
        }


        if (pos == startpos) {
            return null;
        }

        return new IntegerParser(value, pos);
    }

}

