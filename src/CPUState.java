
import java.util.HashMap;

class CPUState {

  public enum Flag {
    Z,  // zero
    N,  // add/sub
    H,  // half carry
    C   // carry
  };

  public enum R { // registers
    A, F,
    B, C,
    D, E,
    H, L,
    SP_0, SP_1, // 16 bit register, so two parts
    PC_0, PC_1  // same
  }

  private HashMap<R, Short> registers;
  private boolean IME;  // Interrupt Master Enable

  public CPUState() {
    registers = new HashMap<R, Short>();

    registers.put(R.A, (short)0);
    registers.put(R.F, (short)0);
    registers.put(R.B, (short)0);
    registers.put(R.C, (short)0);
    registers.put(R.D, (short)0);
    registers.put(R.E, (short)0);
    registers.put(R.H, (short)0);
    registers.put(R.L, (short)0);
    registers.put(R.SP_0, (short)0);
    registers.put(R.SP_1, (short)0);
    registers.put(R.PC_0, (short)0);
    registers.put(R.PC_1, (short)0);
  }

  public boolean IME() { return IME; }
  public void IME(boolean value) { IME = value; }

  /*
   * Register helpers
   */

  // 8 bit manipulators
  public short getReg(R reg) { return registers.get(reg); }
  public void setReg(R reg, short value) {
    if (value < 0 || value > 0xFF) {
      Util.errn("CPU.setReg - out of bounds: 0x" + Util.hex(value));
    }

    registers.put(reg, value);
  }

  // 16 bit manipulators
  public int getReg16(R reg1, R reg2) { return CPUMath.word(getReg(reg1), getReg(reg2)); }
  public void setReg16(R reg1, R reg2, int word) {
    if (word < 0 || word > 0xFFFF) {
      Util.errn("CPU.setReg16 - out of bounds: 0x" + Util.hex(word));
      return;
    }
    short bytes[] = CPUMath.toBytes(word);
    setReg16(reg1, reg2, bytes[0], bytes[1]);
  }
  public void setReg16(R reg1, R reg2, short a, short b) {
    setReg(reg1, a);
    setReg(reg2, b);
  }

  // PC manipulators
  public int PC() { return getReg16(CPUState.R.PC_0, CPUState.R.PC_1); }
  public void incPC() {
    short byte1 = getReg(CPUState.R.PC_0);
    short byte2 = getReg(CPUState.R.PC_1);
    setPC(CPUMath.inc16(byte1, byte2));
  }
  public void decPC() {
    short byte1 = getReg(CPUState.R.PC_0);
    short byte2 = getReg(CPUState.R.PC_1);
    setPC(CPUMath.dec16(byte1, byte2));
  }
  public void setPC(int word) {
    setReg16(CPUState.R.PC_0, CPUState.R.PC_1, word);
  }

  // SP manipulators
  public int SP() { return getReg16(CPUState.R.SP_0, CPUState.R.SP_1); }
  public void incSP() { setSP(SP() + 1); }
  public void decSP() { setSP(SP() - 1); }
  public void setSP(int word) {
    setReg16(CPUState.R.SP_0, CPUState.R.SP_1, word);
  }


  /*
  * Flag helpers
  */
  public void setFlag(CPUState.Flag flag, boolean value) {
    short newFlags = 0;

    short currentFlags = getReg(CPUState.R.F);
    if (flag == CPUState.Flag.Z) {
      if (value)  newFlags = CPUMath.or8(currentFlags, (short)0x80).get8();
      else        newFlags = CPUMath.and8(currentFlags, (short)0x7F).get8();
    }
    else if (flag == CPUState.Flag.N) {
      if (value)  newFlags = CPUMath.or8(currentFlags, (short)0x40).get8();
      else        newFlags = CPUMath.and8(currentFlags, (short)0xBF).get8();
    }
    else if (flag == CPUState.Flag.H) {
      if (value)  newFlags = CPUMath.or8(currentFlags, (short)0x20).get8();
      else        newFlags = CPUMath.and8(currentFlags, (short)0xDF).get8();
    }
    else if (flag == CPUState.Flag.C) {
      if (value)  newFlags = CPUMath.or8(currentFlags, (short)0x10).get8();
      else        newFlags = CPUMath.and8(currentFlags, (short)0xEF).get8();
    }

    setReg(CPUState.R.F, newFlags);
  }

  public boolean getFlag(CPUState.Flag flag) {
    boolean val = false;

    short currentFlags = getReg(CPUState.R.F);
    if (flag == CPUState.Flag.Z) {
      val = CPUMath.and8(currentFlags, (short)0x80).get8() > 0;
    }
    else if (flag == CPUState.Flag.N) {
      val = CPUMath.and8(currentFlags, (short)0x40).get8() > 0;
    }
    else if (flag == CPUState.Flag.H) {
      val = CPUMath.and8(currentFlags, (short)0x20).get8() > 0;
    }
    else if (flag == CPUState.Flag.C) {
      val = CPUMath.and8(currentFlags, (short)0x10).get8() > 0;
    }
    else {
      Util.errn("CPUState.getFlag - bad flag " + flag);
    }

    return val;
  }

  public void resetFlags() {
    setReg(CPUState.R.F, (short)0);  // set all flags to 0
  }
}
