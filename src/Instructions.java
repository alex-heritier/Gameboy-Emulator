
/**
 * Implements each opcode of the LR35902. Every public method corresponds
 * to a single opcode or a small family of opcodes; the cycle counts
 * reported via {@link ClockCounter} are in M-cycles (1 M-cycle == 4
 * T-cycles).
 *
 * Most instructions are short - grouped in the order used by
 * {@code http://www.pastraiser.com/cpu/gameboy/gameboy_opcodes.html}.
 */
class Instructions {
  private final CPUState state;
  private final MMU mmu;
  private final ClockCounter clockCounter;
  private final CBInstructions cb;

  public Instructions(CPUState state, MMU mmu, ClockCounter clockCounter) {
    this.state = state;
    this.mmu = mmu;
    this.clockCounter = clockCounter;
    this.cb = new CBInstructions(state, mmu, clockCounter);
  }

  // ===========================================================================
  // Misc / control
  // ===========================================================================

  public void nop() { clockCounter.add(1); }

  public void stop() {
    clockCounter.add(1);
    state.incPC(); // STOP is a 2-byte instruction: 0x10 0x00
  }

  public void halt() {
    clockCounter.add(1);
    if (state.IME()) {
      state.setHalted(true);
    } else {
      int pending = mmu.forceGet(MMU.IE) & mmu.forceGet(MMU.IF) & 0x1F;
      if (pending == 0) {
        state.setHalted(true);
      } else {
        // HALT bug: next instruction byte is read twice.
        state.setHaltBug(true);
      }
    }
  }

  public void DI() {
    clockCounter.add(1);
    state.IME(false);
    state.setEiPending(false);
  }

  public void EI() {
    clockCounter.add(1);
    state.setEiPending(true);
  }

  public void CB() {
    clockCounter.add(1);
    short instruction = readMem8(state.PC());
    state.incPC();
    cb.handle(instruction);
  }

  // ===========================================================================
  // Jumps / calls
  // ===========================================================================

  public void jp() {
    clockCounter.add(4);
    int address = readImmediate16();
    state.setPC(address);
  }

  public void jp(CPUState.Flag flag) {
    if (state.getFlag(flag)) jp();
    else { clockCounter.add(3); readImmediate16(); }
  }

  public void jpn(CPUState.Flag flag) {
    if (!state.getFlag(flag)) jp();
    else { clockCounter.add(3); readImmediate16(); }
  }

  public void jp(CPUState.R reg1, CPUState.R reg2) {
    clockCounter.add(1);
    state.setPC(word(reg1, reg2));
  }

  public void jr() {
    clockCounter.add(3);
    byte offset = (byte)readMem8(state.PC());
    state.incPC();
    state.setPC((state.PC() + offset) & 0xFFFF);
  }

  public void jr(CPUState.Flag flag) {
    if (state.getFlag(flag)) jr();
    else { clockCounter.add(2); state.incPC(); }
  }

  public void jrn(CPUState.Flag flag) {
    if (!state.getFlag(flag)) jr();
    else { clockCounter.add(2); state.incPC(); }
  }

  public void ret() {
    clockCounter.add(4);
    state.setPC(pop());
  }

  public void ret(CPUState.Flag flag) {
    if (state.getFlag(flag)) { clockCounter.add(1); ret(); }
    else clockCounter.add(2);
  }

  public void retn(CPUState.Flag flag) {
    if (!state.getFlag(flag)) { clockCounter.add(1); ret(); }
    else clockCounter.add(2);
  }

  public void reti() {
    clockCounter.add(4);
    state.setPC(pop());
    state.IME(true);
    state.setEiPending(false);
  }

  public void rst(int address) {
    clockCounter.add(4);
    push(state.PC());
    state.setPC(address);
  }

  public void call() {
    clockCounter.add(6);
    int address = readImmediate16();
    push(state.PC());
    state.setPC(address);
  }

  public void call(CPUState.Flag flag) {
    if (state.getFlag(flag)) call();
    else { clockCounter.add(3); readImmediate16(); }
  }

  public void calln(CPUState.Flag flag) {
    if (!state.getFlag(flag)) call();
    else { clockCounter.add(3); readImmediate16(); }
  }

  // ===========================================================================
  // 8-bit loads
  // ===========================================================================

  /** LD r, r' - 1 M-cycle. */
  public void ld8(CPUState.R dst, CPUState.R src) {
    clockCounter.add(1);
    state.setReg(dst, state.getReg(src));
  }

  /** LD r, d8 - 2 M-cycles. */
  public void ld8(CPUState.R dst) {
    clockCounter.add(2);
    short value = readMem8(state.PC());
    state.incPC();
    state.setReg(dst, value);
  }

  /** LD r, (rr) - 2 M-cycles. */
  public void ld8_load(CPUState.R dst, CPUState.R hi, CPUState.R lo) {
    clockCounter.add(2);
    state.setReg(dst, readMem8(word(hi, lo)));
  }

  /** LD A, (HL-); then HL--. */
  public void ld8_loadd(CPUState.R dst, CPUState.R hi, CPUState.R lo) {
    clockCounter.add(2);
    int hl = word(hi, lo);
    state.setReg(dst, readMem8(hl));
    state.setReg16(hi, lo, CPUMath.dec16(hl));
  }

  /** LD A, (HL+); then HL++. */
  public void ld8_loadi(CPUState.R dst, CPUState.R hi, CPUState.R lo) {
    clockCounter.add(2);
    int hl = word(hi, lo);
    state.setReg(dst, readMem8(hl));
    state.setReg16(hi, lo, CPUMath.inc16(hl));
  }

  /** LD (rr), r. */
  public void ld8_store(CPUState.R hi, CPUState.R lo, CPUState.R src) {
    clockCounter.add(2);
    writeMem8(word(hi, lo), state.getReg(src));
  }

  /** LD (HL-), A; then HL--. */
  public void ld8_stored(CPUState.R hi, CPUState.R lo, CPUState.R src) {
    clockCounter.add(2);
    int hl = word(hi, lo);
    writeMem8(hl, state.getReg(src));
    state.setReg16(hi, lo, CPUMath.dec16(hl));
  }

  /** LD (HL+), A; then HL++. */
  public void ld8_storei(CPUState.R hi, CPUState.R lo, CPUState.R src) {
    clockCounter.add(2);
    int hl = word(hi, lo);
    writeMem8(hl, state.getReg(src));
    state.setReg16(hi, lo, CPUMath.inc16(hl));
  }

  /** LD (HL), d8. */
  public void ldhl_store(CPUState.R hi, CPUState.R lo) {
    clockCounter.add(3);
    int address = word(hi, lo);
    short value = readMem8(state.PC());
    state.incPC();
    writeMem8(address, value);
  }

  /** LDH (a8), A. */
  public void ldh_store(CPUState.R src) {
    clockCounter.add(3);
    short offset = readMem8(state.PC());
    state.incPC();
    writeMem8(0xFF00 + (offset & 0xFF), state.getReg(src));
  }

  /** LDH A, (a8). */
  public void ldh_load(CPUState.R dst) {
    clockCounter.add(3);
    short offset = readMem8(state.PC());
    state.incPC();
    state.setReg(dst, readMem8(0xFF00 + (offset & 0xFF)));
  }

  /** LD (C), A. */
  public void ld8_store(CPUState.R reg1, CPUState.R reg2) {
    clockCounter.add(2);
    writeMem8(0xFF00 + (state.getReg(reg1) & 0xFF), state.getReg(reg2));
  }

  /** LD A, (C). */
  public void ld8_load(CPUState.R dst, CPUState.R offsetReg) {
    clockCounter.add(2);
    state.setReg(dst, readMem8(0xFF00 + (state.getReg(offsetReg) & 0xFF)));
  }

  /** LD (a16), A. */
  public void ld8_store(CPUState.R src) {
    clockCounter.add(4);
    int address = readImmediate16();
    writeMem8(address, state.getReg(src));
  }

  /** LD A, (a16). */
  public void ld8_load(CPUState.R dst) {
    clockCounter.add(4);
    int address = readImmediate16();
    state.setReg(dst, readMem8(address));
  }

  // ===========================================================================
  // 16-bit loads
  // ===========================================================================

  /** LD SP, HL. */
  public void ld16(CPUState.R dstHi, CPUState.R dstLo, CPUState.R srcHi, CPUState.R srcLo) {
    clockCounter.add(2);
    state.setReg16(dstHi, dstLo, state.getReg(srcHi), state.getReg(srcLo));
  }

  /** LD rr, d16. */
  public void ld16(CPUState.R hi, CPUState.R lo) {
    clockCounter.add(3);
    int word = readImmediate16();
    state.setReg16(hi, lo, word);
  }

  /** LD (a16), SP. */
  public void ld16_store(CPUState.R hi, CPUState.R lo) {
    clockCounter.add(5);
    int address = readImmediate16();
    writeMem16(address, word(hi, lo));
  }

  /** LD HL, SP+r8. */
  public void ld_pop() {
    clockCounter.add(3);
    byte offset = (byte)readMem8(state.PC());
    state.incPC();
    CPUMath.Result result = CPUMath.addSp(state.SP(), offset);
    state.setReg16(CPUState.R.H, CPUState.R.L, result.get16());
    state.setFlag(CPUState.Flag.Z, false);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, result.getFlag(CPUState.Flag.H));
    state.setFlag(CPUState.Flag.C, result.getFlag(CPUState.Flag.C));
  }

  public void pop(CPUState.R hi, CPUState.R lo) {
    clockCounter.add(3);
    state.setReg16(hi, lo, pop());
  }

  public void push(CPUState.R hi, CPUState.R lo) {
    clockCounter.add(4);
    push(word(hi, lo));
  }

  // ===========================================================================
  // 8-bit arithmetic
  // ===========================================================================

  public void inc(CPUState.R reg) {
    clockCounter.add(1);
    CPUMath.Result r = CPUMath.inc8(state.getReg(reg));
    state.setReg(reg, r.get8());
    applyIncFlags(r);
  }

  public void inc_mem(CPUState.R hi, CPUState.R lo) {
    // INC (HL) - 3 M-cycles: fetch, read-on-M2, write-on-M3.
    int address = word(hi, lo);
    clockCounter.add(2);
    CPUMath.Result r = CPUMath.inc8(readMem8(address));
    clockCounter.add(1);
    writeMem8(address, r.get8());
    applyIncFlags(r);
  }

  public void dec(CPUState.R reg) {
    clockCounter.add(1);
    CPUMath.Result r = CPUMath.dec8(state.getReg(reg));
    state.setReg(reg, r.get8());
    applyDecFlags(r);
  }

  public void dec_mem(CPUState.R hi, CPUState.R lo) {
    // DEC (HL) - 3 M-cycles: fetch, read-on-M2, write-on-M3.
    int address = word(hi, lo);
    clockCounter.add(2);
    CPUMath.Result r = CPUMath.dec8(readMem8(address));
    clockCounter.add(1);
    writeMem8(address, r.get8());
    applyDecFlags(r);
  }

  /** Canonical DAA after ADD/ADC/SUB/SBC. */
  public void daa() {
    clockCounter.add(1);
    int a = state.getReg(CPUState.R.A) & 0xFF;
    boolean n = state.getFlag(CPUState.Flag.N);
    boolean h = state.getFlag(CPUState.Flag.H);
    boolean c = state.getFlag(CPUState.Flag.C);

    if (!n) {
      if (c || a > 0x99) { a += 0x60; c = true; }
      if (h || (a & 0x0F) > 0x09) { a += 0x06; }
    } else {
      if (c) { a -= 0x60; }
      if (h) { a -= 0x06; }
    }
    a &= 0xFF;

    state.setReg(CPUState.R.A, (short)a);
    state.setFlag(CPUState.Flag.Z, a == 0);
    state.setFlag(CPUState.Flag.H, false);
    state.setFlag(CPUState.Flag.C, c);
  }

  public void scf() {
    clockCounter.add(1);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, false);
    state.setFlag(CPUState.Flag.C, true);
  }

  public void cpl() {
    clockCounter.add(1);
    short a = (short)(~state.getReg(CPUState.R.A) & 0xFF);
    state.setReg(CPUState.R.A, a);
    state.setFlag(CPUState.Flag.N, true);
    state.setFlag(CPUState.Flag.H, true);
  }

  public void ccf() {
    clockCounter.add(1);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, false);
    state.setFlag(CPUState.Flag.C, !state.getFlag(CPUState.Flag.C));
  }

  public void add(CPUState.R dst, CPUState.R src) {
    clockCounter.add(1);
    applyAdd(dst, state.getReg(src));
  }

  public void add(CPUState.R dst, CPUState.R hi, CPUState.R lo) {
    clockCounter.add(2);
    applyAdd(dst, readMem8(word(hi, lo)));
  }

  public void add(CPUState.R dst) {
    clockCounter.add(2);
    short value = readMem8(state.PC());
    state.incPC();
    applyAdd(dst, value);
  }

  public void adc(CPUState.R dst, CPUState.R src) {
    clockCounter.add(1);
    applyAdc(dst, state.getReg(src));
  }

  public void adc(CPUState.R dst, CPUState.R hi, CPUState.R lo) {
    clockCounter.add(2);
    applyAdc(dst, readMem8(word(hi, lo)));
  }

  public void adc(CPUState.R dst) {
    clockCounter.add(2);
    short value = readMem8(state.PC());
    state.incPC();
    applyAdc(dst, value);
  }

  public void sub(CPUState.R src) {
    clockCounter.add(1);
    applySub(state.getReg(src));
  }

  public void sub(CPUState.R hi, CPUState.R lo) {
    clockCounter.add(2);
    applySub(readMem8(word(hi, lo)));
  }

  public void sub() {
    clockCounter.add(2);
    short value = readMem8(state.PC());
    state.incPC();
    applySub(value);
  }

  public void sbc(CPUState.R src) {
    clockCounter.add(1);
    applySbc(state.getReg(src));
  }

  public void sbc(CPUState.R hi, CPUState.R lo) {
    clockCounter.add(2);
    applySbc(readMem8(word(hi, lo)));
  }

  public void sbc() {
    clockCounter.add(2);
    short value = readMem8(state.PC());
    state.incPC();
    applySbc(value);
  }

  public void cp(CPUState.R src) {
    clockCounter.add(1);
    applyCp(state.getReg(src));
  }

  public void cp(CPUState.R hi, CPUState.R lo) {
    clockCounter.add(2);
    applyCp(readMem8(word(hi, lo)));
  }

  public void cp() {
    clockCounter.add(2);
    short value = readMem8(state.PC());
    state.incPC();
    applyCp(value);
  }

  // ===========================================================================
  // Logical
  // ===========================================================================

  public void and(CPUState.R src) { clockCounter.add(1); applyAnd(state.getReg(src)); }
  public void and(CPUState.R hi, CPUState.R lo) { clockCounter.add(2); applyAnd(readMem8(word(hi, lo))); }
  public void and() {
    clockCounter.add(2);
    short v = readMem8(state.PC());
    state.incPC();
    applyAnd(v);
  }

  public void xor(CPUState.R src) { clockCounter.add(1); applyXor(state.getReg(src)); }
  public void xor(CPUState.R hi, CPUState.R lo) { clockCounter.add(2); applyXor(readMem8(word(hi, lo))); }
  public void xor() {
    clockCounter.add(2);
    short v = readMem8(state.PC());
    state.incPC();
    applyXor(v);
  }

  public void or(CPUState.R src) { clockCounter.add(1); applyOr(state.getReg(src)); }
  public void or(CPUState.R hi, CPUState.R lo) { clockCounter.add(2); applyOr(readMem8(word(hi, lo))); }
  public void or() {
    clockCounter.add(2);
    short v = readMem8(state.PC());
    state.incPC();
    applyOr(v);
  }

  // ===========================================================================
  // Rotates that act on register A (flags differ from CB-prefixed variants)
  // ===========================================================================

  public void rlca() {
    clockCounter.add(1);
    int a = state.getReg(CPUState.R.A) & 0xFF;
    int bit7 = (a >> 7) & 1;
    int r = ((a << 1) | bit7) & 0xFF;
    state.setReg(CPUState.R.A, (short)r);
    state.resetFlags();
    state.setFlag(CPUState.Flag.C, bit7 != 0);
  }

  public void rla() {
    clockCounter.add(1);
    int a = state.getReg(CPUState.R.A) & 0xFF;
    int bit7 = (a >> 7) & 1;
    int carryIn = state.getFlag(CPUState.Flag.C) ? 1 : 0;
    int r = ((a << 1) | carryIn) & 0xFF;
    state.setReg(CPUState.R.A, (short)r);
    state.resetFlags();
    state.setFlag(CPUState.Flag.C, bit7 != 0);
  }

  public void rrca() {
    clockCounter.add(1);
    int a = state.getReg(CPUState.R.A) & 0xFF;
    int bit0 = a & 1;
    int r = ((a >> 1) | (bit0 << 7)) & 0xFF;
    state.setReg(CPUState.R.A, (short)r);
    state.resetFlags();
    state.setFlag(CPUState.Flag.C, bit0 != 0);
  }

  public void rra() {
    clockCounter.add(1);
    int a = state.getReg(CPUState.R.A) & 0xFF;
    int bit0 = a & 1;
    int carryIn = state.getFlag(CPUState.Flag.C) ? 1 : 0;
    int r = ((a >> 1) | (carryIn << 7)) & 0xFF;
    state.setReg(CPUState.R.A, (short)r);
    state.resetFlags();
    state.setFlag(CPUState.Flag.C, bit0 != 0);
  }

  // ===========================================================================
  // 16-bit arithmetic
  // ===========================================================================

  public void inc(CPUState.R hi, CPUState.R lo) {
    clockCounter.add(2);
    state.setReg16(hi, lo, CPUMath.inc16(word(hi, lo)));
  }

  public void dec(CPUState.R hi, CPUState.R lo) {
    clockCounter.add(2);
    state.setReg16(hi, lo, CPUMath.dec16(word(hi, lo)));
  }

  /** ADD HL, rr. */
  public void add16(CPUState.R dstHi, CPUState.R dstLo, CPUState.R srcHi, CPUState.R srcLo) {
    clockCounter.add(2);
    int dst = word(dstHi, dstLo);
    int src = word(srcHi, srcLo);
    CPUMath.Result r = CPUMath.add16Flags(dst, src);
    state.setReg16(dstHi, dstLo, r.get16());
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, r.getFlag(CPUState.Flag.H));
    state.setFlag(CPUState.Flag.C, r.getFlag(CPUState.Flag.C));
  }

  /** ADD SP, r8. */
  public void add16(CPUState.R dstHi, CPUState.R dstLo) {
    clockCounter.add(4);
    byte offset = (byte)readMem8(state.PC());
    state.incPC();
    int sp = word(dstHi, dstLo);
    CPUMath.Result r = CPUMath.addSp(sp, offset);
    state.setReg16(dstHi, dstLo, r.get16());
    state.setFlag(CPUState.Flag.Z, false);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, r.getFlag(CPUState.Flag.H));
    state.setFlag(CPUState.Flag.C, r.getFlag(CPUState.Flag.C));
  }

  // ===========================================================================
  // Shared helpers
  // ===========================================================================

  private void applyIncFlags(CPUMath.Result r) {
    state.setFlag(CPUState.Flag.Z, r.get8() == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, r.getFlag(CPUState.Flag.H));
  }

  private void applyDecFlags(CPUMath.Result r) {
    state.setFlag(CPUState.Flag.Z, r.get8() == 0);
    state.setFlag(CPUState.Flag.N, true);
    state.setFlag(CPUState.Flag.H, r.getFlag(CPUState.Flag.H));
  }

  private void applyAdd(CPUState.R dst, short value) {
    CPUMath.Result r = CPUMath.add8(state.getReg(dst), value);
    state.setReg(dst, r.get8());
    state.setFlag(CPUState.Flag.Z, r.get8() == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, r.getFlag(CPUState.Flag.H));
    state.setFlag(CPUState.Flag.C, r.getFlag(CPUState.Flag.C));
  }

  private void applyAdc(CPUState.R dst, short value) {
    CPUMath.Result r = CPUMath.adc8(state.getReg(dst), value, state.getFlag(CPUState.Flag.C));
    state.setReg(dst, r.get8());
    state.setFlag(CPUState.Flag.Z, r.get8() == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, r.getFlag(CPUState.Flag.H));
    state.setFlag(CPUState.Flag.C, r.getFlag(CPUState.Flag.C));
  }

  private void applySub(short value) {
    CPUMath.Result r = CPUMath.sub8(state.getReg(CPUState.R.A), value);
    state.setReg(CPUState.R.A, r.get8());
    state.setFlag(CPUState.Flag.Z, r.get8() == 0);
    state.setFlag(CPUState.Flag.N, true);
    state.setFlag(CPUState.Flag.H, r.getFlag(CPUState.Flag.H));
    state.setFlag(CPUState.Flag.C, r.getFlag(CPUState.Flag.C));
  }

  private void applySbc(short value) {
    CPUMath.Result r = CPUMath.sbc8(state.getReg(CPUState.R.A), value, state.getFlag(CPUState.Flag.C));
    state.setReg(CPUState.R.A, r.get8());
    state.setFlag(CPUState.Flag.Z, r.get8() == 0);
    state.setFlag(CPUState.Flag.N, true);
    state.setFlag(CPUState.Flag.H, r.getFlag(CPUState.Flag.H));
    state.setFlag(CPUState.Flag.C, r.getFlag(CPUState.Flag.C));
  }

  private void applyCp(short value) {
    CPUMath.Result r = CPUMath.sub8(state.getReg(CPUState.R.A), value);
    state.setFlag(CPUState.Flag.Z, r.get8() == 0);
    state.setFlag(CPUState.Flag.N, true);
    state.setFlag(CPUState.Flag.H, r.getFlag(CPUState.Flag.H));
    state.setFlag(CPUState.Flag.C, r.getFlag(CPUState.Flag.C));
  }

  private void applyAnd(short value) {
    CPUMath.Result r = CPUMath.and8(state.getReg(CPUState.R.A), value);
    state.setReg(CPUState.R.A, r.get8());
    state.setFlag(CPUState.Flag.Z, r.getFlag(CPUState.Flag.Z));
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, true);
    state.setFlag(CPUState.Flag.C, false);
  }

  private void applyXor(short value) {
    CPUMath.Result r = CPUMath.xor8(state.getReg(CPUState.R.A), value);
    state.setReg(CPUState.R.A, r.get8());
    state.resetFlags();
    state.setFlag(CPUState.Flag.Z, r.getFlag(CPUState.Flag.Z));
  }

  private void applyOr(short value) {
    CPUMath.Result r = CPUMath.or8(state.getReg(CPUState.R.A), value);
    state.setReg(CPUState.R.A, r.get8());
    state.resetFlags();
    state.setFlag(CPUState.Flag.Z, r.getFlag(CPUState.Flag.Z));
  }

  // Stack and memory helpers
  public void push(int word) {
    state.decSP();
    writeMem8(state.SP(), (short)((word >> 8) & 0xFF));
    state.decSP();
    writeMem8(state.SP(), (short)(word & 0xFF));
  }

  private int pop() {
    short lo = readMem8(state.SP()); state.incSP();
    short hi = readMem8(state.SP()); state.incSP();
    return CPUMath.word(hi, lo);
  }

  private int readImmediate16() {
    short lo = readMem8(state.PC()); state.incPC();
    short hi = readMem8(state.PC()); state.incPC();
    return CPUMath.word(hi, lo);
  }

  private void writeMem8(int address, short value) { mmu.set(address, value); }
  private short readMem8(int address) { return mmu.get(address); }

  private void writeMem16(int address, int word) {
    writeMem8(address, (short)(word & 0xFF));
    writeMem8((address + 1) & 0xFFFF, (short)((word >> 8) & 0xFF));
  }

  private int word(CPUState.R hi, CPUState.R lo) {
    return CPUMath.word(state.getReg(hi), state.getReg(lo));
  }
}
