
/**
 * CPU register file and status flags for the Sharp LR35902.
 *
 * Register values are stored as ints (0..0xFF for 8-bit, 0..0xFFFF for
 * 16-bit) to sidestep Java's signed {@code short} quirks while still
 * returning {@code short} from the accessors used by the rest of the
 * emulator.
 */
class CPUState {

  public enum Flag { Z, N, H, C }

  public enum R {
    A, F,
    B, C,
    D, E,
    H, L,
    SP_0, SP_1,   // SP high, SP low
    PC_0, PC_1,   // PC high, PC low
  }

  private int a, b, c, d, e, f, h, l;
  private int sp, pc;
  private boolean ime;
  private boolean halted;
  // Becomes true the cycle AFTER an EI instruction; triggers the IME set.
  private boolean eiPending;
  // True when a HALT instruction was executed while IME=0 and (IE & IF) != 0.
  // The next instruction byte read will be read twice (PC stays put for one op).
  private boolean haltBug;

  public CPUState() {
    // Values after the DMG boot ROM completes.
    a  = 0x01;
    f  = 0xB0;
    b  = 0x00;
    c  = 0x13;
    d  = 0x00;
    e  = 0xD8;
    h  = 0x01;
    l  = 0x4D;
    sp = 0xFFFE;
    pc = 0x0100;
  }

  public boolean IME() { return ime; }
  public void IME(boolean value) { ime = value; }

  public boolean isHalted() { return halted; }
  public void setHalted(boolean value) { halted = value; }

  public boolean eiPending() { return eiPending; }
  public void setEiPending(boolean value) { eiPending = value; }

  public boolean haltBug() { return haltBug; }
  public void setHaltBug(boolean value) { haltBug = value; }

  public short getReg(R reg) {
    switch (reg) {
      case A: return (short)(a & 0xFF);
      case F: return (short)(f & 0xF0);
      case B: return (short)(b & 0xFF);
      case C: return (short)(c & 0xFF);
      case D: return (short)(d & 0xFF);
      case E: return (short)(e & 0xFF);
      case H: return (short)(h & 0xFF);
      case L: return (short)(l & 0xFF);
      case SP_0: return (short)((sp >> 8) & 0xFF);
      case SP_1: return (short)(sp & 0xFF);
      case PC_0: return (short)((pc >> 8) & 0xFF);
      case PC_1: return (short)(pc & 0xFF);
    }
    return 0;
  }

  public void setReg(R reg, short value) {
    int v = value & 0xFF;
    switch (reg) {
      case A: a = v; return;
      case F: f = v & 0xF0; return;
      case B: b = v; return;
      case C: c = v; return;
      case D: d = v; return;
      case E: e = v; return;
      case H: h = v; return;
      case L: l = v; return;
      case SP_0: sp = ((v & 0xFF) << 8) | (sp & 0xFF); return;
      case SP_1: sp = (sp & 0xFF00) | (v & 0xFF); return;
      case PC_0: pc = ((v & 0xFF) << 8) | (pc & 0xFF); return;
      case PC_1: pc = (pc & 0xFF00) | (v & 0xFF); return;
    }
  }

  public int getReg16(R reg1, R reg2) {
    return ((getReg(reg1) & 0xFF) << 8) | (getReg(reg2) & 0xFF);
  }

  public void setReg16(R reg1, R reg2, int word) {
    word &= 0xFFFF;
    setReg(reg1, (short)((word >> 8) & 0xFF));
    setReg(reg2, (short)(word & 0xFF));
  }

  public void setReg16(R reg1, R reg2, short a, short b) {
    setReg(reg1, a);
    setReg(reg2, b);
  }

  public int PC() { return pc & 0xFFFF; }
  public void setPC(int word) { pc = word & 0xFFFF; }
  public void incPC() { pc = (pc + 1) & 0xFFFF; }
  public void decPC() { pc = (pc - 1) & 0xFFFF; }

  public int SP() { return sp & 0xFFFF; }
  public void setSP(int word) { sp = word & 0xFFFF; }
  public void incSP() { sp = (sp + 1) & 0xFFFF; }
  public void decSP() { sp = (sp - 1) & 0xFFFF; }

  public boolean getFlag(Flag flag) {
    switch (flag) {
      case Z: return (f & 0x80) != 0;
      case N: return (f & 0x40) != 0;
      case H: return (f & 0x20) != 0;
      case C: return (f & 0x10) != 0;
    }
    return false;
  }

  public void setFlag(Flag flag, boolean value) {
    int mask;
    switch (flag) {
      case Z: mask = 0x80; break;
      case N: mask = 0x40; break;
      case H: mask = 0x20; break;
      case C: mask = 0x10; break;
      default: return;
    }
    if (value) f |= mask;
    else       f &= ~mask;
    f &= 0xF0;
  }

  public void resetFlags() { f = 0; }
}
