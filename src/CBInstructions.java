
class CBInstructions {
  private CPUState state;
  private MMU mmu;
  private ClockCounter clockCounter;

  private CPUState.R bitHelpers[] = {
    CPUState.R.B,
    CPUState.R.C,
    CPUState.R.D,
    CPUState.R.E,
    CPUState.R.H,
    CPUState.R.L,
    CPUState.R.B, // placeholder for [HL]
    CPUState.R.A,
  };

  public CBInstructions(CPUState state, MMU mmu, ClockCounter clockCounter) {
    this.state = state;
    this.mmu = mmu;
    this.clockCounter = clockCounter;
  }
  private CBInstructions() {
  }

  public void handle(short instruction) {
    int bit3 = CPUMath.getBit(instruction, 3);

    clockCounter.add(2);

    // 0x00 - 0x0F
    if (instruction >= 0 && instruction < 0x10) { // RLC / RRC
      if (bit3 == 0)
        rlc(instruction);
      else
        rrc(instruction);
    }

    // 0x10 - 0x1F
    else if (instruction >= 0x10 && instruction < 0x20) { // RL / RR
      if (bit3 == 0)
        rl(instruction);
      else
        rr(instruction);
    }

    // 0x20 - 0x2F
    else if (instruction >= 0x20 && instruction < 0x30) { // SLA / SRA
      if (bit3 == 0)
        sla(instruction);
      else
        sra(instruction);
    }

    // 0x30 - 0x3F
    else if (instruction >= 0x30 && instruction < 0x40) { // SWAP / SRL
      if (bit3 == 0)
        swap(instruction);
      else
        srl(instruction);
    }

    // 0x40 - 0x7F
    else if (instruction >= 0x40 && instruction < 0x80) // BIT
      bit(instruction);

    // 0x80 - 0xBF
    else if (instruction >= 0x80 && instruction < 0xC0) // RES
      res(instruction);

    // 0xC0 - 0xFF
    else if (instruction >= 0xC0 && instruction <= 0xFF)// SET
      set(instruction);
  }

  /*
   * Good reference for insruction implementation
   * https://github.com/simias/gb-rs/blob/master/src/cpu/instructions.rs
   */

  //  - RLC
  private void rlc(short instruction) {
    int registerIndex = (short)((instruction << 5 & 0xFF) >> 5);

    if (registerIndex < 0 || registerIndex >= 8) return;

    // special case for [HL]
    if (registerIndex == 6) {
      int address = word(CPUState.R.H, CPUState.R.L);
      short value = readMem8(address);
      _rlc(address, value);
    }
    // Otherwise get the register index normally
    else {
      _rlc(bitHelpers[registerIndex]);
    }
  }

  private void _rlc(int address, short value) {
    short shifted = _rlc(value);
    writeMem8(address, shifted);
  }
  private void _rlc(CPUState.R reg) {
    short registerValue = state.getReg(reg);
    short shifted = _rlc(registerValue);
    state.setReg(reg, shifted);
  }
  private short _rlc(short value) {
    state.setFlag(CPUState.Flag.C, (value & (short)0x80) != 0);

    short shifted = (short)(((value << 1) | (value >> 7)) & 0xFF);

    state.setFlag(CPUState.Flag.Z, shifted == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, false);

    return shifted;
  }

  //  - RL
  private void rl(short instruction) {
    int registerIndex = (short)((instruction << 5 & 0xFF) >> 5);

    if (registerIndex < 0 || registerIndex >= 8) return;

    // special case for [HL]
    if (registerIndex == 6) {
      int address = word(CPUState.R.H, CPUState.R.L);
      short value = readMem8(address);
      _rl(address, value);
    }
    // Otherwise get the register index normally
    else {
      _rl(bitHelpers[registerIndex]);
    }
  }

  private void _rl(int address, short value) {
    clockCounter.add(2);

    short shifted = _rl(value);
    writeMem8(address, shifted);
  }
  private void _rl(CPUState.R reg) {
    short registerValue = state.getReg(reg);
    short shifted = _rl(registerValue);
    state.setReg(reg, shifted);
  }
  private short _rl(short value) {
    int oldCarry = state.getFlag(CPUState.Flag.C) ? 1 : 0;
    state.setFlag(CPUState.Flag.C, (value & (short)0x80) != 0);

    short shifted = (short)(((value << 1) | oldCarry) & 0xFF);

    state.setFlag(CPUState.Flag.Z, shifted == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, false);

    return shifted;
  }

  //  - RRC
  private void rrc(short instruction) {
    int registerIndex = (short)((instruction << 5 & 0xFF) >> 5);

    if (registerIndex < 0 || registerIndex >= 8) return;

    // special case for [HL]
    if (registerIndex == 6) {
      int address = word(CPUState.R.H, CPUState.R.L);
      short value = readMem8(address);
      _rrc(address, value);
    }
    // Otherwise get the register index normally
    else {
      _rrc(bitHelpers[registerIndex]);
    }
  }

  private void _rrc(int address, short value) {
    clockCounter.add(2);

    short shifted = _rrc(value);
    writeMem8(address, shifted);
  }
  private void _rrc(CPUState.R reg) {
    short registerValue = state.getReg(reg);
    short shifted = _rrc(registerValue);
    state.setReg(reg, shifted);
  }
  private short _rrc(short value) {
    state.setFlag(CPUState.Flag.C, (value & (short)0x01) != 0);

    short shifted = (short)(((value >> 1) | (value << 7)) & 0xFF);

    state.setFlag(CPUState.Flag.Z, shifted == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, false);

    return shifted;
  }

  //  - RR
  private void rr(short instruction) {
    int registerIndex = (short)((instruction << 5 & 0xFF) >> 5);

    if (registerIndex < 0 || registerIndex >= 8) return;

    // special case for [HL]
    if (registerIndex == 6) {
      int address = word(CPUState.R.H, CPUState.R.L);
      short value = readMem8(address);
      _rr(address, value);
    }
    // Otherwise get the register index normally
    else {
      _rr(bitHelpers[registerIndex]);
    }
  }

  private void _rr(int address, short value) {
    clockCounter.add(2);

    short shifted = _rr(value);
    writeMem8(address, shifted);

  }
  private void _rr(CPUState.R reg) {
    short registerValue = state.getReg(reg);
    short shifted = _rr(registerValue);
    state.setReg(reg, shifted);
  }
  private short _rr(short value) {
    int oldCarry = state.getFlag(CPUState.Flag.C) ? 1 : 0;
    state.setFlag(CPUState.Flag.C, (value & (short)0x01) != 0);

    short shifted = (short)(((value >> 1) | (oldCarry << 7)) & 0xFF);

    state.setFlag(CPUState.Flag.Z, shifted == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, false);

    return shifted;
  }

  //  - SLA
  private void sla(short instruction) {
    int registerIndex = (short)((instruction << 5 & 0xFF) >> 5);

    if (registerIndex < 0 || registerIndex >= 8) return;

    // special case for [HL]
    if (registerIndex == 6) {
      int address = word(CPUState.R.H, CPUState.R.L);
      short value = readMem8(address);
      _sla(address, value);
    }
    // Otherwise get the register index normally
    else {
      _sla(bitHelpers[registerIndex]);
    }
  }

  private void _sla(int address, short value) {
    clockCounter.add(2);

    short shifted = _sla(value);
    writeMem8(address, shifted);
  }
  private void _sla(CPUState.R reg) {
    short registerValue = state.getReg(reg);
    short shifted = _sla(registerValue);
    state.setReg(reg, shifted);
  }
  private short _sla(short value) {
    state.setFlag(CPUState.Flag.C, (value & (short)0x80) != 0);

    short shifted = (short)((value << 1) & 0xFF);

    state.setFlag(CPUState.Flag.Z, shifted == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, false);

    return shifted;
  }

  //  - SRA
  private void sra(short instruction) {
    int registerIndex = (short)((instruction << 5 & 0xFF) >> 5);

    if (registerIndex < 0 || registerIndex >= 8) return;

    // special case for [HL]
    if (registerIndex == 6) {
      int address = word(CPUState.R.H, CPUState.R.L);
      short value = readMem8(address);
      _sra(address, value);
    }
    // Otherwise get the register index normally
    else {
      _sra(bitHelpers[registerIndex]);
    }
  }

  private void _sra(int address, short value) {
    clockCounter.add(2);

    short shifted = _sra(value);
    writeMem8(address, shifted);
  }
  private void _sra(CPUState.R reg) {
    short registerValue = state.getReg(reg);
    short shifted = _sra(registerValue);
    state.setReg(reg, shifted);
  }
  private short _sra(short value) {
    state.setFlag(CPUState.Flag.C, (value & (short)0x01) != 0);

    // MSB isn't affected
    short shifted = (short)(((value >> 1) | (value & (short) 0x80)) & 0xFF);

    state.setFlag(CPUState.Flag.Z, shifted == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, false);

    return shifted;
  }

  // SWAP
  private void swap(short instruction) {
    int registerIndex = (short)((instruction << 5 & 0xFF) >> 5);

    if (registerIndex < 0 || registerIndex >= 8) return;

    // special case for [HL]
    if (registerIndex == 6) {
      int address = word(CPUState.R.H, CPUState.R.L);
      short value = readMem8(address);
      _swap(address, value);
    }
    // Otherwise get the register index normally
    else {
      _swap(bitHelpers[registerIndex]);
    }
  }

  private void _swap(int address, short value) {
    clockCounter.add(2);

    short newValue = _swap(value);
    writeMem8(address, newValue);
  }
  private void _swap(CPUState.R reg) {
    short registerValue = state.getReg(reg);
    short newValue = _swap(registerValue);
    state.setReg(reg, newValue);
  }
  private short _swap(short value) {
    short newValue = (short)((value << 4 | value >> 4) & 0xFF);

    state.setFlag(CPUState.Flag.Z, value == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, false);
    state.setFlag(CPUState.Flag.C, false);

    return newValue;
  }

  // SRL
  private void srl(short instruction) {
    int registerIndex = (short)((instruction << 5 & 0xFF) >> 5);

    if (registerIndex < 0 || registerIndex >= 8) return;

    // special case for [HL]
    if (registerIndex == 6) {
      int address = word(CPUState.R.H, CPUState.R.L);
      short value = readMem8(address);
      _srl(address, value);
    }
    // Otherwise get the register index normally
    else {
      _srl(bitHelpers[registerIndex]);
    }
  }

  private void _srl(int address, short value) {
    clockCounter.add(2);

    state.setFlag(CPUState.Flag.C, (value & (short)0x01) != 0);

    short shifted = (short)((value >> 1) & 0xFF);

    writeMem8(address, shifted);
    state.setFlag(CPUState.Flag.Z, shifted == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, false);
  }
  private void _srl(CPUState.R reg) {
    short value = state.getReg(reg);
    state.setFlag(CPUState.Flag.C, (value & (short)0x01) != 0);

    short shifted = (short)((value >> 1) & 0xFF);

    state.setReg(reg, shifted);
    state.setFlag(CPUState.Flag.Z, shifted == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, false);
  }

  //  - BIT N, R
  private void bit(short instruction) {
    int bitIndex = (short)((instruction << 2 & 0xFF) >> 5);
    int registerIndex = (short)((instruction << 5 & 0xFF) >> 5);

    if (registerIndex < 0 || registerIndex >= 8) return;

    // special case for [HL]
    if (registerIndex == 6) {
      int address = word(CPUState.R.H, CPUState.R.L);
      short value = readMem8(address);
      bit(bitIndex, value);
    }
    // Otherwise get the register index normally
    else {
      bit(bitIndex, bitHelpers[registerIndex]);
    }
  }

  private void bit(int index, CPUState.R reg) {
    short registerValue = state.getReg(reg);
    bit(index, registerValue);
  }
  private void bit(int index, short value) {
    clockCounter.add(2);

    int bit = CPUMath.getBit(value, index);

    state.setFlag(CPUState.Flag.Z, bit == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, true);
  }

  //  - RES N, R
  private void res(short instruction) {
    int bitIndex = (short)((instruction << 2 & 0xFF) >> 5);
    int registerIndex = (short)((instruction << 5 & 0xFF) >> 5);

    if (registerIndex < 0 || registerIndex >= 8) return;

    // special case for [HL]
    if (registerIndex == 6) {
      int address = word(CPUState.R.H, CPUState.R.L);
      short value = readMem8(address);
      res_mem(bitIndex, value, address);
    }
    // Otherwise get the register index normally
    else {
      res(bitIndex, bitHelpers[registerIndex]);
    }
  }

  private void res_mem(int index, short value, int address) {
    clockCounter.add(2);

    short newValue = CPUMath.resetBit(value, index);
    writeMem8(address, newValue);
  }
  private void res(int index, CPUState.R reg) {
    short registerValue = state.getReg(reg);
    short newValue = CPUMath.resetBit(registerValue, index);

    state.setReg(reg, newValue);
  }

  //  - SET N, R
  private void set(short instruction) {
    int bitIndex = (short)((instruction << 2 & 0xFF) >> 5);
    int registerIndex = (short)((instruction << 5 & 0xFF) >> 5);

    if (registerIndex < 0 || registerIndex >= 8) return;

    // special case for [HL]
    if (registerIndex == 6) {
      int addsets = word(CPUState.R.H, CPUState.R.L);
      short value = readMem8(addsets);
      set_mem(bitIndex, value, addsets);
    }
    // Otherwise get the register index normally
    else {
      set(bitIndex, bitHelpers[registerIndex]);
    }
  }

  private void set_mem(int index, short value, int address) {
    clockCounter.add(2);

    short newValue = CPUMath.setBit(value, index);
    writeMem8(address, newValue);
  }
  private void set(int index, CPUState.R reg) {
    short registerValue = state.getReg(reg);
    short newValue = CPUMath.setBit(registerValue, index);

    state.setReg(reg, newValue);
  }

  // Helpers
  private short readMem8(int address) {
    return mmu.get(address);
  }

  private void writeMem8(int address, short value) {
    mmu.set(address, value);
  }

  private int word(CPUState.R reg1, CPUState.R reg2) {
    return CPUMath.word(state.getReg(reg1), state.getReg(reg2));
  }
}
