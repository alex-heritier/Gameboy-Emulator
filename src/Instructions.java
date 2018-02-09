
import java.util.HashMap;

class Instructions {
  private CPUState state;
  private MMU mmu;
  private ClockCounter clockCounter;
  private CBInstructions cb;

  public Instructions(CPUState state, MMU mmu, ClockCounter clockCounter) {
    this.state = state;
    this.mmu = mmu;
    this.clockCounter = clockCounter;
    this.cb = new CBInstructions(state, mmu, clockCounter);
  }
  private Instructions() {
  }

  /*
   * Top level CPU instructions
   */

 /* - Misc/control instructions */
 //     - nop
 public void nop() {
   clockCounter.add(1);
 }

 //     - stop
 public void stop() {
   Util.debug("STOP");
   state.incPC(); // STOP skips the next instruction
   clockCounter.add(1);
 }

 //     - halt
 public void halt() {
   Util.debug("HALT");
   clockCounter.add(1);
 }

 //     - DI
 public void DI() {
   state.IME(false);
   clockCounter.add(1);
 }

 //     - EI
 public void EI() {
   state.IME(true);
   clockCounter.add(1);
 }

 //     - prefix CB
 public void CB() {
   short instruction = readMem8(state.PC());
   state.incPC();  // skip CB instruction
   clockCounter.add(1);
   cb.handle(instruction);
 }
 /* - END Misc/control instructions */


 /* - Jumps/calls */
 //     - jp a16
 public void jp() {
   int address = 0;
   address = readMem16(state.PC());
   state.incPC();
   state.incPC();

   clockCounter.add(4);

   jump(address);
 }

 //     - jp FLAG a16
 public void jp(CPUState.Flag flag) {
   if (state.getFlag(flag)) {
      jp();
   } else {
     state.incPC();  // skip immediate address
     state.incPC();
     clockCounter.add(3);
   }
 }

 //     - jp HL
 public void jp(CPUState.R reg1, CPUState.R reg2) {
   int address = word(reg1, reg2);
   clockCounter.add(1);
   jump(address);
 }

 //     - jpn FLAG a16
 public void jpn(CPUState.Flag flag) {
    if (!state.getFlag(flag)) {
      jp();
    } else {
      state.incPC();  // skip immediate address
      state.incPC();
      clockCounter.add(3);
    }
 }

 //     - jr a8
 // unconditional relative jump
 public void jr() {
   clockCounter.add(3);

   byte offset = (byte)readMem8(state.PC());  // cast to byte so that sign matters
   state.incPC();
   int address = add16(CPUState.R.PC_0, CPUState.R.PC_1, offset);


   Util.debug("jr 0x" + Util.hex(offset));
   jump(address);
 }

 //     - jr FLAG a8
 // relative jump if flag IS SET
 public void jr(CPUState.Flag flag) {
   if (state.getFlag(flag))
     jr();
   else {
     state.incPC();  // skip address
     clockCounter.add(2);
   }
 }

 //     - jrn FLAG a8
 // relative jump if flag is NOT SET
 public void jrn(CPUState.Flag flag) {
   if (!state.getFlag(flag))
     jr();
   else {
     state.incPC();  // skip address
     clockCounter.add(2);
   }
 }

 //     - ret
 public void ret() {
   int address = pop();
   // Util.debug("RET - address: " + Util.hex(address));

   clockCounter.add(4);

   jump(address);
 }

 //     - ret FLAG
 public void ret(CPUState.Flag flag) {
   if (state.getFlag(flag)) {
     clockCounter.add(1); // more time gets added in ret()
     ret();
   } else
     clockCounter.add(2);
 }

 //     - retn FLAG
 public void retn(CPUState.Flag flag) {
   if (!state.getFlag(flag)) {
     clockCounter.add(1);
     ret();
   } else {
     clockCounter.add(2);
   }
 }

 //     - reti
 public void reti() {
   ret();
   EI();  // enable interrupts
 }

 //     - rst a8
 public void rst(int address) {
   clockCounter.add(4);

   push(state.PC());
   jump(address);
 }

 //     - call a16
 public void call() {
   int address = 0;

   address = readMem16(state.PC());
   state.incPC();
   state.incPC();
   push(state.PC());  // push address of next instruction on stack

   clockCounter.add(6);

   jump(address);
 }

 //     - call FLAG a16
 public void call(CPUState.Flag flag) {
   if (state.getFlag(flag)) {
     call();
   } else {
     state.incPC(); // skip immediate address
     state.incPC();
     clockCounter.add(3);
   }
 }

 //     - calln FLAG a16
 public void calln(CPUState.Flag flag) {
   if (!state.getFlag(flag)) {
     call();
   } else {
     state.incPC(); // skip immediate address
     state.incPC();
     clockCounter.add(3);
   }
 }
 /* - END Jumps/calls */


 /* - 8bit load/store/move instructions */
 //     - ld A, B
 public void ld8(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(1);

   ld8(reg1, state.getReg(reg2));
 }

 //     - ld A, d8
 public void ld8(CPUState.R reg) {
   clockCounter.add(2);

   short value = 0;
   value = readMem8(state.PC());
   state.incPC();
   ld8(reg, value);
 }

 //     - ld A, [BC]
 public void ld8_load(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
   clockCounter.add(2);

   int address = word(reg2, reg3);
   short value = readMem8(address);
   ld8(reg1, value);
 }

 //     - ld A, [HL-]
 public void ld8_loadd(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
   clockCounter.add(2);

   ld8_load(reg1, reg2, reg3);  // ld A, [HL]
   dec(reg2, reg3);             // HL--
 }

 //     - ld A, [HL+]
 public void ld8_loadi(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
   clockCounter.add(2);

   ld8_load(reg1, reg2, reg3);  // ld A, [HL]
   inc(reg2, reg3);             // HL++
 }

 //     - ld [BC], A
 public void ld8_store(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
   clockCounter.add(2);

   short value = state.getReg(reg3);
   int address = word(reg1, reg2);
   writeMem8(address, value);
 }

 //     - ld [HL-], A
 public void ld8_stored(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
   clockCounter.add(2);

   ld8_store(reg1, reg2, reg3);  // ld [HL], A
   dec(reg1, reg2);              // HL--
 }

 //     - ld [HL+], A
 public void ld8_storei(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
   clockCounter.add(2);

   ld8_store(reg1, reg2, reg3);  // ld [HL], A
   inc(reg1, reg2);              // HL++
 }

 //     - ld [HL], d8
 public void ldhl_store(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(3);

   int address = word(reg1, reg2);
   short value = 0;

   value = readMem8(state.PC());
   state.incPC();
   writeMem8(address, value);
 }

 //     - ldh [a8], A
 public void ldh_store(CPUState.R reg) {
   clockCounter.add(3);

   short registerValue = state.getReg(reg);
   short offset = 0;
   int base = 0xFF00;

   offset = readMem8(state.PC());
   state.incPC();
   writeMem8(CPUMath.add16(base, offset), registerValue);
 }

 //     - ldh A, [a8]
 public void ldh_load(CPUState.R reg) {
   clockCounter.add(3);

   short offset = 0;
   int base = 0xFF00;

   offset = readMem8(state.PC());
   state.incPC();
   int address = CPUMath.add16(base, offset);
   short value = readMem8(address);
   ld8(reg, value);
 }

 //     - ld [C], A
 public void ld8_store(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(2);

   int base = 0xFF40;
   short offset = state.getReg(reg1);
   short value = state.getReg(reg2);
   writeMem8(CPUMath.add16(base, offset), value);
 }

 //     - ld A, [C]
 public void ld8_load(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(2);

   int base = 0xFF40;
   short offset = state.getReg(reg2);
   short value = readMem8(CPUMath.add16(base, offset));
   ld8(CPUState.R.A, value);
 }

 //     - ld [a16], A
 public void ld8_store(CPUState.R reg) {
   clockCounter.add(4);

   short value = state.getReg(reg);

   int address = readMem16(state.PC());
   state.incPC();
   state.incPC();

   writeMem8(address, value);
 }

 //     - ld A, [a16]
 public void ld8_load(CPUState.R reg) {
   clockCounter.add(4);

   int address = readMem16(state.PC());
   state.incPC();
   state.incPC();

   short value = readMem8(address);
   ld8(reg, value);
 }
 /* - END 8bit load/store/move instructions */


 /* - 16bit load/store/move instructions */
 //     - ld SP, HL
 public void ld16(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3, CPUState.R reg4) {
   clockCounter.add(2);

   ld16(reg1, reg2, state.getReg(reg3), state.getReg(reg4));
 }

 //     - ld BC, d16
 public void ld16(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(3);

   int word = 0;

   word = readMem16(state.PC());
   state.incPC();
   state.incPC();

   short[] bytes = CPUMath.toBytes(word);
   ld16(reg1, reg2, bytes[0], bytes[1]);
 }

 //     - ld [d16], SP
 public void ld16_store(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(5);

   int address = 0;

   address = readMem16(state.PC());
   state.incPC();
   state.incPC();

   writeMem16(address, word(reg1, reg2));
 }

 //     - LD HL,SP+r8 (SIGNED)
 public void ld_pop() {
   clockCounter.add(3);

   // instruction SHOULD set carry and half carry flags as appropriate
   // https://github.com/Drenn1/GameYob/issues/15
   // http://forums.nesdev.com/viewtopic.php?p=42138
   // Both of these set carry and half-carry based on the low byte of SP added
   // to the UNSIGNED immediate byte. The Negative and Zero flags are always
   // cleared. They also calculate SP + SIGNED immediate byte and put the result
   // into SP or HL, respectively.

   short offset = 0;
   offset = readMem8(state.PC());
   state.incPC();

   int address = (word(CPUState.R.SP_0, CPUState.R.SP_1) + (byte)offset) & 0xFFFF;

   short lowerByte = state.getReg(CPUState.R.SP_1);
   CPUMath.Result flagTest = CPUMath.add8(lowerByte, offset);

   // FLAGS
   state.setFlag(CPUState.Flag.Z, false);
   state.setFlag(CPUState.Flag.N, false);
   state.setFlag(CPUState.Flag.H, flagTest.getFlag(CPUState.Flag.H));
   state.setFlag(CPUState.Flag.C, flagTest.getFlag(CPUState.Flag.C));

   ld16(CPUState.R.H, CPUState.R.L, address);
 }

 //     - pop BC
 public void pop(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(3);

   int word = pop();
   ld16(reg1, reg2, word);
 }

 //     - push BC
 public void push(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(4);

   int value = word(reg1, reg2);
   push(value);
 }
 /* - END 16bit load/store/move instructions */


 /* - 8bit arithmetic */
 //     - inc B
 public void inc(CPUState.R reg) {
   clockCounter.add(1);

   short originalValue = state.getReg(reg);
   state.setReg(reg, inc8(reg));

   state.setFlag(CPUState.Flag.Z, state.getReg(reg) == 0);
   state.setFlag(CPUState.Flag.N, false);
   state.setFlag(CPUState.Flag.H, CPUMath.addWillHalfCarry(originalValue, (short)1));
 }

 //     - inc [HL]
 public void inc_mem(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(3);

   int address = word(reg1, reg2);
   short originalValue = readMem8(address);
   CPUMath.Result r = CPUMath.inc8(originalValue);

   writeMem8(address, r.get8());
 }

 //     - dec B
 public void dec(CPUState.R reg) {
   clockCounter.add(1);

   short originalValue = state.getReg(reg);
   CPUMath.Result result = dec8(reg);

   state.setReg(reg, result.get8());

   state.setFlag(CPUState.Flag.Z, result.get8() == 0);
   state.setFlag(CPUState.Flag.N, true);
   state.setFlag(CPUState.Flag.H, result.getFlag(CPUState.Flag.H));
 }

 //     - dec [BC]
 public void dec_mem(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(3);

   int address = word(reg1, reg2);
   short originalValue = readMem8(address);
   CPUMath.Result r = CPUMath.dec8(originalValue);

   writeMem8(address, r.get8());
 }

 //     - daa
 public void daa() {
   // Adapted from http://forums.nesdev.com/viewtopic.php?t=9088
   clockCounter.add(1);

   short regA = state.getReg(CPUState.R.A);

   if (!state.getFlag(CPUState.Flag.N)) {
       if (state.getFlag(CPUState.Flag.H) || (regA & 0xF) > 0x09)
           regA = CPUMath.add8(regA, (short)0x06).get8();

       if (state.getFlag(CPUState.Flag.C) || regA > 0x9F)
           regA = CPUMath.add8(regA, (short)0x60).get8();
   }
   else {
       if (state.getFlag(CPUState.Flag.H))
           regA = CPUMath.sub8(regA, (short)0x06).get8();

       if (state.getFlag(CPUState.Flag.C))
           regA = CPUMath.sub8(regA, (short)0x60).get8();
   }

   final short halfCarryFlag = 0x20;
   final short zeroFlag = 0x80;
   short tmp = (short)(~(CPUMath.or8(halfCarryFlag, zeroFlag).get8()));
   state.setReg(CPUState.R.F, and8(CPUState.R.F, tmp).get8());

   if ((regA & 0x100) == 0x100)
       state.setFlag(CPUState.Flag.C, true);

   regA = CPUMath.and8(regA, (short)0xFF).get8();

   if (regA == 0)
       state.setFlag(CPUState.Flag.Z, true);

   state.setReg(CPUState.R.A, regA);
 }

 //     - scf
 public void scf() {
   state.setFlag(CPUState.Flag.C, true);
 }

 //     - cpl
 public void cpl() {
   clockCounter.add(1);

   short registerValue = state.getReg(CPUState.R.A);
   registerValue = (short)((~registerValue) & 0xFF);

   state.setReg(CPUState.R.A, registerValue);
   state.setFlag(CPUState.Flag.N, true);
   state.setFlag(CPUState.Flag.H, true);
 }

 //     - ccf
 public void ccf() {
   clockCounter.add(1);

   state.setFlag(CPUState.Flag.N, true);
   state.setFlag(CPUState.Flag.H, true);
   state.setFlag(CPUState.Flag.C, !state.getFlag(CPUState.Flag.C));
 }

 //     - add A, B
 public void add(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(1);

   add(reg1, state.getReg(reg2));
 }

 //     - add A, [HL]
 public void add(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
   clockCounter.add(2);

   int address = word(reg2, reg3);
   short value = readMem8(address);

   add(reg1, value);
 }

 //     - add A, d8
 public void add(CPUState.R reg) {
   clockCounter.add(2);

   short value = 0;
   value = readMem8(state.PC());
   state.incPC();

   add(reg, value);
 }

 //     - adc A, B
 public void adc(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(1);

   short value = state.getReg(reg2);
   short carry = (short)(state.getFlag(CPUState.Flag.C) ? 1 : 0);
   short sum = CPUMath.add8(value, carry).get8();

   add(reg1, sum);
 }

 //     - adc A, [HL]
 public void adc(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3) {
   clockCounter.add(2);

   int address = word(reg2, reg3);
   short value = readMem8(address);
   short carry = (short)(state.getFlag(CPUState.Flag.C) ? 1 : 0);
   short sum = CPUMath.add8(value, carry).get8();

   add(reg1, sum);
 }

 //     - adc A, d8
 public void adc(CPUState.R reg) {
   clockCounter.add(2);

   short value = 0;
   short carry = (short)(state.getFlag(CPUState.Flag.C) ? 1 : 0);

   value = readMem8(state.PC());
   state.incPC();
   short sum = CPUMath.add8(value, carry).get8();

   add(reg, sum);
 }

 //     - sub B
 public void sub(CPUState.R reg) {
   clockCounter.add(1);

   sub(CPUState.R.A, state.getReg(reg));
 }

 //     - sub [HL]
 public void sub(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(2);

   int address = word(reg1, reg2);
   short value = readMem8(address);

   sub(CPUState.R.A, value);
 }

 //     - sub d8
 public void sub() {
   clockCounter.add(2);

   short value = 0;
   value = readMem8(state.PC());
   state.incPC();

   sub(CPUState.R.A, value);
 }

 //     - sbc A, B
 public void sbc(CPUState.R reg) {
   clockCounter.add(1);

   short subtractor = add8(reg, (short)(state.getFlag(CPUState.Flag.C) ? 1 : 0));
   sub(CPUState.R.A, subtractor);
 }

 //     - sbc A, [HL]
 public void sbc(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(2);

   int address = word(reg1, reg2);
   short value = readMem8(address);
   short subtractor = CPUMath.add8(value, (short)(state.getFlag(CPUState.Flag.C) ? 1 : 0)).get8();
   sub(CPUState.R.A, subtractor);
 }

 //     - sbc A, d8
 public void sbc() {
   clockCounter.add(2);

   short value = 0;
   value = readMem8(state.PC());
   state.incPC();
   short subtractor = CPUMath.add8(value, (short)(state.getFlag(CPUState.Flag.C) ? 1 : 0)).get8();
   sub(CPUState.R.A, subtractor);
 }

 //     - cp B
 public void cp(CPUState.R reg) {
   clockCounter.add(1);

   short value = state.getReg(reg);
   cp(value);
 }

 //     - cp [HL]
 public void cp(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(2);

   int address = word(reg1, reg2);
   short value = readMem8(address);
   cp(value);
 }

 //     - cp d8
 public void cp() {
   clockCounter.add(2);

   short value = 0;
   value = readMem8(state.PC());
   state.incPC();
   cp(value);
 }

 private void cp(short value) {
    CPUMath.Result result = sub8(CPUState.R.A, value);

    state.setFlag(CPUState.Flag.Z, result.get8() == 0);
    state.setFlag(CPUState.Flag.N, true);
    state.setFlag(CPUState.Flag.H, result.getFlag(CPUState.Flag.H));
    state.setFlag(CPUState.Flag.C, result.getFlag(CPUState.Flag.C));
  }
 /* - END 8bit arithmetic */


 /* - logical instructions */
 //     - and B
 public void and(CPUState.R reg) {
   clockCounter.add(1);

   short value = state.getReg(reg);
   and8(value);
 }

 //     - and [HL]
 public void and(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(2);

   int address = word(reg1, reg2);
   short value = readMem8(address);
   and8(value);
 }

 //     - and d8
 public void and() {
   clockCounter.add(2);

   short value = 0;
   value = readMem8(state.PC());
   state.incPC();
   and8(value);
 }

 //     - xor B
 public void xor(CPUState.R reg) {
   clockCounter.add(1);

   short value = state.getReg(reg);
   xor8(value);
 }

 //     - xor [HL]
 public void xor(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(2);

   int address = word(reg1, reg2);
   short value = readMem8(address);
   xor8(value);
 }

 //     - xor d8
 public void xor() {
   clockCounter.add(2);

   short value = 0;
   value = readMem8(state.PC());
   state.incPC();
   xor8(value);
 }

 //     - or B
 public void or(CPUState.R reg) {
   clockCounter.add(1);

   short value = state.getReg(reg);
   or8(value);
 }

 //     - or [HL]
 public void or(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(2);

   int address = word(reg1, reg2);
   short value = readMem8(address);
   or8(value);
 }

 //     - or d8
 public void or() {
   clockCounter.add(2);

   short value = 0;
   value = readMem8(state.PC());
   state.incPC();
   or8(value);
 }
 /* - END logical instructions */


 /* - 8bit rotations/shifts and bit instructions */
 //   - RLCA
 public void rlca() {
   clockCounter.add(1);

   short value = state.getReg(CPUState.R.A);
   boolean msb = (value & 0x80) > 0;

   value <<= 1; // shift left
   value += (short)(msb ? 1 : 0);  // set bit 0 to old bit 7
   value &= (short)0xFF;

   state.setReg(CPUState.R.A, value);
   state.resetFlags();
   state.setFlag(CPUState.Flag.C, msb);
 }

 //   - RLA
 public void rla() {
   clockCounter.add(1);

   short value = state.getReg(CPUState.R.A);
   boolean msb = (value & 0x80) > 0;

   value <<= 1; // shift left
   value += (short)(state.getFlag(CPUState.Flag.C) ? 1 : 0);  // set bit 0 to carry
   value &= (short)0xFF;

   state.setReg(CPUState.R.A, value);
   state.resetFlags();
   state.setFlag(CPUState.Flag.C, msb);
 }

 //   - RRCA
 public void rrca() {
   clockCounter.add(1);

   short value = state.getReg(CPUState.R.A);
   boolean lsb = (value & 0x01) > 0;

   value >>= 1; // shift right
   value += (short)(lsb ? 0x80 : 0);  // set bit 7 to old bit 0

   state.setReg(CPUState.R.A, value);
   state.resetFlags();
   state.setFlag(CPUState.Flag.C, lsb);
 }

 //   - RRA
 public void rra() {
   clockCounter.add(1);

   short value = state.getReg(CPUState.R.A);
   boolean lsb = (value & 0x01) > 0;

   value >>= 1; // shift right
   value += (short)(state.getFlag(CPUState.Flag.C) ? 0x80 : 0);  // set bit 7 to carry

   state.setReg(CPUState.R.A, value);
   state.resetFlags();
   state.setFlag(CPUState.Flag.C, lsb);
 }
 /* - END 8bit rotations/shifts and bit instructions */


 /* - 16bit arithmetic */
 //     - inc BC
 public void inc(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(2);

   int originalValue = word(reg1, reg2);
   state.setReg16(reg1, reg2, inc16(reg1, reg2));
   /* Apparently 16 bit increments don't affect flags */
 }

 //     - dec BC
 public void dec(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(2);

   int originalValue = word(reg1, reg2);
   state.setReg16(reg1, reg2, dec16(reg1, reg2));
 }

 //     - add HL, BC
 public void add16(CPUState.R reg1, CPUState.R reg2, CPUState.R reg3, CPUState.R reg4) {
   clockCounter.add(2);

   int result = add16(reg1, reg2, word(reg3, reg4));
   state.setReg16(reg1, reg2, result);
 }

 //     - add SP, r8 (SIGNED)
 public void add16(CPUState.R reg1, CPUState.R reg2) {
   clockCounter.add(4);

   // instruction SHOULD set carry and half carry flags as appropriate
   // https://github.com/Drenn1/GameYob/issues/15
   // http://forums.nesdev.com/viewtopic.php?p=42138
   // Both of these set carry and half-carry based on the low byte of SP added
   // to the UNSIGNED immediate byte. The Negative and Zero flags are always
   // cleared. They also calculate SP + SIGNED immediate byte and put the result
   // into SP or HL, respectively.

   short value = 0;
   value = readMem8(state.PC());
   state.incPC();

   int sum = (word(reg1, reg2) +  (byte)value) & 0xFFFF;

   short lowerByte = state.getReg(reg2);
   CPUMath.Result flagTest = CPUMath.add8(lowerByte, value);

   // FLAGS
   state.setReg16(reg1, reg2, sum);
   state.setFlag(CPUState.Flag.Z, false);
   state.setFlag(CPUState.Flag.N, false);
   state.setFlag(CPUState.Flag.H, flagTest.getFlag(CPUState.Flag.H));
   state.setFlag(CPUState.Flag.C, flagTest.getFlag(CPUState.Flag.C));
 }
 /* - END 16bit arithmetic */


  // Logic functions
  private CPUMath.Result or8(CPUState.R reg1, CPUState.R reg2) { return or8(reg1, state.getReg(reg2)); }
  private CPUMath.Result or8(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    return CPUMath.or8(registerValue, value);
  }

  private CPUMath.Result xor8(CPUState.R reg1, CPUState.R reg2) { return xor8(reg1, state.getReg(reg2)); }
  private CPUMath.Result xor8(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    return CPUMath.xor8(registerValue, value);
  }

  private CPUMath.Result and8(CPUState.R reg1, CPUState.R reg2) { return or8(reg1, state.getReg(reg2)); }
  private CPUMath.Result and8(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    return CPUMath.and8(registerValue, value);
  }


  private void or8(short value) {
    CPUMath.Result result = or8(CPUState.R.A, value);

    state.setReg(CPUState.R.A, result.get8());
    state.resetFlags();
    state.setFlag(CPUState.Flag.Z, result.getFlag(CPUState.Flag.Z));
  }

  private void xor8(short value) {
    CPUMath.Result result = xor8(CPUState.R.A, value);

    state.setReg(CPUState.R.A, result.get8());
    state.resetFlags();
    state.setFlag(CPUState.Flag.Z, result.getFlag(CPUState.Flag.Z));
  }

  private void and8(short value) {
    CPUMath.Result result = and8(CPUState.R.A, value);

    state.setReg(CPUState.R.A, result.get8());
    state.resetFlags();
    state.setFlag(CPUState.Flag.Z, result.getFlag(CPUState.Flag.Z));
    state.setFlag(CPUState.Flag.H, true);
  }


  // Arithmetic functions
  private short inc8(CPUState.R reg) { return add8(reg, (short)1); }

  private int inc16(CPUState.R reg1, CPUState.R reg2) {
    return add16(reg1, reg2, 1);
  }

  private CPUMath.Result dec8(CPUState.R reg1) { return sub8(reg1, (short)1); }
  private int dec16(CPUState.R reg1, CPUState.R reg2) {
    return sub16(reg1, reg2, 1);
  }

  private short add8(CPUState.R reg1, CPUState.R reg2) { return add8(reg1, state.getReg(reg2)); }
  private short add8(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    CPUMath.Result r = CPUMath.add8(registerValue, value);

    return r.get8();
  }

  private int add16(CPUState.R reg1, CPUState.R reg2, int word) {
    return CPUMath.add16(word(reg1, reg2), word);
  }

  private CPUMath.Result sub8(CPUState.R reg1, CPUState.R reg2) { return sub8(reg1, state.getReg(reg2)); }
  private CPUMath.Result sub8(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    return CPUMath.sub8(registerValue, value);
  }

  private int sub16(CPUState.R reg1, CPUState.R reg2, int word) {
    return CPUMath.sub16(word(reg1, reg2), word);
  }


  private void add(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    CPUMath.Result result = CPUMath.add8(registerValue, value);

    state.setReg(reg, result.get8());
    state.setFlag(CPUState.Flag.Z, result.get8() == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, result.getFlag(CPUState.Flag.H));
    state.setFlag(CPUState.Flag.C, result.getFlag(CPUState.Flag.C));
  }

  private void sub(CPUState.R reg, short value) {
    short registerValue = state.getReg(reg);
    CPUMath.Result result = CPUMath.sub8(registerValue, value);

    state.setReg(reg, result.get8());
    state.setFlag(CPUState.Flag.Z, result.get8() == 0);
    state.setFlag(CPUState.Flag.N, true);
    state.setFlag(CPUState.Flag.H, result.getFlag(CPUState.Flag.H));
    state.setFlag(CPUState.Flag.C, result.getFlag(CPUState.Flag.C));
  }


  // Load functions
  private void ld8(CPUState.R reg, short value) {
    state.setReg(reg, value);
  }

  private void ld16(CPUState.R reg1, CPUState.R reg2, short byte1, short byte2) {
    state.setReg16(reg1, reg2, byte1, byte2);
  }
  private void ld16(CPUState.R reg1, CPUState.R reg2, int word) {
    state.setReg16(reg1, reg2, word);
  }

  // Stack manipulators
  /*
  *  Pushes the word onto the stack in 2 parts
  *  example usage: PUSH BC
  *    [--SP] = B; [--SP] = C;
  */
  private void push(int word) {
    state.decSP();  // SP--
    state.decSP();  // SP--
    Util.debug("PUSH - address: " + Util.hex(state.SP()) + "\tvalue: " + Util.hex(word));
    writeMem16(state.SP(), word);  // [SP] = byte1
  }

  private int pop() {
    int word = readMem16(state.SP());
    state.incSP();
    state.incSP();
    Util.debug("POP - address: " + Util.hex(state.SP()) + "\tvalue: " + Util.hex(word));
    return word;
  }


  // MMU helpers
  private void writeMem8(int address, short value) {
    mmu.set(address, value);
  }

  private void writeMem16(int address, int word) {
    short[] bytes = CPUMath.toBytes(word);
    writeMem16(address, bytes[0], bytes[1]);
  }
  private void writeMem16(int address, short byte1, short byte2) {
    // Store in LITTLE ENDIAN order
    writeMem8(address, byte2);
    writeMem8(CPUMath.inc16(address), byte1);
  }

  private short readMem8(int address) {
    return mmu.get(address);
  }

  private int readMem16(int address) {
    short byte1 = readMem8(address);
    short byte2 = readMem8(CPUMath.inc16(address));
    // Undo LITTLE ENDIAN on retrieval
    return CPUMath.word(byte2, byte1);
  }

  // Jump helpers
  private void jump(int address) {
    if (address < 0 || address > 0xFFFF) {
      Util.errn("Instructions.jump - out of bounds error: 0x" + Util.hex(address));
      return;
    }
    // Util.debug("JUMP - address: " + Util.hex(address));
    state.setPC(address);
  }

  private int word(CPUState.R reg1, CPUState.R reg2) {
    return CPUMath.word(state.getReg(reg1), state.getReg(reg2));
  }
}
