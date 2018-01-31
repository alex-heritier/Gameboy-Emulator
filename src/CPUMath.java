
class CPUMath {

  private CPUMath() {}

  // General helpers
  public static short[] toBytes(int word) {
    short[] bytes = new short[2];
    bytes[0] = (short)((word & 0xFF00) >> 8);
    bytes[1] = (short)(word & 0xFF);

    return bytes;
  }

  public static int word(short a, short b) {
    return (((int)a) << 8) + b;
  }

  public static boolean addWillHalfCarry(short a, short b) {
    return (((a & 0xF) + (b & 0xF)) & 0x10) == 0x10;
  }

  public static boolean subWillHalfCarry(short a, short b) {
    return ((a & 0xF) - (b & 0xF)) < 0;
  }

  public static boolean subWillCarry(short a, short b) {
    return a < b;
  }


  // Logic functions
  public static short or8(short byte1, short byte2) { return (short)(byte1 | byte2); }
  public static short xor8(short byte1, short byte2) { return (short)(byte1 ^ byte2); }
  public static short and8(short byte1, short byte2) { return (short)(byte1 & byte2); }


  // Arithmetic functions
  public static Result inc8(short value) { return add8(value, (short)1); }

  public static int inc16(int word) { return add16(word, 1); }
  public static int inc16(short byte1, short byte2) {
    int word = word(byte1, byte2);
    return inc16(word);
  }

  public static short dec8(short value) { return sub8(value, (short)1); }

  public static int dec16(int word) { return sub16(word, 1); }
  public static int dec16(short byte1, short byte2) {
    int word = word(byte1, byte2);
    return dec16(word);
  }

  public static Result add8(short byte1, short byte2) {
    short result = (short)(byte1 + byte2);
    result &= 0xFF; // 8 bit overflow

    boolean z = result == 0;
    boolean n = true;
    boolean h = (((byte1 & 0xF) + (byte2 & 0xF)) & 0x10) == 0x10;
     // result is less than original numbers? must be overflow
    boolean c = result < byte1 || result < byte2;

    return new Result(result, z, n, h, c);
  }

  public static int add16(int word1, int word2) {
    int result = word1 + word2;
    result &= 0xFFFF;
    return result;
  }

  public static short sub8(short byte1, short byte2) {
    short result = (short)(byte1 - byte2);
    result &= 0xFF;
    return result;
  }

  public static int sub16(int word1, int word2) {
    int result = word1 - word2;
    result &= 0xFFFF;  // 16 bit underflow
    return result;
  }


  public static void main(String args[]) {
    short arg1 = (short)0xFF;
    short arg2 = (short)0x00;

    Result result = add8(arg1, arg2);

    Util.log("arg1 == " + Util.hex(arg1));
    Util.log("arg2 == " + Util.hex(arg2));
    Util.log("### Results");
    Util.log(result.toString());

    /*
    short num = add8(arg1, arg2);

    Util.log("num:  " + Util.hex(num));
    */
  }


  /*
   * Represents the result of a math operation
   * Returns the value (either 8 or 16 bit) of the result along with flag states
   */
  public static class Result {

    private int result;
    private boolean z;
    private boolean n;
    private boolean h;
    private boolean c;

    public Result(int result, boolean z, boolean n, boolean h, boolean c) {
      this.result = result;
      this.z = z;
      this.n = n;
      this.h = h;
      this.c = c;
    }

    public int get8() { return result & 0xFF; } // force result to 8 bits
    public int get16() { return result & 0xFFFF; }  // force result to 16 bits

    public boolean getFlag(CPUState.Flag flag) {
      boolean ret = false;
      switch (flag) {
        case Z:
          ret = z;
          break;
        case N:
          ret = n;
          break;
        case H:
          ret = h;
          break;
        case C:
          ret = c;
          break;
      }
      return ret;
    }

    public String toString() {
      String str = "";

      str += Util.hex(get16());
      str += "\nz: " + z;
      str += "\nn: " + n;
      str += "\nh: " + h;
      str += "\nc: " + c;

      return str;
    }
  }
}
