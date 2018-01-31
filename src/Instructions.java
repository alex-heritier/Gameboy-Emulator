
import java.util.HashMap;

class Instructions {
  private CPUState state;
  private MMU mmu;

  public Instructions(CPUState state, MMU mmu) {
    this.state = state;
    this.mmu = mmu;
  }
  private Instructions() {
  }

  /*
   * Top level CPU instructions
   */

 // - Misc/control instructions
 //     - nop
 //     - stop
 //     - halt
 //     - DI
 //     - EI
 //     - prefix CB
 // - Jumps/calls
 //     - jp a16
 //     - jp FLAG a16
 //     - jp [HL]
 //     - jr a8
 //     - jr FLAG a8
 //     - jrn a8
 //     - jrn FLAG a8
 //     - ret
 //     - ret FLAG
 //     - retn FLAG
 //     - reti
 //     - rst a8
 //     - call a16
 //     - call FLAG a16
 //     - calln FLAG a16
 // - 8bit load/store/move instructions
 //     - ld A, B
 //     - ld A, d8
 //     - ld A, [BC]
 //     - ld A, [HL-]
 //     - ld A, [HL+]
 //     - ld [HL-], A
 //     - ld [HL+], A
 //     - ld [BC], A
 //     - ld [HL], d8
 //     - ldh [a8], A
 //     - ldh A, [a8]
 //     - ld [C], A
 //     - ld A, [C]
 //     - ld [a16], A
 //     - ld A, [a16]
 // - 16bit load/store/move instructions
 //     - ld SP, HL
 //     - ld BC, d16
 //     - ld [d16], SP
 //     - ld HL, SP + 8
 //     - pop BC
 //     - push BC
 // - 8bit arithmetic
 //     - inc B
 //     - inc [BC]
 //     - dec B
 //     - dec [BC]
 //     - daa
 //     - scf
 //     - cpl
 //     - ccf
 //     - add A, B
 //     - add A, [HL]
 //     - add A, d8
 //     - adc A, B
 //     - adc A, [HL]
 //     - adc A, d8
 //     - sub B
 //     - sub [HL]
 //     - sub d8
 //     - sbc A, B
 //     - sbc A, [HL]
 //     - sbc A, d8
 //     - cp B
 //     - cp [HL]
 //     - cp d8
 // - logical instructions
 //     - and B
 //     - and [HL]
 //     - and d8
 //     - xor B
 //     - xor [HL]
 //     - xor d8
 //     - or B
 //     - or [HL]
 //     - or d8
 // - 16bit arithmetic
 //     - inc BC
 //     - dec BC
 //     - add HL, BC
 //     - add SP, r8 
 // - 8bit rotations/shifts and bit instructions (prefix CB)
 //     - <INS> B
 //     - <INS> [HL]
 //         - <INS> = {rlc, rrc, rl, rr, sla, sra, swap, srl, bit <N>, res <N>, set <N>}
 //         - <N> = 0-7

  public void add(CPUState.R reg1, CPUState.R reg2) {
    short sum = add8(reg1, reg2);
    state.setReg(reg1, sum);
  }

  public void cp() {
    incPC();
    short byte1 = mmu.get(PC());
    short result = sub8(CPUState.R.A, byte1);

    // flags
    state.setFlag(CPUState.Flag.Z, result == 0);
    state.setFlag(CPUState.Flag.N, true);
    state.setFlag(CPUState.Flag.H, CPUMath.subWillHalfCarry(state.getReg(CPUState.R.A), byte1));
    state.setFlag(CPUState.Flag.C, CPUMath.subWillCarry(state.getReg(CPUState.R.A), byte1));
  }

  public void xor8(CPUState.R reg) {
    short result = xor8(CPUState.R.A, reg);
    state.setReg(CPUState.R.A, result);

    state.resetFlags();
    state.setFlag(CPUState.Flag.Z, result == 0);  // should always be set
  }

  // ld X, [XX]
  public void ld8_pop(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
    int address = word(reg2, reg3);
    short value = mmu.get(address);

    ld8(reg1, value);
  }
  // ld X, d8
  public void ld8(CPUState.R reg) {
    short value = 0;

    incPC();
    value = mmu.get(PC());
    ld8(reg, value);
  }
  // ld X, X
  public void ld8(CPUState.R reg1, CPUState.R reg2) {
    ld8(reg1, state.getReg(reg2));
  }
  private void ld8(CPUState.R reg, short value) {
    state.setReg(reg, value);
  }

  public void ld16_push(CPUState.R reg1, CPUState.R reg2) {
    short byte1 = 0;
    short byte2 = 0;

    incPC();
    byte1 = mmu.get(PC());
    incPC();
    byte2 = mmu.get(PC());

    int address = CPUMath.word(byte2, byte1);
    mmu.set(address, state.getReg(reg2));
    mmu.set(inc16(address), state.getReg(reg1));
  }
  // ld RR, x
  public void ld16(CPUState.R reg1, CPUState.R reg2) {
    short byte1 = 0;
    short byte2 = 0;

    incPC();
    byte1 = mmu.get(PC());
    incPC();
    byte2 = mmu.get(PC());

    ld16(reg1, reg2, byte1, byte2);
  }
  public void ld16(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3, CPUState.R reg4) {
    ld16(reg1, reg2, state.getReg(reg3), state.getReg(reg4));
  }
  private void ld16(CPUState.R reg1, CPUState.R reg2, short byte1, short byte2) {
    state.setReg16(reg1, reg2, byte1, byte2);
  }

  // LD [HL-], X
  public void ld16_hld(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
    ld16_hl(reg1, reg2, reg3);    // LD [HL], A
    state.setReg16(reg1, reg2, dec16(reg1, reg2));            // HL--
  }

  // LD [HL+], X
  public void ld16_hli(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
    ld16_hl(reg1, reg2, reg3);    // LD [HL], A
    state.setReg16(reg1, reg2, inc16(reg1, reg2));            // HL++
  }

  // LD [HL], X
  public void ld16_hl(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
    int word = word(reg1, reg2);  // word = [HL]
    mmu.set(word, state.getReg(reg3));  // LD [word], A
  }

  public void sbc(CPUState.R reg) {
    short subtractor = add8(reg, (short)(state.getFlag(CPUState.Flag.C) ? 1 : 0));
    sub(CPUState.R.A, subtractor);
  }
  public void sub(CPUState.R reg) {
    sub(CPUState.R.A, state.getReg(reg));
  }
  public void sub_pop(CPUState.R reg1, CPUState.R reg2) {
    int address = word(reg1, reg2);
    short value = mmu.get(address);

    sub(CPUState.R.A, value);
  }
  private void sub(CPUState.R reg, short value) {
    short originalValue = state.getReg(reg);
    short result = sub8(reg, value);
    state.setReg(reg, result);

    state.setFlag(CPUState.Flag.Z, result == 0);
    state.setFlag(CPUState.Flag.N, true);
    state.setFlag(CPUState.Flag.H, CPUMath.subWillHalfCarry(originalValue, value));
    state.setFlag(CPUState.Flag.C, CPUMath.subWillCarry(originalValue, value));
  }

  public void rst(int address) {
    push(PC());
    jump(0x38);
  }

  // relative jump if flag is NOT SET
  public void jrn(CPUState.Flag flag) {
    if (!state.getFlag(flag)) {
      Util.log("jrn succeeded");
      jr();
    } else { Util.log("jrn failed"); }
  }

  // relative jump if flag IS SET
  public void jr(CPUState.Flag flag) {
    if (state.getFlag(flag))
      jr();
  }

  // unconditional relative jump
  public void jr() {
    incPC();
    byte offset = (byte)mmu.get(PC());  // cast to byte so that sign matters
    int sum = add16(CPUState.R.PC_0, CPUState.R.PC_1, offset);

    Util.log("jr 0x" + Util.hex(offset));
    jump(sum);
  }

  public void CB() {
    Util.errn("CB prefixed instructions not implemented!");
    incPC();  // skip CB opcode
  }

  public void DI() {
    state.IME(false);
  }

  public void EI() {
    state.IME(true); //
  }

  public void inc(CPUState.R reg) {
    short originalValue = state.getReg(reg);
    state.setReg(reg, inc8(reg));

    state.setFlag(CPUState.Flag.Z, state.getReg(reg) == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, CPUMath.addWillHalfCarry(originalValue, (short)1));
  }

  public void inc_mem(CPUState.R reg1, CPUState.R reg2) {
    int address = word(reg1, reg2);
    short originalValue = mmu.get(address);

    mmu.set(address, inc8(originalValue));
  }

  public void inc(CPUState.R reg1, CPUState.R reg2) {
    int originalValue = word(reg1, reg2);
    state.setReg16(reg1, reg2, inc16(reg1, reg2));

    /* Apparently 16 bit increments don't affect registers */
    // state.setFlag(CPUState.Flag.Z, state.getReg(reg) == 0);
    // state.setFlag(CPUState.Flag.N, false);
    // state.setFlag(CPUState.Flag.H, CPUMath.addWillHalfCarry(originalValue, (short)1));
  }

  public void dec(CPUState.R reg) {
    short originalValue = state.getReg(reg);
    state.setReg(reg, dec8(reg));

    state.setFlag(CPUState.Flag.Z, state.getReg(reg) == 0);
    state.setFlag(CPUState.Flag.N, true);
    state.setFlag(CPUState.Flag.H, !CPUMath.subWillHalfCarry(originalValue, (short)1));
  }

  public void ld8_push(CPUState.R reg1, CPUState.R reg2) {
    int address = state.getReg(reg1);
    short value = state.getReg(reg2);

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

    mmu.set(address, state.getReg(CPUState.R.A));
  }

  public void ldh_pop() {
    incPC();
    short offset = mmu.get(PC());
    short base = (short)0xFF00;

    Util.log("ldh_pop offset == " + Util.hex(offset));
    Util.log("ldh_pop base == " + Util.hex(base));
    int address = add16(base, offset) & 0xFFFF;
    Util.log("ldh_pop pop address == " + Util.hex(address));

    state.setReg(CPUState.R.A, mmu.get(address));
  }

  public void call() {
    short byte1 = 0;
    short byte2 = 0;

    incPC();
    byte1 = mmu.get(PC());
    incPC();
    byte2 = mmu.get(PC());

    push(PC()); // store next instruction address for later

    int address = CPUMath.word(byte2, byte1);
    jump(PC());
  }

  public void ret(CPUState.Flag flag) {
    if (state.getFlag(flag)) {
      int address = pop();
      jump(address);
    }
  }


  // PC manipulators
  public int PC() { return state.getReg16(CPUState.R.PC_0, CPUState.R.PC_1); }
  public void incPC() { setPC(PC() + 1); }
  private void decPC() { setPC(PC() - 1); }
  private void setPC(int word) {
    state.setReg16(CPUState.R.PC_0, CPUState.R.PC_1, word);
  }

  // SP manipulators
  private int SP() { return state.getReg16(CPUState.R.SP_0, CPUState.R.SP_1); }
  private void incSP() { setSP(SP() + 1); }
  private void decSP() { setSP(SP() - 1); }
  private void setSP(int word) {
    state.setReg16(CPUState.R.SP_0, CPUState.R.SP_1, word);
  }

  // Logic functions
  private short or8(CPUState.R reg1, CPUState.R reg2) { return or8(reg1, state.getReg(reg2)); }
  private short or8(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    registerValue |= value;
    return registerValue;
  }

  private short xor8(CPUState.R reg1, CPUState.R reg2) { return xor8(reg1, state.getReg(reg2)); }
  private short xor8(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    registerValue ^= value;
    return registerValue;
  }

  private short and8(CPUState.R reg1, CPUState.R reg2) { return or8(reg1, state.getReg(reg2)); }
  private short and8(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    registerValue &= value;
    return registerValue;
  }

  // Arithmetic functions
  private short inc8(short value) { return add8(value, (short)1); }
  private short inc8(CPUState.R reg) { return add8(reg, (short)1); }

  private int inc16(int word) { return add16(word, 1); }
  private int inc16(CPUState.R reg1, CPUState.R reg2) {
    return add16(reg1, reg2, 1);
  }

  private short dec8(CPUState.R reg1) { return sub8(reg1, (short)1); }
  private int dec16(CPUState.R reg1, CPUState.R reg2) {
    return sub16(reg1, reg2, 1);
  }

  private short add8(CPUState.R reg1, CPUState.R reg2) { return add8(reg1, state.getReg(reg2)); }
  private short add8(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    return add8(registerValue, value);
  }
  private short add8(short byte1, short byte2) {
    short sum = (short)(byte1 + byte2);
    sum %= 0x100; // 8 bit overflow
    return sum;
  }

  private int add16(CPUState.R reg1, CPUState.R reg2, int word) {
    return add16(word(reg1, reg2), word);
  }
  private int add16(int word1, int word2) {
    int registerValue = word1 + word2;
    registerValue %= 0x10000; // 16 bit overflow
    return registerValue;
  }

  private short sub8(CPUState.R reg1, CPUState.R reg2) { return sub8(reg1, state.getReg(reg2)); }
  private short sub8(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    registerValue -= value;
    if (registerValue < 0) registerValue += 0x100;  // 8 bit underflow
    return registerValue;
  }

  private int sub16(CPUState.R reg1, CPUState.R reg2, int word) {
    return sub16(word(reg1, reg2), word);
  }
  private int sub16(int word1, int word2) {
    int registerValue = word1 + word2;
    if (registerValue < 0) registerValue += 0x10000;  // 16 bit underflow
    return registerValue;
  }

  public int word(CPUState.R reg1, CPUState.R reg2) {
    return CPUMath.word(state.getReg(reg1), state.getReg(reg2));
  }


  // Stack manipulators
  /*
  *  Pushes the word onto the stack in 2 parts
  *  example usage: PUSH BC
  *    [--SP] = B; [--SP] = C;
  */
  private void push(int word) {
    short[] bytes = CPUMath.toBytes(word);

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

    return CPUMath.word(byte2, byte1);
  }


  // Jump helpers
  private void jump(int address) {
    if (address < 0 || address > 0xFFFF) {
      Util.errn("CPUState.jump - out of bounds error: 0x" + Util.hex(address));
      return;
    }

    setPC(address);

    // TODO fix this workaround
    decPC();  // cpu.tick() to counter cpu.tick incPC()
  }
}
