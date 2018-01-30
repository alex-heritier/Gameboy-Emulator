
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

  public void add(CPU.Register reg1, CPU.Register reg2) {
    short sum = add8(reg1, reg2);
    setReg(reg1, sum);
  }

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

  // ld X, [XX]
  public void ld8_pop(CPU.Register reg1, CPU.Register reg2, CPU.Register reg3) {
    int address = word(reg2, reg3);
    short value = mmu.get(address);

    ld8(reg1, value);
  }
  // ld X, d8
  public void ld8(CPU.Register reg) {
    short value = 0;

    incPC();
    value = mmu.get(PC());
    ld8(reg, value);
  }
  // ld X, X
  public void ld8(CPU.Register reg1, CPU.Register reg2) {
    ld8(reg1, getReg(reg2));
  }
  private void ld8(CPU.Register reg, short value) {
    setReg(reg, value);
  }

  public void ld16_push(CPU.Register reg1, CPU.Register reg2) {
    short byte1 = 0;
    short byte2 = 0;

    incPC();
    byte1 = mmu.get(PC());
    incPC();
    byte2 = mmu.get(PC());

    int address = word(byte2, byte1);
    mmu.set(address, getReg(reg2));
    mmu.set(inc16(address), getReg(reg1));
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
  public void ld16(CPU.Register reg1, CPU.Register reg2, CPU.Register reg3, CPU.Register reg4) {
    ld16(reg1, reg2, getReg(reg3), getReg(reg4));
  }
  private void ld16(CPU.Register reg1, CPU.Register reg2, short byte1, short byte2) {
    setReg16(reg1, reg2, byte1, byte2);
  }

  // LD [HL-], X
  public void ld16_hld(CPU.Register reg1, CPU.Register reg2, CPU.Register reg3) {
    ld16_hl(reg1, reg2, reg3);    // LD [HL], A
    setReg16(reg1, reg2, dec16(reg1, reg2));            // HL--
  }

  // LD [HL+], X
  public void ld16_hli(CPU.Register reg1, CPU.Register reg2, CPU.Register reg3) {
    ld16_hl(reg1, reg2, reg3);    // LD [HL], A
    setReg16(reg1, reg2, inc16(reg1, reg2));            // HL++
  }

  // LD [HL], X
  public void ld16_hl(CPU.Register reg1, CPU.Register reg2, CPU.Register reg3) {
    int word = word(reg1, reg2);  // word = [HL]
    mmu.set(word, getReg(reg3));  // LD [word], A
  }

  public void sbc(CPU.Register reg) {
    short subtractor = add8(reg, (short)(getFlag(CPU.Flag.C) ? 1 : 0));
    sub(CPU.Register.A, subtractor);
  }
  public void sub(CPU.Register reg) {
    sub(CPU.Register.A, getReg(reg));
  }
  public void sub_pop(CPU.Register reg1, CPU.Register reg2) {
    int address = word(reg1, reg2);
    short value = mmu.get(address);

    sub(CPU.Register.A, value);
  }
  private void sub(CPU.Register reg, short value) {
    short originalValue = getReg(reg);
    short result = sub8(reg, value);
    setReg(reg, result);

    setFlag(CPU.Flag.Z, result == 0);
    setFlag(CPU.Flag.N, true);
    setFlag(CPU.Flag.H, subHasHalfCarry(originalValue, value));
    setFlag(CPU.Flag.C, subHasCarry(originalValue, value));
  }

  public void rst(int address) {
    push(PC());
    jump(0x38);
  }

  // relative jump if flag is NOT SET
  public void jrn(CPU.Flag flag) {
    if (!getFlag(flag)) {
      Util.log("jrn succeeded");
      jr();
    } else { Util.log("jrn failed"); }
  }

  // relative jump if flag IS SET
  public void jr(CPU.Flag flag) {
    if (getFlag(flag))
      jr();
  }

  // unconditional relative jump
  public void jr() {
    incPC();
    byte offset = (byte)mmu.get(PC());  // cast to byte so that sign matters
    int sum = add16(CPU.Register.PC_0, CPU.Register.PC_1, offset);

    Util.log("jr 0x" + Util.hex(offset));
    jump(sum);
  }

  public void CB() {
    Util.errn("CB prefixed instructions not implemented!");
    incPC();  // skip CB opcode
  }

  public void DI() {
    IME = false;
  }

  public void EI() {
    IME = true; //
  }

  public void inc(CPU.Register reg) {
    short originalValue = getReg(reg);
    setReg(reg, inc8(reg));

    setFlag(CPU.Flag.Z, getReg(reg) == 0);
    setFlag(CPU.Flag.N, false);
    setFlag(CPU.Flag.H, addHasHalfCarry(originalValue, (short)1));
  }

  public void inc_mem(CPU.Register reg1, CPU.Register reg2) {
    int address = word(reg1, reg2);
    short originalValue = mmu.get(address);

    mmu.set(address, inc8(originalValue));
  }

  public void inc(CPU.Register reg1, CPU.Register reg2) {
    int originalValue = word(reg1, reg2);
    setReg16(reg1, reg2, inc16(reg1, reg2));

    /* Apparently 16 bit increments don't affect registers */
    // setFlag(CPU.Flag.Z, getReg(reg) == 0);
    // setFlag(CPU.Flag.N, false);
    // setFlag(CPU.Flag.H, addHasHalfCarry(originalValue, (short)1));
  }

  public void dec(CPU.Register reg) {
    short originalValue = getReg(reg);
    setReg(reg, dec8(reg));

    setFlag(CPU.Flag.Z, getReg(reg) == 0);
    setFlag(CPU.Flag.N, true);
    setFlag(CPU.Flag.H, !subHasHalfCarry(originalValue, (short)1));
  }

  public void ld8_push(CPU.Register reg1, CPU.Register reg2) {
    int address = getReg(reg1);
    short value = getReg(reg2);

    mmu.set(address, value);
  }

  public void ldh_push() {
    incPC();
    short offset = mmu.get(PC());
    short base = (short)0xFF00;

    Util.log("ldh_push offset == " + Util.hex(offset));
    Util.log("ldh_push base == " + Util.hex(base));
    int address = add16(base, offset) & 0xFFFF;
    Util.log("ldh_push push address == " + Util.hex(address));

    mmu.set(address, getReg(CPU.Register.A));
  }

  public void ldh_pop() {
    incPC();
    short offset = mmu.get(PC());
    short base = (short)0xFF00;

    Util.log("ldh_pop offset == " + Util.hex(offset));
    Util.log("ldh_pop base == " + Util.hex(base));
    int address = add16(base, offset) & 0xFFFF;
    Util.log("ldh_pop pop address == " + Util.hex(address));

    setReg(CPU.Register.A, mmu.get(address));
  }

  public void call() {
    short byte1 = 0;
    short byte2 = 0;

    incPC();
    byte1 = mmu.get(PC());
    incPC();
    byte2 = mmu.get(PC());

    push(PC()); // store next instruction address for later

    int address = word(byte2, byte1);
    jump(PC());
  }

  public void ret(CPU.Flag flag) {
    if (getFlag(flag)) {
      int address = pop();
      jump(address);
    }
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
  private short inc8(short value) { return add8(value, (short)1); }
  private short inc8(CPU.Register reg) { return add8(reg, (short)1); }

  private int inc16(int word) { return add16(word, 1); }
  private int inc16(CPU.Register reg1, CPU.Register reg2) {
    return add16(reg1, reg2, 1);
  }

  private short dec8(CPU.Register reg1) { return sub8(reg1, (short)1); }
  private int dec16(CPU.Register reg1, CPU.Register reg2) {
    return sub16(reg1, reg2, 1);
  }

  private short add8(CPU.Register reg1, CPU.Register reg2) { return add8(reg1, getReg(reg2)); }
  private short add8(CPU.Register reg, short value) {
    short registerValue = getReg(reg);
    return add8(registerValue, value);
  }
  private short add8(short byte1, short byte2) {
    short sum = (short)(byte1 + byte2);
    sum %= 0x100; // 8 bit overflow
    return sum;
  }

  private int add16(CPU.Register reg1, CPU.Register reg2, int word) {
    return add16(word(reg1, reg2), word);
  }
  private int add16(int word1, int word2) {
    int registerValue = word1 + word2;
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
    return sub16(word(reg1, reg2), word);
  }
  private int sub16(int word1, int word2) {
    int registerValue = word1 + word2;
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

    // TODO fix this workaround
    decPC();  // cpu.tick() to counter cpu.tick incPC()
  }
}
