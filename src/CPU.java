
/**
 * Orchestrates the LR35902 fetch/decode/execute loop, the five hardware
 * interrupts, and the two quirks at the heart of Blargg's cpu_instrs:
 *   * The HALT bug  - if HALT runs with IME=0 and an interrupt pending,
 *                     the next instruction byte is read twice.
 *   * EI delay      - EI only sets IME after one more instruction runs.
 */
class CPU {

  private final CPUState state;
  private final MMU mmu;
  private final ClockCounter clockCounter;
  private final Instructions ins;

  public CPU(CPUState state, MMU mmu, Cart cart, ClockCounter clockCounter) {
    this.state = state;
    this.mmu = mmu;
    this.clockCounter = clockCounter;
    this.ins = new Instructions(state, mmu, clockCounter);
  }

  /** Execute one instruction (or service a pending interrupt). */
  public void tick() {
    if (serviceInterrupts()) return;

    if (state.isHalted()) {
      clockCounter.add(1);
      return;
    }

    boolean eiWasPending = state.eiPending();

    int pc = state.PC();
    short instruction = mmu.get(pc);
    short nb = mmu.get((pc + 1) & 0xFFFF);
    short nnb = mmu.get((pc + 2) & 0xFFFF);
    Util.debug(Util.hex(pc) + "\t" + Util.hex(instruction) + "\t"
               + Util.mnemonic(instruction, nb, nnb));

    state.incPC();
    if (state.haltBug()) {
      state.setHaltBug(false);
      state.decPC();
    }

    decode(instruction);

    if (eiWasPending) {
      state.IME(true);
      state.setEiPending(false);
    }
  }

  /**
   * Returns true if an interrupt was serviced this tick.
   *
   * Even with IME=0, a pending interrupt wakes up a halted CPU - the
   * interrupt is left pending so the handler fires once IME is set.
   */
  private boolean serviceInterrupts() {
    int pending = mmu.forceGet(MMU.IE) & mmu.forceGet(MMU.IF) & 0x1F;
    if (pending == 0) return false;

    state.setHalted(false);

    if (!state.IME()) return false;

    for (int i = 0; i <= 4; i++) {
      if ((pending & (1 << i)) == 0) continue;

      state.IME(false);
      state.setEiPending(false);
      short flags = (short)(mmu.forceGet(MMU.IF) & ~(1 << i) & 0xFF);
      mmu.forceSet(MMU.IF, flags);

      ins.push(state.PC());
      state.setPC(0x40 + 8 * i);

      Util.debug("### interrupt " + Util.getInterruptName(i));
      clockCounter.add(5);
      return true;
    }
    return false;
  }

  public void dump() {
    Util.log();
    Util.log("AF\t" + Util.hex(state.getReg16(CPUState.R.A, CPUState.R.F)));
    Util.log("BC\t" + Util.hex(state.getReg16(CPUState.R.B, CPUState.R.C)));
    Util.log("DE\t" + Util.hex(state.getReg16(CPUState.R.D, CPUState.R.E)));
    Util.log("HL\t" + Util.hex(state.getReg16(CPUState.R.H, CPUState.R.L)));
    Util.log("SP\t" + Util.hex(state.SP()));
    Util.log("PC\t" + Util.hex(state.PC()));
    Util.log("Z\t" + (state.getFlag(CPUState.Flag.Z) ? 1 : 0));
    Util.log("N\t" + (state.getFlag(CPUState.Flag.N) ? 1 : 0));
    Util.log("H\t" + (state.getFlag(CPUState.Flag.H) ? 1 : 0));
    Util.log("C\t" + (state.getFlag(CPUState.Flag.C) ? 1 : 0));
    Util.log();
  }

  public short checksum() {
    short value = 0;
    value = CPUMath.add8(value, state.getReg(CPUState.R.A)).get8();
    value = CPUMath.add8(value, state.getReg(CPUState.R.F)).get8();
    value = CPUMath.add8(value, state.getReg(CPUState.R.B)).get8();
    value = CPUMath.add8(value, state.getReg(CPUState.R.C)).get8();
    value = CPUMath.add8(value, state.getReg(CPUState.R.D)).get8();
    value = CPUMath.add8(value, state.getReg(CPUState.R.E)).get8();
    value = CPUMath.add8(value, state.getReg(CPUState.R.H)).get8();
    value = CPUMath.add8(value, state.getReg(CPUState.R.L)).get8();
    value = CPUMath.add8(value, state.getReg(CPUState.R.PC_0)).get8();
    value = CPUMath.add8(value, state.getReg(CPUState.R.PC_1)).get8();
    value = CPUMath.add8(value, state.getReg(CPUState.R.SP_0)).get8();
    value = CPUMath.add8(value, state.getReg(CPUState.R.SP_1)).get8();
    return value;
  }

  private void decode(short instruction) {
    switch ((int) instruction) {
      case 0x00: ins.nop(); break;
      case 0x01: ins.ld16(CPUState.R.B, CPUState.R.C); break;
      case 0x02: ins.ld8_store(CPUState.R.B, CPUState.R.C, CPUState.R.A); break;
      case 0x03: ins.inc(CPUState.R.B, CPUState.R.C); break;
      case 0x04: ins.inc(CPUState.R.B); break;
      case 0x05: ins.dec(CPUState.R.B); break;
      case 0x06: ins.ld8(CPUState.R.B); break;
      case 0x07: ins.rlca(); break;
      case 0x08: ins.ld16_store(CPUState.R.SP_0, CPUState.R.SP_1); break;
      case 0x09: ins.add16(CPUState.R.H, CPUState.R.L, CPUState.R.B, CPUState.R.C); break;
      case 0x0A: ins.ld8_load(CPUState.R.A, CPUState.R.B, CPUState.R.C); break;
      case 0x0B: ins.dec(CPUState.R.B, CPUState.R.C); break;
      case 0x0C: ins.inc(CPUState.R.C); break;
      case 0x0D: ins.dec(CPUState.R.C); break;
      case 0x0E: ins.ld8(CPUState.R.C); break;
      case 0x0F: ins.rrca(); break;

      case 0x10: ins.stop(); break;
      case 0x11: ins.ld16(CPUState.R.D, CPUState.R.E); break;
      case 0x12: ins.ld8_store(CPUState.R.D, CPUState.R.E, CPUState.R.A); break;
      case 0x13: ins.inc(CPUState.R.D, CPUState.R.E); break;
      case 0x14: ins.inc(CPUState.R.D); break;
      case 0x15: ins.dec(CPUState.R.D); break;
      case 0x16: ins.ld8(CPUState.R.D); break;
      case 0x17: ins.rla(); break;
      case 0x18: ins.jr(); break;
      case 0x19: ins.add16(CPUState.R.H, CPUState.R.L, CPUState.R.D, CPUState.R.E); break;
      case 0x1A: ins.ld8_load(CPUState.R.A, CPUState.R.D, CPUState.R.E); break;
      case 0x1B: ins.dec(CPUState.R.D, CPUState.R.E); break;
      case 0x1C: ins.inc(CPUState.R.E); break;
      case 0x1D: ins.dec(CPUState.R.E); break;
      case 0x1E: ins.ld8(CPUState.R.E); break;
      case 0x1F: ins.rra(); break;

      case 0x20: ins.jrn(CPUState.Flag.Z); break;
      case 0x21: ins.ld16(CPUState.R.H, CPUState.R.L); break;
      case 0x22: ins.ld8_storei(CPUState.R.H, CPUState.R.L, CPUState.R.A); break;
      case 0x23: ins.inc(CPUState.R.H, CPUState.R.L); break;
      case 0x24: ins.inc(CPUState.R.H); break;
      case 0x25: ins.dec(CPUState.R.H); break;
      case 0x26: ins.ld8(CPUState.R.H); break;
      case 0x27: ins.daa(); break;
      case 0x28: ins.jr(CPUState.Flag.Z); break;
      case 0x29: ins.add16(CPUState.R.H, CPUState.R.L, CPUState.R.H, CPUState.R.L); break;
      case 0x2A: ins.ld8_loadi(CPUState.R.A, CPUState.R.H, CPUState.R.L); break;
      case 0x2B: ins.dec(CPUState.R.H, CPUState.R.L); break;
      case 0x2C: ins.inc(CPUState.R.L); break;
      case 0x2D: ins.dec(CPUState.R.L); break;
      case 0x2E: ins.ld8(CPUState.R.L); break;
      case 0x2F: ins.cpl(); break;

      case 0x30: ins.jrn(CPUState.Flag.C); break;
      case 0x31: ins.ld16(CPUState.R.SP_0, CPUState.R.SP_1); break;
      case 0x32: ins.ld8_stored(CPUState.R.H, CPUState.R.L, CPUState.R.A); break;
      case 0x33: ins.inc(CPUState.R.SP_0, CPUState.R.SP_1); break;
      case 0x34: ins.inc_mem(CPUState.R.H, CPUState.R.L); break;
      case 0x35: ins.dec_mem(CPUState.R.H, CPUState.R.L); break;
      case 0x36: ins.ldhl_store(CPUState.R.H, CPUState.R.L); break;
      case 0x37: ins.scf(); break;
      case 0x38: ins.jr(CPUState.Flag.C); break;
      case 0x39: ins.add16(CPUState.R.H, CPUState.R.L, CPUState.R.SP_0, CPUState.R.SP_1); break;
      case 0x3A: ins.ld8_loadd(CPUState.R.A, CPUState.R.H, CPUState.R.L); break;
      case 0x3B: ins.dec(CPUState.R.SP_0, CPUState.R.SP_1); break;
      case 0x3C: ins.inc(CPUState.R.A); break;
      case 0x3D: ins.dec(CPUState.R.A); break;
      case 0x3E: ins.ld8(CPUState.R.A); break;
      case 0x3F: ins.ccf(); break;

      // 0x40..0x7F: LD r, r / HALT
      case 0x40: ins.ld8(CPUState.R.B, CPUState.R.B); break;
      case 0x41: ins.ld8(CPUState.R.B, CPUState.R.C); break;
      case 0x42: ins.ld8(CPUState.R.B, CPUState.R.D); break;
      case 0x43: ins.ld8(CPUState.R.B, CPUState.R.E); break;
      case 0x44: ins.ld8(CPUState.R.B, CPUState.R.H); break;
      case 0x45: ins.ld8(CPUState.R.B, CPUState.R.L); break;
      case 0x46: ins.ld8_load(CPUState.R.B, CPUState.R.H, CPUState.R.L); break;
      case 0x47: ins.ld8(CPUState.R.B, CPUState.R.A); break;
      case 0x48: ins.ld8(CPUState.R.C, CPUState.R.B); break;
      case 0x49: ins.ld8(CPUState.R.C, CPUState.R.C); break;
      case 0x4A: ins.ld8(CPUState.R.C, CPUState.R.D); break;
      case 0x4B: ins.ld8(CPUState.R.C, CPUState.R.E); break;
      case 0x4C: ins.ld8(CPUState.R.C, CPUState.R.H); break;
      case 0x4D: ins.ld8(CPUState.R.C, CPUState.R.L); break;
      case 0x4E: ins.ld8_load(CPUState.R.C, CPUState.R.H, CPUState.R.L); break;
      case 0x4F: ins.ld8(CPUState.R.C, CPUState.R.A); break;
      case 0x50: ins.ld8(CPUState.R.D, CPUState.R.B); break;
      case 0x51: ins.ld8(CPUState.R.D, CPUState.R.C); break;
      case 0x52: ins.ld8(CPUState.R.D, CPUState.R.D); break;
      case 0x53: ins.ld8(CPUState.R.D, CPUState.R.E); break;
      case 0x54: ins.ld8(CPUState.R.D, CPUState.R.H); break;
      case 0x55: ins.ld8(CPUState.R.D, CPUState.R.L); break;
      case 0x56: ins.ld8_load(CPUState.R.D, CPUState.R.H, CPUState.R.L); break;
      case 0x57: ins.ld8(CPUState.R.D, CPUState.R.A); break;
      case 0x58: ins.ld8(CPUState.R.E, CPUState.R.B); break;
      case 0x59: ins.ld8(CPUState.R.E, CPUState.R.C); break;
      case 0x5A: ins.ld8(CPUState.R.E, CPUState.R.D); break;
      case 0x5B: ins.ld8(CPUState.R.E, CPUState.R.E); break;
      case 0x5C: ins.ld8(CPUState.R.E, CPUState.R.H); break;
      case 0x5D: ins.ld8(CPUState.R.E, CPUState.R.L); break;
      case 0x5E: ins.ld8_load(CPUState.R.E, CPUState.R.H, CPUState.R.L); break;
      case 0x5F: ins.ld8(CPUState.R.E, CPUState.R.A); break;
      case 0x60: ins.ld8(CPUState.R.H, CPUState.R.B); break;
      case 0x61: ins.ld8(CPUState.R.H, CPUState.R.C); break;
      case 0x62: ins.ld8(CPUState.R.H, CPUState.R.D); break;
      case 0x63: ins.ld8(CPUState.R.H, CPUState.R.E); break;
      case 0x64: ins.ld8(CPUState.R.H, CPUState.R.H); break;
      case 0x65: ins.ld8(CPUState.R.H, CPUState.R.L); break;
      case 0x66: ins.ld8_load(CPUState.R.H, CPUState.R.H, CPUState.R.L); break;
      case 0x67: ins.ld8(CPUState.R.H, CPUState.R.A); break;
      case 0x68: ins.ld8(CPUState.R.L, CPUState.R.B); break;
      case 0x69: ins.ld8(CPUState.R.L, CPUState.R.C); break;
      case 0x6A: ins.ld8(CPUState.R.L, CPUState.R.D); break;
      case 0x6B: ins.ld8(CPUState.R.L, CPUState.R.E); break;
      case 0x6C: ins.ld8(CPUState.R.L, CPUState.R.H); break;
      case 0x6D: ins.ld8(CPUState.R.L, CPUState.R.L); break;
      case 0x6E: ins.ld8_load(CPUState.R.L, CPUState.R.H, CPUState.R.L); break;
      case 0x6F: ins.ld8(CPUState.R.L, CPUState.R.A); break;
      case 0x70: ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.B); break;
      case 0x71: ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.C); break;
      case 0x72: ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.D); break;
      case 0x73: ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.E); break;
      case 0x74: ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.H); break;
      case 0x75: ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.L); break;
      case 0x76: ins.halt(); break;
      case 0x77: ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.A); break;
      case 0x78: ins.ld8(CPUState.R.A, CPUState.R.B); break;
      case 0x79: ins.ld8(CPUState.R.A, CPUState.R.C); break;
      case 0x7A: ins.ld8(CPUState.R.A, CPUState.R.D); break;
      case 0x7B: ins.ld8(CPUState.R.A, CPUState.R.E); break;
      case 0x7C: ins.ld8(CPUState.R.A, CPUState.R.H); break;
      case 0x7D: ins.ld8(CPUState.R.A, CPUState.R.L); break;
      case 0x7E: ins.ld8_load(CPUState.R.A, CPUState.R.H, CPUState.R.L); break;
      case 0x7F: ins.ld8(CPUState.R.A, CPUState.R.A); break;

      // 0x80..0xBF: ALU ops
      case 0x80: ins.add(CPUState.R.A, CPUState.R.B); break;
      case 0x81: ins.add(CPUState.R.A, CPUState.R.C); break;
      case 0x82: ins.add(CPUState.R.A, CPUState.R.D); break;
      case 0x83: ins.add(CPUState.R.A, CPUState.R.E); break;
      case 0x84: ins.add(CPUState.R.A, CPUState.R.H); break;
      case 0x85: ins.add(CPUState.R.A, CPUState.R.L); break;
      case 0x86: ins.add(CPUState.R.A, CPUState.R.H, CPUState.R.L); break;
      case 0x87: ins.add(CPUState.R.A, CPUState.R.A); break;
      case 0x88: ins.adc(CPUState.R.A, CPUState.R.B); break;
      case 0x89: ins.adc(CPUState.R.A, CPUState.R.C); break;
      case 0x8A: ins.adc(CPUState.R.A, CPUState.R.D); break;
      case 0x8B: ins.adc(CPUState.R.A, CPUState.R.E); break;
      case 0x8C: ins.adc(CPUState.R.A, CPUState.R.H); break;
      case 0x8D: ins.adc(CPUState.R.A, CPUState.R.L); break;
      case 0x8E: ins.adc(CPUState.R.A, CPUState.R.H, CPUState.R.L); break;
      case 0x8F: ins.adc(CPUState.R.A, CPUState.R.A); break;
      case 0x90: ins.sub(CPUState.R.B); break;
      case 0x91: ins.sub(CPUState.R.C); break;
      case 0x92: ins.sub(CPUState.R.D); break;
      case 0x93: ins.sub(CPUState.R.E); break;
      case 0x94: ins.sub(CPUState.R.H); break;
      case 0x95: ins.sub(CPUState.R.L); break;
      case 0x96: ins.sub(CPUState.R.H, CPUState.R.L); break;
      case 0x97: ins.sub(CPUState.R.A); break;
      case 0x98: ins.sbc(CPUState.R.B); break;
      case 0x99: ins.sbc(CPUState.R.C); break;
      case 0x9A: ins.sbc(CPUState.R.D); break;
      case 0x9B: ins.sbc(CPUState.R.E); break;
      case 0x9C: ins.sbc(CPUState.R.H); break;
      case 0x9D: ins.sbc(CPUState.R.L); break;
      case 0x9E: ins.sbc(CPUState.R.H, CPUState.R.L); break;
      case 0x9F: ins.sbc(CPUState.R.A); break;
      case 0xA0: ins.and(CPUState.R.B); break;
      case 0xA1: ins.and(CPUState.R.C); break;
      case 0xA2: ins.and(CPUState.R.D); break;
      case 0xA3: ins.and(CPUState.R.E); break;
      case 0xA4: ins.and(CPUState.R.H); break;
      case 0xA5: ins.and(CPUState.R.L); break;
      case 0xA6: ins.and(CPUState.R.H, CPUState.R.L); break;
      case 0xA7: ins.and(CPUState.R.A); break;
      case 0xA8: ins.xor(CPUState.R.B); break;
      case 0xA9: ins.xor(CPUState.R.C); break;
      case 0xAA: ins.xor(CPUState.R.D); break;
      case 0xAB: ins.xor(CPUState.R.E); break;
      case 0xAC: ins.xor(CPUState.R.H); break;
      case 0xAD: ins.xor(CPUState.R.L); break;
      case 0xAE: ins.xor(CPUState.R.H, CPUState.R.L); break;
      case 0xAF: ins.xor(CPUState.R.A); break;
      case 0xB0: ins.or(CPUState.R.B); break;
      case 0xB1: ins.or(CPUState.R.C); break;
      case 0xB2: ins.or(CPUState.R.D); break;
      case 0xB3: ins.or(CPUState.R.E); break;
      case 0xB4: ins.or(CPUState.R.H); break;
      case 0xB5: ins.or(CPUState.R.L); break;
      case 0xB6: ins.or(CPUState.R.H, CPUState.R.L); break;
      case 0xB7: ins.or(CPUState.R.A); break;
      case 0xB8: ins.cp(CPUState.R.B); break;
      case 0xB9: ins.cp(CPUState.R.C); break;
      case 0xBA: ins.cp(CPUState.R.D); break;
      case 0xBB: ins.cp(CPUState.R.E); break;
      case 0xBC: ins.cp(CPUState.R.H); break;
      case 0xBD: ins.cp(CPUState.R.L); break;
      case 0xBE: ins.cp(CPUState.R.H, CPUState.R.L); break;
      case 0xBF: ins.cp(CPUState.R.A); break;

      // 0xC0..0xFF: control + misc
      case 0xC0: ins.retn(CPUState.Flag.Z); break;
      case 0xC1: ins.pop(CPUState.R.B, CPUState.R.C); break;
      case 0xC2: ins.jpn(CPUState.Flag.Z); break;
      case 0xC3: ins.jp(); break;
      case 0xC4: ins.calln(CPUState.Flag.Z); break;
      case 0xC5: ins.push(CPUState.R.B, CPUState.R.C); break;
      case 0xC6: ins.add(CPUState.R.A); break;
      case 0xC7: ins.rst(0x00); break;
      case 0xC8: ins.ret(CPUState.Flag.Z); break;
      case 0xC9: ins.ret(); break;
      case 0xCA: ins.jp(CPUState.Flag.Z); break;
      case 0xCB: ins.CB(); break;
      case 0xCC: ins.call(CPUState.Flag.Z); break;
      case 0xCD: ins.call(); break;
      case 0xCE: ins.adc(CPUState.R.A); break;
      case 0xCF: ins.rst(0x08); break;
      case 0xD0: ins.retn(CPUState.Flag.C); break;
      case 0xD1: ins.pop(CPUState.R.D, CPUState.R.E); break;
      case 0xD2: ins.jpn(CPUState.Flag.C); break;
      case 0xD3: missing(instruction); break;
      case 0xD4: ins.calln(CPUState.Flag.C); break;
      case 0xD5: ins.push(CPUState.R.D, CPUState.R.E); break;
      case 0xD6: ins.sub(); break;
      case 0xD7: ins.rst(0x10); break;
      case 0xD8: ins.ret(CPUState.Flag.C); break;
      case 0xD9: ins.reti(); break;
      case 0xDA: ins.jp(CPUState.Flag.C); break;
      case 0xDB: missing(instruction); break;
      case 0xDC: ins.call(CPUState.Flag.C); break;
      case 0xDD: missing(instruction); break;
      case 0xDE: ins.sbc(); break;
      case 0xDF: ins.rst(0x18); break;
      case 0xE0: ins.ldh_store(CPUState.R.A); break;
      case 0xE1: ins.pop(CPUState.R.H, CPUState.R.L); break;
      case 0xE2: ins.ld8_store(CPUState.R.C, CPUState.R.A); break;
      case 0xE3: missing(instruction); break;
      case 0xE4: missing(instruction); break;
      case 0xE5: ins.push(CPUState.R.H, CPUState.R.L); break;
      case 0xE6: ins.and(); break;
      case 0xE7: ins.rst(0x20); break;
      case 0xE8: ins.add16(CPUState.R.SP_0, CPUState.R.SP_1); break;
      case 0xE9: ins.jp(CPUState.R.H, CPUState.R.L); break;
      case 0xEA: ins.ld8_store(CPUState.R.A); break;
      case 0xEB: missing(instruction); break;
      case 0xEC: missing(instruction); break;
      case 0xED: missing(instruction); break;
      case 0xEE: ins.xor(); break;
      case 0xEF: ins.rst(0x28); break;
      case 0xF0: ins.ldh_load(CPUState.R.A); break;
      case 0xF1: ins.pop(CPUState.R.A, CPUState.R.F); break;
      case 0xF2: ins.ld8_load(CPUState.R.A, CPUState.R.C); break;
      case 0xF3: ins.DI(); break;
      case 0xF4: missing(instruction); break;
      case 0xF5: ins.push(CPUState.R.A, CPUState.R.F); break;
      case 0xF6: ins.or(); break;
      case 0xF7: ins.rst(0x30); break;
      case 0xF8: ins.ld_pop(); break;
      case 0xF9: ins.ld16(CPUState.R.SP_0, CPUState.R.SP_1, CPUState.R.H, CPUState.R.L); break;
      case 0xFA: ins.ld8_load(CPUState.R.A); break;
      case 0xFB: ins.EI(); break;
      case 0xFC: missing(instruction); break;
      case 0xFD: missing(instruction); break;
      case 0xFE: ins.cp(); break;
      case 0xFF: ins.rst(0x38); break;
      default:
        Util.errn("CPU.decode - defaulted on instruction: " + Util.hex(instruction));
        break;
    }
  }

  private void missing(short instruction) {
    Util.errn("Attempted to run missing instruction 0x" + Util.hex(instruction));
  }
}
