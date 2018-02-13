
import java.util.HashMap;

class CPU {

  private CPUState state;
  private MMU mmu;
  private Cart cart;
  private ClockCounter clockCounter;
  private Instructions ins;

  public CPU(CPUState state, MMU mmu, Cart cart, ClockCounter clockCounter) {
    this.state = state;
    this.mmu = mmu;
    this.cart = cart;
    this.clockCounter = clockCounter;
    this.ins = new Instructions(state, mmu, clockCounter);

    loadROM();
  }

  // Load MMU's romBank00 and romBank01 with cart data
  private void loadROM() {
    for (int i = 0; i < cart.size(); i++) {
      mmu.set(i, cart.get(i));
    }
    mmu.log(true);
  }

  // Run instruction at mmu[PC]
  public void tick() {
    short instruction = mmu.get(state.PC());
    short nextByte = mmu.get(CPUMath.inc16(state.PC()));
    short nextNextByte = mmu.get(CPUMath.inc16(CPUMath.inc16(state.PC())));
    Util.debug(Util.hex(state.PC()) + "\t" + Util.hex(instruction) + "\t" + Util.mnemonic(instruction, nextByte, nextNextByte));

    handleInterrupts();

    if (state.isHalted()) {
      Util.log("CPU.tick - Halted...");
      clockCounter.add(1);
      return;
    }

    instruction = mmu.get(state.PC());  // in case there was an interrupt
    state.incPC();
    decode(instruction);
  }

  public void dump() {
    Util.log();
    Util.log("AF\t" + Util.hex(state.getReg16(CPUState.R.A, CPUState.R.F)));
    Util.log("BC\t" + Util.hex(state.getReg16(CPUState.R.B, CPUState.R.C)));
    Util.log("DE\t" + Util.hex(state.getReg16(CPUState.R.D, CPUState.R.E)));
    Util.log("HL\t" + Util.hex(state.getReg16(CPUState.R.H, CPUState.R.L)));
    Util.log("SP\t" + Util.hex(state.getReg16(CPUState.R.SP_0, CPUState.R.SP_1)));
    Util.log("PC\t" + Util.hex(state.getReg16(CPUState.R.PC_0, CPUState.R.PC_1)));
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

  private void handleInterrupts() {
    int interruptFlagsAddress = 0xFF0F;
    short interruptFlags = mmu.get(interruptFlagsAddress);

    // Util.log("INTERRUPT FLAGS - " + Util.hex(interruptFlags));

    // Return if there aren't any interrupts
    if (interruptFlags == 0) return;

    // Always un-halt the CPU if there is a pending interrupt
    state.setHalted(false);

    // Return if interrupts are disabled
    if (!state.IME()) return;

    for (int i = 0; i <= 4; i++) {
      int bit = CPUMath.getBit(interruptFlags, i);
      int ierFlag = CPUMath.getBit(mmu.get(0xFFFF), i);

      if (bit > 0 && ierFlag > 0) {

        Util.log("INTERRUPT - " + i);

        // turn off interrupt flag
        interruptFlags = CPUMath.resetBit(interruptFlags, i);
        mmu.set(interruptFlagsAddress, interruptFlags);

        int interruptAddress = 0x40 + 8 * i;
        Util.log("Interrupt address - " + Util.hex(interruptAddress));

        ins.push(CPUState.R.PC_0, CPUState.R.PC_1);
        state.setPC(interruptAddress);  // jump to interrupt handler
        state.IME(false); // prevents more interrupts from occuring

        short newInstruction = mmu.get(interruptAddress);
        short data1 = mmu.get(interruptAddress + 1);
        short data2 = mmu.get(interruptAddress + 2);
        Util.log("\n### New instruction - " + Util.mnemonic(newInstruction, data1, data2));

        // Timing
        clockCounter.add(5);  // 20 / 4

        break;
      }
    }
  }

  /*
   * Instruction helpers
   */
  private void decode(short instruction) {
    switch ((int) instruction) {  // cast to remove lossy conversion errors
      case 0x00:  // NOP
        ins.nop();
        break;
      case 0x01:  // LD BC,d16
        ins.ld16(CPUState.R.B, CPUState.R.C);
        break;
      case 0x02:  // LD [BC], A
        ins.ld8_store(CPUState.R.B, CPUState.R.C, CPUState.R.A);
        break;
      case 0x03:  // INC BC
        ins.inc(CPUState.R.B, CPUState.R.C);
        break;
      case 0x04:  // INC B
        ins.inc(CPUState.R.B);
        break;
      case 0x05:  // DEC B
        ins.dec(CPUState.R.B);
        break;
      case 0x06:  // LD B,d8
        ins.ld8(CPUState.R.B);
        break;
      case 0x07:  // RLCA
        ins.rlca();
        break;
      case 0x08:  // LD (a16),SP
        ins.ld16_store(CPUState.R.SP_0, CPUState.R.SP_1);
        break;
      case 0x09:  // ADD HL,BC
        ins.add16(CPUState.R.H, CPUState.R.L, CPUState.R.B, CPUState.R.C);
        break;
      case 0x0A:  // LD A,[BC]
        ins.ld8_load(CPUState.R.A, CPUState.R.B, CPUState.R.C);
        break;
      case 0x0B:  // DEC BC
        ins.dec(CPUState.R.B, CPUState.R.C);
        break;
      case 0x0C:  // INC C
        ins.inc(CPUState.R.C);
        break;
      case 0x0D:  // DEC C
        ins.dec(CPUState.R.C);
        break;
      case 0x0E:  // LD C,d8
        ins.ld8(CPUState.R.C);
        break;
      case 0x0F:  // RRCA
        ins.rrca();
        break;
      case 0x10:  // STOP 0
        ins.stop();
        break;
      case 0x11:  // LD DE,d16
        ins.ld16(CPUState.R.D, CPUState.R.E);
        break;
      case 0x12:  // LD [DE],A
        ins.ld8_store(CPUState.R.D, CPUState.R.E, CPUState.R.A);
        break;
      case 0x13:  // INC DE
        ins.inc(CPUState.R.D, CPUState.R.E);
        break;
      case 0x14:  // INC D
        ins.inc(CPUState.R.D);
        break;
      case 0x15:  // DEC D
        ins.dec(CPUState.R.D);
        break;
      case 0x16:  // LD D,d8
        ins.ld8(CPUState.R.D);
        break;
      case 0x17:  // RLA
        ins.rla();
        break;
      case 0x18:  // JR r8
        ins.jr();
        break;
      case 0x19:  // LD HL,DE
        ins.add16(CPUState.R.H, CPUState.R.L, CPUState.R.D, CPUState.R.E);
        break;
      case 0x1A:  // LD A,(DE)
        ins.ld8_load(CPUState.R.A, CPUState.R.D, CPUState.R.E);
        break;
      case 0x1B:  // DEC DE
        ins.dec(CPUState.R.D, CPUState.R.E);
        break;
      case 0x1C:  // INC E
        ins.inc(CPUState.R.E);
        break;
      case 0x1D:  // DEC E
        ins.dec(CPUState.R.E);
        break;
      case 0x1E:  // LD E,d8
        ins.ld8(CPUState.R.E);
        break;
      case 0x1F:  // RRA
        ins.rra();
        break;
      case 0x20:  // JR NZ,r8
        ins.jrn(CPUState.Flag.Z);
        break;
      case 0x21:  // LD HL,d16
        ins.ld16(CPUState.R.H, CPUState.R.L);
        break;
      case 0x22:  // LD (HL+),A
        ins.ld8_storei(CPUState.R.H, CPUState.R.L, CPUState.R.A);
        break;
      case 0x23:  // INC HL
        ins.inc(CPUState.R.H, CPUState.R.L);
        break;
      case 0x24:  // INC H
        ins.inc(CPUState.R.H);
        break;
      case 0x25:  // DEC H
        ins.dec(CPUState.R.H);
        break;
      case 0x26:  // LD H,d8
        ins.ld8(CPUState.R.H);
        break;
      case 0x27:  // DAA
        ins.daa();
        break;
      case 0x28:  // JR Z,r8
        ins.jr(CPUState.Flag.Z);
        break;
      case 0x29:  // LD HL,HL
        ins.add16(CPUState.R.H, CPUState.R.L, CPUState.R.H, CPUState.R.L);
        break;
      case 0x2A:  // LD A,[HL+]
        ins.ld8_loadi(CPUState.R.A, CPUState.R.H, CPUState.R.L);
        break;
      case 0x2B:  // DEC HL
        ins.dec(CPUState.R.H, CPUState.R.L);
        break;
      case 0x2C:  // INC L
        ins.inc(CPUState.R.L);
        break;
      case 0x2D:  // DEC L
        ins.dec(CPUState.R.L);
        break;
      case 0x2E:  // LD L,d8
        ins.ld8(CPUState.R.L);
        break;
      case 0x2F:  // CPL
        ins.cpl();
        break;
      case 0x30:  // JR NC,r8
        ins.jrn(CPUState.Flag.C);
        break;
      case 0x31:  // LD SP,d16
        ins.ld16(CPUState.R.SP_0, CPUState.R.SP_1);
        break;
      case 0x32:  // LD (HL-),A
        ins.ld8_stored(CPUState.R.H, CPUState.R.L, CPUState.R.A);
        break;
      case 0x33:  // INC SP
        ins.inc(CPUState.R.SP_0, CPUState.R.SP_1);
        break;
      case 0x34:  // INC (HL)
        ins.inc_mem(CPUState.R.H, CPUState.R.L);
        break;
      case 0x35:  // DEC [HL]
        ins.dec_mem(CPUState.R.H, CPUState.R.L);
        break;
      case 0x36:  // LD [HL],d8
        ins.ldhl_store(CPUState.R.H, CPUState.R.L);
        break;
      case 0x37:  // SCF
        ins.scf();
        break;
      case 0x38:  // JR C,r8
        ins.jr(CPUState.Flag.C);
        break;
      case 0x39:  // LD HL,SP
        ins.add16(CPUState.R.H, CPUState.R.L, CPUState.R.SP_0, CPUState.R.SP_1);
        break;
      case 0x3A:  // LD A,[HL-]
        ins.ld8_loadd(CPUState.R.A, CPUState.R.H, CPUState.R.L);
        break;
      case 0x3B:  // DEC SP
        ins.dec(CPUState.R.SP_0, CPUState.R.SP_1);
        break;
      case 0x3C:  // INC A
        ins.inc(CPUState.R.A);
        break;
      case 0x3D:  // DEC A
        ins.dec(CPUState.R.A);
        break;
      case 0x3E:  // LD A,d8
        ins.ld8(CPUState.R.A);
        break;
      case 0x3F:  // CCF
        ins.ccf();
        break;
      case 0x40:  // LD B,B
        ins.ld8(CPUState.R.B, CPUState.R.B);
        break;
      case 0x41:  // LD B,C
        ins.ld8(CPUState.R.B, CPUState.R.C);
        break;
      case 0x42:  // LD B,D
        ins.ld8(CPUState.R.B, CPUState.R.D);
        break;
      case 0x43:  // LD B,E
        ins.ld8(CPUState.R.B, CPUState.R.E);
        break;
      case 0x44:  // LD B,H
        ins.ld8(CPUState.R.B, CPUState.R.H);
        break;
      case 0x45:  // LD B,L
        ins.ld8(CPUState.R.B, CPUState.R.L);
        break;
      case 0x46:  // LD B,[HL]
        ins.ld8_load(CPUState.R.B, CPUState.R.H, CPUState.R.L);
        break;
      case 0x47:  // LD B,A
        ins.ld8(CPUState.R.B, CPUState.R.A);
        break;
      case 0x48:  // LD C,B
        ins.ld8(CPUState.R.C, CPUState.R.B);
        break;
      case 0x49:  // LD C,C
        ins.ld8(CPUState.R.C, CPUState.R.C);
        break;
      case 0x4A:  // LD C,D
        ins.ld8(CPUState.R.C, CPUState.R.D);
        break;
      case 0x4B:  // LD C,E
        ins.ld8(CPUState.R.C, CPUState.R.E);
        break;
      case 0x4C:  // LD C,H
        ins.ld8(CPUState.R.C, CPUState.R.H);
        break;
      case 0x4D:  // LD C,L
        ins.ld8(CPUState.R.C, CPUState.R.L);
        break;
      case 0x4E:  // LD C,[HL]
        ins.ld8_load(CPUState.R.C, CPUState.R.H, CPUState.R.L);
        break;
      case 0x4F:  // LD C,A
        ins.ld8(CPUState.R.C, CPUState.R.A);
        break;
      case 0x50:  // LD D,B
        ins.ld8(CPUState.R.D, CPUState.R.B);
        break;
      case 0x51:  // LD D,C
        ins.ld8(CPUState.R.D, CPUState.R.C);
        break;
      case 0x52:  // LD D,D
        ins.ld8(CPUState.R.D, CPUState.R.D);
        break;
      case 0x53:  // LD D,E
        ins.ld8(CPUState.R.D, CPUState.R.E);
        break;
      case 0x54:  // LD D,H
        ins.ld8(CPUState.R.D, CPUState.R.H);
        break;
      case 0x55:  // LD D,L
        ins.ld8(CPUState.R.D, CPUState.R.L);
        break;
      case 0x56:  // LD D,[HL]
        ins.ld8_load(CPUState.R.D, CPUState.R.H, CPUState.R.L);
        break;
      case 0x57:  // LD D,A
        ins.ld8(CPUState.R.D, CPUState.R.A);
        break;
      case 0x58:  // LD E,B
        ins.ld8(CPUState.R.E, CPUState.R.B);
        break;
      case 0x59:  // LD E,C
        ins.ld8(CPUState.R.E, CPUState.R.C);
        break;
      case 0x5A:  // LD E,D
        ins.ld8(CPUState.R.E, CPUState.R.D);
        break;
      case 0x5B:  // LD E,E
        ins.ld8(CPUState.R.E, CPUState.R.E);
        break;
      case 0x5C:  // LD E,H
        ins.ld8(CPUState.R.E, CPUState.R.H);
        break;
      case 0x5D:  // LD E,L
        ins.ld8(CPUState.R.E, CPUState.R.L);
        break;
      case 0x5E:  // LD E,[HL]
        ins.ld8_load(CPUState.R.E, CPUState.R.H, CPUState.R.L);
        break;
      case 0x5F:  // LD E,A
        ins.ld8(CPUState.R.E, CPUState.R.A);
        break;
      case 0x60:  // LD H,B
        ins.ld8(CPUState.R.H, CPUState.R.B);
        break;
      case 0x61:  // LD H,C
        ins.ld8(CPUState.R.H, CPUState.R.C);
        break;
      case 0x62:  // LD H,D
        ins.ld8(CPUState.R.H, CPUState.R.D);
        break;
      case 0x63:  // LD H,E
        ins.ld8(CPUState.R.H, CPUState.R.E);
        break;
      case 0x64:  // LD H,H
        ins.ld8(CPUState.R.H, CPUState.R.H);
        break;
      case 0x65:  // LD H,L
        ins.ld8(CPUState.R.H, CPUState.R.L);
        break;
      case 0x66:  // LD H,[HL]
        ins.ld8_load(CPUState.R.H, CPUState.R.H, CPUState.R.L);
        break;
      case 0x67:  // LD H,A
        ins.ld8(CPUState.R.H, CPUState.R.A);
        break;
      case 0x68:  // LD L,B
        ins.ld8(CPUState.R.L, CPUState.R.B);
        break;
      case 0x69:  // LD L,C
        ins.ld8(CPUState.R.L, CPUState.R.C);
        break;
      case 0x6A:  // LD L,D
        ins.ld8(CPUState.R.L, CPUState.R.D);
        break;
      case 0x6B:  // LD L,E
        ins.ld8(CPUState.R.L, CPUState.R.E);
        break;
      case 0x6C:  // LD L,H
        ins.ld8(CPUState.R.L, CPUState.R.H);
        break;
      case 0x6D:  // LD L,L
        ins.ld8(CPUState.R.L, CPUState.R.L);
        break;
      case 0x6E:  // LD L,[HL]
        ins.ld8_load(CPUState.R.L, CPUState.R.H, CPUState.R.L);
        break;
      case 0x6F:  // LD L,A
        ins.ld8(CPUState.R.L, CPUState.R.A);
        break;
      case 0x70:  // LD [HL],B
        ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.B);
        break;
      case 0x71:  // LD [HL],C
        ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.C);
        break;
      case 0x72:  // LD [HL],D
        ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.D);
        break;
      case 0x73:  // LD [HL],E
        ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.E);
        break;
      case 0x74:  // LD [HL],H
        ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.H);
        break;
      case 0x75:  // LD [HL],L
        ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.L);
        break;
      case 0x76:  // HALT
        ins.halt();
        break;
      case 0x77:  // LD [HL],A
        ins.ld8_store(CPUState.R.H, CPUState.R.L, CPUState.R.A);
        break;
      case 0x78:  // LD A,B
        ins.ld8(CPUState.R.A, CPUState.R.B);
        break;
      case 0x79:  // LD A,C
        ins.ld8(CPUState.R.A, CPUState.R.C);
        break;
      case 0x7A:  // LD A,D
        ins.ld8(CPUState.R.A, CPUState.R.D);
        break;
      case 0x7B:  // LD A,E
        ins.ld8(CPUState.R.A, CPUState.R.E);
        break;
      case 0x7C:  // LD A,H
        ins.ld8(CPUState.R.A, CPUState.R.H);
        break;
      case 0x7D:  // LD A,L
        ins.ld8(CPUState.R.A, CPUState.R.L);
        break;
      case 0x7E:  // LD A,[HL]
        ins.ld8_load(CPUState.R.A, CPUState.R.H, CPUState.R.L);
        break;
      case 0x7F:  // LD A,A
        ins.ld8(CPUState.R.A, CPUState.R.A);
        break;
      case 0x80:  // ADD A,B
        ins.add(CPUState.R.A, CPUState.R.B);
        break;
      case 0x81:  // ADD A,C
        ins.add(CPUState.R.A, CPUState.R.C);
        break;
      case 0x82:  // ADD A,D
        ins.add(CPUState.R.A, CPUState.R.D);
        break;
      case 0x83:  // ADD A,E
        ins.add(CPUState.R.A, CPUState.R.E);
        break;
      case 0x84:  // ADD A,H
        ins.add(CPUState.R.A, CPUState.R.H);
        break;
      case 0x85:  // ADD A,L
        ins.add(CPUState.R.A, CPUState.R.L);
        break;
      case 0x86:  // ADD A,[HL]
        ins.add(CPUState.R.A, CPUState.R.H, CPUState.R.L);
        break;
      case 0x87:  // ADD A,A
        ins.add(CPUState.R.A, CPUState.R.A);
        break;
      case 0x88:  // ADC A,B
        ins.adc(CPUState.R.A, CPUState.R.B);
        break;
      case 0x89:  // ADC A,C
        ins.adc(CPUState.R.A, CPUState.R.C);
        break;
      case 0x8A:  // ADC A,D
        ins.adc(CPUState.R.A, CPUState.R.D);
        break;
      case 0x8B:  // ADC A,E
        ins.adc(CPUState.R.A, CPUState.R.E);
        break;
      case 0x8C:  // ADC A,H
        ins.adc(CPUState.R.A, CPUState.R.H);
        break;
      case 0x8D:  // ADC A,L
        ins.adc(CPUState.R.A, CPUState.R.L);
        break;
      case 0x8E:  // ADC A,[HL]
        ins.adc(CPUState.R.A, CPUState.R.H, CPUState.R.L);
        break;
      case 0x8F:  // ADC A,A
        ins.adc(CPUState.R.A, CPUState.R.A);
        break;
      case 0x90:  // SUB B
        ins.sub(CPUState.R.B);
        break;
      case 0x91:  // SUB C
        ins.sub(CPUState.R.C);
        break;
      case 0x92:  // SUB D
        ins.sub(CPUState.R.D);
        break;
      case 0x93:  // SUB E
        ins.sub(CPUState.R.E);
        break;
      case 0x94:  // SUB H
        ins.sub(CPUState.R.H);
        break;
      case 0x95:  // SUB L
        ins.sub(CPUState.R.L);
        break;
      case 0x96:  // SUB [HL]
        ins.sub(CPUState.R.H, CPUState.R.L);
        break;
      case 0x97:  // SUB A
        ins.sub(CPUState.R.A);
        break;
      case 0x98:  // SBC A,B
        ins.sbc(CPUState.R.B);
        break;
      case 0x99:  // SBC A,C
        ins.sbc(CPUState.R.C);
        break;
      case 0x9A:  // SBC A,D
        ins.sbc(CPUState.R.D);
        break;
      case 0x9B:  // SBC A,E
        ins.sbc(CPUState.R.E);
        break;
      case 0x9C:  // SBC A,H
        ins.sbc(CPUState.R.H);
        break;
      case 0x9D:  // SBC A,L
        ins.sbc(CPUState.R.L);
        break;
      case 0x9E:  // SBC A,[HL]
        ins.sbc(CPUState.R.H, CPUState.R.L);
        break;
      case 0x9F:  // SBC A,A
        ins.and(CPUState.R.A);
        break;
      case 0xA0:  // AND B
        ins.and(CPUState.R.B);
        break;
      case 0xA1:  // AND C
        ins.and(CPUState.R.C);
        break;
      case 0xA2:  // AND D
        ins.and(CPUState.R.D);
        break;
      case 0xA3:  // AND E
        ins.and(CPUState.R.E);
        break;
      case 0xA4:  // AND H
        ins.and(CPUState.R.H);
        break;
      case 0xA5:  // AND L
        ins.and(CPUState.R.L);
        break;
      case 0xA6:  // AND [HL]
        ins.and(CPUState.R.H, CPUState.R.L);
        break;
      case 0xA7:  // AND A
        ins.and(CPUState.R.A);
        break;
      case 0xA8:  // XOR B
        ins.xor(CPUState.R.B);
        break;
      case 0xA9:  // XOR C
        ins.xor(CPUState.R.C);
        break;
      case 0xAA:  // XOR D
        ins.xor(CPUState.R.D);
        break;
      case 0xAB:  // XOR E
        ins.xor(CPUState.R.E);
        break;
      case 0xAC:  // XOR H
        ins.xor(CPUState.R.H);
        break;
      case 0xAD:  // XOR L
        ins.xor(CPUState.R.L);
        break;
      case 0xAE:  // XOR [HL]
        ins.xor(CPUState.R.H, CPUState.R.L);
        break;
      case 0xAF:  // XOR A
        ins.xor(CPUState.R.A);
        break;
      case 0xB0:  // OR B
        ins.or(CPUState.R.B);
        break;
      case 0xB1:  // OR C
        ins.or(CPUState.R.C);
        break;
      case 0xB2:  // OR D
        ins.or(CPUState.R.D);
        break;
      case 0xB3:  // OR E
        ins.or(CPUState.R.E);
        break;
      case 0xB4:  // OR H
        ins.or(CPUState.R.H);
        break;
      case 0xB5:  // OR L
        ins.or(CPUState.R.L);
        break;
      case 0xB6:  // OR [HL]
        ins.or(CPUState.R.H, CPUState.R.L);
        break;
      case 0xB7:  // OR A
        ins.or(CPUState.R.A);
        break;
      case 0xB8:  // CP B
        ins.cp(CPUState.R.B);
        break;
      case 0xB9:  // CP C
        ins.cp(CPUState.R.C);
        break;
      case 0xBA:  // CP D
        ins.cp(CPUState.R.D);
        break;
      case 0xBB:  // CP E
        ins.cp(CPUState.R.E);
        break;
      case 0xBC:  // CP H
        ins.cp(CPUState.R.H);
        break;
      case 0xBD:  // CP L
        ins.cp(CPUState.R.L);
        break;
      case 0xBE:  // CP [HL]
        ins.cp(CPUState.R.H, CPUState.R.L);
        break;
      case 0xBF:  // CP A
        ins.cp(CPUState.R.A);
        break;
      case 0xC0:  // RET NZ
        ins.retn(CPUState.Flag.Z);
        break;
      case 0xC1:  // POP BC
        ins.pop(CPUState.R.B, CPUState.R.C);
        break;
      case 0xC2:  // JP NZ,a16
        ins.jpn(CPUState.Flag.Z);
        break;
      case 0xC3:  // JP a16
        ins.jp();
        break;
      case 0xC4:  // CALL NZ,a16
        ins.calln(CPUState.Flag.Z);
        break;
      case 0xC5:  // PUSH BC
        ins.push(CPUState.R.B, CPUState.R.C);
        break;
      case 0xC6:  // ADD A,d8
        ins.add(CPUState.R.A);
        break;
      case 0xC7:  // RST 0x00
        ins.rst(0x00);
        break;
      case 0xC8:  // RET Z
        ins.ret(CPUState.Flag.Z);
        break;
      case 0xC9:  // RET
        ins.ret();
        break;
      case 0xCA:  // JP Z,a16
        ins.jp(CPUState.Flag.Z);
        break;
      case 0xCB:  // PREFIX CB
        ins.CB();
        break;
      case 0xCC:  // CALL Z,a16
        ins.call(CPUState.Flag.Z);
        break;
      case 0xCD:  // CALL a16
        ins.call();
        break;
      case 0xCE:  // ADC A,d8
        ins.adc(CPUState.R.A);
        break;
      case 0xCF:  // RST 0x08
        ins.rst(0x08);
        break;
      case 0xD0:  // RET NC
        ins.retn(CPUState.Flag.C);
        break;
      case 0xD1:  // POP DE
        ins.pop(CPUState.R.D, CPUState.R.E);
        break;
      case 0xD2:  // JP NC,a16
        ins.jpn(CPUState.Flag.C);
        break;
      case 0xD3:  // N/A
        missing(instruction);
        break;
      case 0xD4:  // CALL NC,a16
        ins.calln(CPUState.Flag.C);
        break;
      case 0xD5:  // PUSH DE
        ins.push(CPUState.R.D, CPUState.R.E);
        break;
      case 0xD6:  // SUB d8
        ins.sub();
        break;
      case 0xD7:  // RST 0x10
        ins.rst(0x10);
        break;
      case 0xD8:  // RET C
        ins.ret(CPUState.Flag.C);
        break;
      case 0xD9:  // RETI
        ins.reti();
        break;
      case 0xDA:  // JP C,a16
        ins.jp(CPUState.Flag.C);
        break;
      case 0xDB:  // N/A
        missing(instruction);
        break;
      case 0xDC:  // CALL C,a16
        ins.call(CPUState.Flag.C);
        break;
      case 0xDD:  // N/A
        dump(); // TODO change back to missing(instruction);
        break;
      case 0xDE:  // SBC A,d8
        ins.sbc();
        break;
      case 0xDF:  // RST 0x18
        ins.rst(0x18);
        break;
      case 0xE0:  // LDH (a8),A
        ins.ldh_store(CPUState.R.A);
        break;
      case 0xE1:  // POP HL
        ins.pop(CPUState.R.H, CPUState.R.L);
        break;
      case 0xE2:  // LD (C),A
        ins.ld8_store(CPUState.R.C, CPUState.R.A);
        break;
      case 0xE3:  // N/A
        missing(instruction);
        break;
      case 0xE4:  // N/A
        missing(instruction);
        break;
      case 0xE5:  // PUSH HL
        ins.push(CPUState.R.H, CPUState.R.L);
        break;
      case 0xE6:  // AND d8
        ins.and();
        break;
      case 0xE7:  // RST 0x20
        ins.rst(0x20);
        break;
      case 0xE8:  // ADD SP,r8
        ins.add16(CPUState.R.SP_0, CPUState.R.SP_1);
        break;
      case 0xE9:  // JP HL
        ins.jp(CPUState.R.H, CPUState.R.L);
        break;
      case 0xEA:  // LD [a16],A
        ins.ld8_store(CPUState.R.A);
        break;
      case 0xEB:  // N/A
        missing(instruction);
        break;
      case 0xEC:  // N/A
        missing(instruction);
        break;
      case 0xED:  // N/A
        missing(instruction);
        break;
      case 0xEE:  // XOR d8
        ins.xor();
        break;
      case 0xEF:  // RST 0x28
        ins.rst(0x28);
        break;
      case 0xF0:  // LDH A,(a8)
        ins.ldh_load(CPUState.R.A);
        break;
      case 0xF1:  // POP AF
        ins.pop(CPUState.R.A, CPUState.R.F);
        break;
      case 0xF2:  // LD A,(C)
        ins.ld8_load(CPUState.R.A, CPUState.R.C);
        break;
      case 0xF3:  // DI
        ins.DI();
        break;
      case 0xF4:  // N/A
        missing(instruction);
        break;
      case 0xF5:  // PUSH AF
        ins.push(CPUState.R.A, CPUState.R.F);
        break;
      case 0xF6:  // OR d8
        ins.or();
        break;
      case 0xF7:  // RST 0x30
        ins.rst(0x30);
        break;
      case 0xF8:  // LD HL,SP+r8
        ins.ld_pop();
        break;
      case 0xF9:  // LD SP,HL
        ins.ld16(CPUState.R.SP_0, CPUState.R.SP_1, CPUState.R.H, CPUState.R.L);
        break;
      case 0xFA:  // LD A,[a16]
        ins.ld8_load(CPUState.R.A);
        break;
      case 0xFB:  // EI
        ins.EI();
        break;
      case 0xFC:  // N/A
        missing(instruction);
        break;
      case 0xFD:  // N/A
        missing(instruction);
        break;
      case 0xFE:  // CP d8
        ins.cp();
        break;
      case 0xFF:  // RST 0x38
        ins.rst(0x38);
        break;
      default:
        Util.errn("CPU.decode - defaulted on instruction: " + Util.hex(instruction));
        break;
    }
  }

  private void missing(short instruction) {
    Util.errn("Attempted to run missing instruction 0x" + Util.hex(instruction));
  }


  public static void main(String args[]) {
    CPUState state = new CPUState();
    Cart cart = new Cart("roms/bios.gb");
    ClockCounter cc = new ClockCounter();
    PPU ppu = new PPU(cc);
    MMU mmu = new MMU(ppu);
    ppu.setMMU(mmu);

    CPU cpu = new CPU(state, mmu, cart, cc);
  }
}
