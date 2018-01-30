
import java.util.HashMap;

class InstructionHelper {
  private HashMap<CPU.Register, Short> registers;
  private MMU mmu;

  // TODO move IME out of here
  private boolean IME;  // Interrupt Master Enable

  public InstructionHelper(HashMap<CPU.Register, Short> registers, MMU mmu) {
    this.registers = registers;
    this.mmu = mmu;
  }
  private InstructionHelper() {
  }

  /*
   * Top level CPU instructions
   */

  public void cp() {
    incPC();
    short byte1 = mmu.get(PC());
    short result = sub8(CPU.Register.A, byte1);

    // flags
    setFlag(CPU.Flag.Z, result == 0);
    setFlag(CPU.Flag.N, true);
    setFlag(CPU.Flag.H, subHasHalfCarry(getReg(CPU.Register.A), byte1));
    setFlag(CPU.Flag.C, subHasCarry(getReg(CPU.Register.A), byte1));
  }

  public void xor8(CPU.Register reg) {
    short result = xor8(CPU.Register.A, reg);
    setReg(CPU.Register.A, result);

    resetFlags();
    setFlag(CPU.Flag.Z, result == 0);  // should always be set
  }

  // ld R, x
  public void ld8(CPU.Register reg) {
    short value = 0;

    incPC();
    value = mmu.get(PC());
    ld8(reg, value);
  }
  public void ld8(CPU.Register reg1, CPU.Register reg2) {
    ld8(reg1, getReg(reg2));
  }
  private void ld8(CPU.Register reg, short value) {
    setReg(reg, value);
  }

  // ld RR, x
  public void ld16(CPU.Register reg1, CPU.Register reg2) {
    short byte1 = 0;
    short byte2 = 0;

    incPC();
    byte1 = mmu.get(PC());
    incPC();
    byte2 = mmu.get(PC());

    ld16(reg1, reg2, byte1, byte2);
  }
  private void ld16(CPU.Register reg1, CPU.Register reg2, short byte1, short byte2) {
      setReg16(reg1, reg2, byte1, byte2);
  }

  // LD [HL-], X
  public void ld16_hli(CPU.Register reg1, CPU.Register reg2, CPU.Register reg3) {
    int word = word(reg1, reg2);  // word = [HL]
    mmu.set(word, getReg(reg3));  // LD [word], A
    dec16(reg1, reg2);            // HL--
  }

  public void sbc(CPU.Register reg) {
    short originalValue = getReg(reg);
    short subtractor = add8(reg, (short)(getFlag(CPU.Flag.C) ? 1 : 0));
    short result = sub8(CPU.Register.A, subtractor);

    setFlag(CPU.Flag.Z, result == 0);
    setFlag(CPU.Flag.N, true);
    setFlag(CPU.Flag.H, subHasHalfCarry(originalValue, subtractor));
    setFlag(CPU.Flag.C, subHasCarry(originalValue, subtractor));
  }

  public void rst(int address) {
    push(PC());
    jump(0x38);
  }

  // relative jump is flag is NOT SET
  public void jrn(CPU.Flag flag) {
    incPC();
    byte offset = (byte)mmu.get(PC());  // cast to byte so that sign matters

    Util.log("jrn 0x" + Util.hex(offset));

    if (getFlag(flag)) {
      Util.log("jrn succeeded");
      add16(CPU.Register.PC_0, CPU.Register.PC_1, offset);
    } else {
        Util.log("jrn failed");
    }
  }

  public void CB() {
    Util.errn("CB prefixed instructions not implemented!");
    incPC();  // skip CB opcode
  }

  public void EI() {
    IME = true; //
  }

  /*
  * Register helpers
  */

  // 8 bit manipulators
  private short getReg(CPU.Register reg) { return registers.get(reg); }
  private void setReg(CPU.Register reg, short value) {
    if (value < 0 || value > 0xFF) {
      Util.errn("CPU.setReg - out of bounds: 0x" + Util.hex(value));
    }

    registers.put(reg, value);
  }

  // 16 bit manipulators
  private int getReg16(CPU.Register reg1, CPU.Register reg2) { return word(getReg(reg1), getReg(reg2)); }
  private void setReg16(CPU.Register reg1, CPU.Register reg2, int word) {
    if (word < 0 || word > 0xFFFF) {
      Util.errn("CPU.setReg16 - out of bounds: 0x" + Util.hex(word));
      return;
    }
    short bytes[] = toBytes(word);
    setReg16(reg1, reg2, bytes[0], bytes[1]);
  }
  private void setReg16(CPU.Register reg1, CPU.Register reg2, short a, short b) {
    setReg(reg1, a);
    setReg(reg2, b);
  }

  private void dec16(CPU.Register reg1, CPU.Register reg2) {
    sub16(reg1, reg2, 1);
  }

  // PC manipulators
  public int PC() { return getReg16(CPU.Register.PC_0, CPU.Register.PC_1); }
  public void incPC() { setPC(PC() + 1); }
  private void decPC() { setPC(PC() - 1); }
  private void setPC(int word) {
    setReg16(CPU.Register.PC_0, CPU.Register.PC_1, word);
  }

  // SP manipulators
  private int SP() { return getReg16(CPU.Register.SP_0, CPU.Register.SP_1); }
  private void incSP() { setSP(SP() + 1); }
  private void decSP() { setSP(SP() - 1); }
  private void setSP(int word) {
    setReg16(CPU.Register.SP_0, CPU.Register.SP_1, word);
  }

  // Logic functions
  private short or8(CPU.Register reg1, CPU.Register reg2) { return or8(reg1, getReg(reg2)); }
  private short or8(CPU.Register reg, short value) {
    short registerValue = getReg(reg);
    registerValue |= value;
    return registerValue;
  }

  private short xor8(CPU.Register reg1, CPU.Register reg2) { return xor8(reg1, getReg(reg2)); }
  private short xor8(CPU.Register reg, short value) {
    short registerValue = getReg(reg);
    registerValue ^= value;
    return registerValue;
  }

  private short and8(CPU.Register reg1, CPU.Register reg2) { return or8(reg1, getReg(reg2)); }
  private short and8(CPU.Register reg, short value) {
    short registerValue = getReg(reg);
    registerValue &= value;
    return registerValue;
  }

  // Arithmetic functions
  private short add8(CPU.Register reg1, CPU.Register reg2) { return add8(reg1, getReg(reg2)); }
  private short add8(CPU.Register reg, short value) {
    short registerValue = getReg(reg);
    registerValue += value;
    registerValue %= 0x100; // 8 bit overflow
    return registerValue;
  }

  private int add16(CPU.Register reg1, CPU.Register reg2, int word) {
    int registerValue = word(reg1, reg2);
    registerValue += word;
    registerValue %= 0x10000; // 16 bit overflow
    return registerValue;
  }

  private short sub8(CPU.Register reg1, CPU.Register reg2) { return sub8(reg1, getReg(reg2)); }
  private short sub8(CPU.Register reg, short value) {
    short registerValue = getReg(reg);
    registerValue -= value;
    if (registerValue < 0) registerValue += 0x100;  // 8 bit underflow
    return registerValue;
  }

  private int sub16(CPU.Register reg1, CPU.Register reg2, int word) {
    int registerValue = word(reg1, reg2);
    registerValue -= word;
    if (registerValue < 0) registerValue += 0x10000;  // 16 bit underflow
    return registerValue;
  }


  /*
  * Flag helpers
  */
  private void setFlag(CPU.Flag flag, boolean value) {
    short newFlags = 0;

    if (flag == CPU.Flag.Z) {
      if (value)  newFlags = or8(CPU.Register.F, (short)0x80);
      else        newFlags = and8(CPU.Register.F, (short)0x7F);
    }
    else if (flag == CPU.Flag.N) {
      if (value)  newFlags = or8(CPU.Register.F, (short)0x40);
      else        newFlags = and8(CPU.Register.F, (short)0xBF);
    }
    else if (flag == CPU.Flag.H) {
      if (value)  newFlags = or8(CPU.Register.F, (short)0x20);
      else        newFlags = and8(CPU.Register.F, (short)0xDF);
    }
    else if (flag == CPU.Flag.C) {
      if (value)  newFlags = or8(CPU.Register.F, (short)0x10);
      else        newFlags = and8(CPU.Register.F, (short)0xEF);
    }

    setReg(CPU.Register.F, newFlags);
  }

  private boolean getFlag(CPU.Flag flag) {
    boolean val = false;

    if (flag == CPU.Flag.Z) {
      val = and8(CPU.Register.F, (short)0x80) > 0;
    }
    else if (flag == CPU.Flag.N) {
      val = and8(CPU.Register.F, (short)0x40) > 0;
    }
    else if (flag == CPU.Flag.H) {
      val = and8(CPU.Register.F, (short)0x20) > 0;
    }
    else if (flag == CPU.Flag.C) {
      val = and8(CPU.Register.F, (short)0x10) > 0;
    }
    else {
      Util.errn("CPU.getFlag - bad flag " + flag);
    }

    return val;
  }

  private void resetFlags() {
    setReg(CPU.Register.F, (short)0);  // set all flags to 0
  }

  private static short[] toBytes(int word) {
    short[] bytes = new short[2];

    bytes[0] = (short)((word & 0xFF00) >> 8);
    bytes[1] = (short)(word & 0xFF);

    return bytes;
  }

  private int word(CPU.Register reg1, CPU.Register reg2) {
    return word(getReg(reg1), getReg(reg2));
  }
  private static int word(short a, short b) {
    return (((int)a) << 8) + b;
  }

  private static boolean addHasHalfCarry(short a, short b) {
    return (((a & 0xF) + (b & 0xF)) & 0x10) == 0x10;
  }

  private static boolean subHasHalfCarry(short a, short b) {
    return ((a & 0xF) - (b & 0xF)) < 0;
  }

  private static boolean subHasCarry(short a, short b) {
    return a < b;
  }


  // Stack manipulators
  /*
  *  Pushes the word onto the stack in 2 parts
  *  example usage: PUSH BC
  *    [--SP] = B; [--SP] = C;
  */
  private void push(int word) {
    short[] bytes = toBytes(word);

    decSP();  // SP--
    mmu.set(SP(), bytes[0]);  // [SP] = byte1

    decSP();
    mmu.set(SP(), bytes[1]);
  }

  private int pop() {
    short byte1 = 0;
    short byte2 = 0;

    byte1 = mmu.get(SP());
    incSP();
    byte2 = mmu.get(SP());
    incSP();

    return word(byte2, byte1);
  }


  // Jump helpers
  private void jump(int address) {
    if (address < 0 || address > 0xFFFF) {
      Util.errn("CPU.jump - out of bounds error: 0x" + Util.hex(address));
      return;
    }

    setPC(address);
  }
}
