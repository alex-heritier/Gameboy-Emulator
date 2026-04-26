
/**
 * 8- and 16-bit ALU helpers that also compute the Game Boy flag effects.
 *
 * Arithmetic operations return a {@link Result} carrying the reduced
 * value plus Z/N/H/C flags; the caller applies only the flags it needs.
 */
class CPUMath {
  private CPUMath() {}

  public static short[] toBytes(int word) {
    return new short[] {
      (short)((word >> 8) & 0xFF),
      (short)(word & 0xFF),
    };
  }

  public static int word(short hi, short lo) {
    return ((hi & 0xFF) << 8) | (lo & 0xFF);
  }

  // --- logic ------------------------------------------------------------
  public static Result or8(short a, short b) {
    int r = (a & 0xFF) | (b & 0xFF);
    return new Result(r, r == 0, false, false, false);
  }

  public static Result xor8(short a, short b) {
    int r = (a & 0xFF) ^ (b & 0xFF);
    return new Result(r, r == 0, false, false, false);
  }

  public static Result and8(short a, short b) {
    int r = (a & 0xFF) & (b & 0xFF);
    return new Result(r, r == 0, false, true, false);
  }

  // --- bit manipulation -------------------------------------------------
  public static int getBit(short value, int n) {
    if (n < 0 || n > 7) return 0;
    return ((value & 0xFF) >> n) & 1;
  }

  public static short setBit(short value, int n) {
    if (n < 0 || n > 7) return (short)(value & 0xFF);
    return (short)((value | (1 << n)) & 0xFF);
  }

  public static short resetBit(short value, int n) {
    if (n < 0 || n > 7) return (short)(value & 0xFF);
    return (short)((value & ~(1 << n)) & 0xFF);
  }

  // --- arithmetic -------------------------------------------------------
  public static Result inc8(short value) { return add8(value, (short)1); }
  public static Result dec8(short value) { return sub8(value, (short)1); }

  public static int inc16(int word) { return (word + 1) & 0xFFFF; }
  public static int dec16(int word) { return (word - 1) & 0xFFFF; }
  public static int inc16(short hi, short lo) { return inc16(word(hi, lo)); }
  public static int dec16(short hi, short lo) { return dec16(word(hi, lo)); }

  public static Result add8(short a, short b) {
    int x = a & 0xFF;
    int y = b & 0xFF;
    int sum = x + y;
    int result = sum & 0xFF;
    boolean h = ((x & 0xF) + (y & 0xF)) > 0xF;
    boolean c = sum > 0xFF;
    return new Result(result, result == 0, false, h, c);
  }

  public static Result adc8(short a, short b, boolean carryIn) {
    int x = a & 0xFF;
    int y = b & 0xFF;
    int cin = carryIn ? 1 : 0;
    int sum = x + y + cin;
    int result = sum & 0xFF;
    boolean h = ((x & 0xF) + (y & 0xF) + cin) > 0xF;
    boolean c = sum > 0xFF;
    return new Result(result, result == 0, false, h, c);
  }

  public static Result sub8(short a, short b) {
    int x = a & 0xFF;
    int y = b & 0xFF;
    int diff = x - y;
    int result = diff & 0xFF;
    boolean h = (x & 0xF) < (y & 0xF);
    boolean c = x < y;
    return new Result(result, result == 0, true, h, c);
  }

  public static Result sbc8(short a, short b, boolean carryIn) {
    int x = a & 0xFF;
    int y = b & 0xFF;
    int cin = carryIn ? 1 : 0;
    int diff = x - y - cin;
    int result = diff & 0xFF;
    boolean h = ((x & 0xF) - (y & 0xF) - cin) < 0;
    boolean c = diff < 0;
    return new Result(result, result == 0, true, h, c);
  }

  public static int add16(int a, int b) { return (a + b) & 0xFFFF; }
  public static int sub16(int a, int b) { return (a - b) & 0xFFFF; }

  public static Result add16Flags(int a, int b) {
    int sum = a + b;
    boolean h = ((a & 0x0FFF) + (b & 0x0FFF)) > 0x0FFF;
    boolean c = sum > 0xFFFF;
    return new Result(sum & 0xFFFF, false, false, h, c);
  }

  /** ADD SP, r8 / LD HL, SP+r8 flag computation. */
  public static Result addSp(int sp, byte offset) {
    int result = (sp + offset) & 0xFFFF;
    int u = offset & 0xFF;
    boolean h = ((sp & 0x0F) + (u & 0x0F)) > 0x0F;
    boolean c = ((sp & 0xFF) + u) > 0xFF;
    return new Result(result, false, false, h, c);
  }

  /**
   * Represents the result of a math operation.
   */
  public static class Result {
    private final int result;
    private final boolean z, n, h, c;

    public Result(int result, boolean z, boolean n, boolean h, boolean c) {
      this.result = result;
      this.z = z;
      this.n = n;
      this.h = h;
      this.c = c;
    }

    public short get8() { return (short)(result & 0xFF); }
    public int get16() { return result & 0xFFFF; }

    public boolean getFlag(CPUState.Flag flag) {
      switch (flag) {
        case Z: return z;
        case N: return n;
        case H: return h;
        case C: return c;
      }
      return false;
    }

    public String toString() {
      return "value=" + Util.hex(get16()) +
             " Z=" + (z ? 1 : 0) +
             " N=" + (n ? 1 : 0) +
             " H=" + (h ? 1 : 0) +
             " C=" + (c ? 1 : 0);
    }
  }
}
