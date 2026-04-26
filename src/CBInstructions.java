
/**
 * Implementation of the 256-entry CB-prefixed opcode table.
 *
 * Most operations are 2 M-cycles (register) or 4 M-cycles (via
 * {@code (HL)}); {@code BIT n, (HL)} is an exception at 3 M-cycles.
 */
class CBInstructions {
  private final CPUState state;
  private final MMU mmu;
  private final ClockCounter clockCounter;

  private static final CPUState.R[] OPERANDS = {
    CPUState.R.B, CPUState.R.C,
    CPUState.R.D, CPUState.R.E,
    CPUState.R.H, CPUState.R.L,
    null,
    CPUState.R.A,
  };

  public CBInstructions(CPUState state, MMU mmu, ClockCounter clockCounter) {
    this.state = state;
    this.mmu = mmu;
    this.clockCounter = clockCounter;
  }

  /**
   * Dispatches a CB-prefixed opcode. The caller ({@link Instructions#CB()})
   * has already charged the 1 M-cycle for the CB fetch itself; we charge
   * the remaining cycles here and interleave them with the (HL) read /
   * write so that memory accesses happen on the right M-cycle boundary
   * for Blargg's mem_timing test.
   *
   * <p>Timings (total M-cycles, CB prefix included):
   *   <ul>
   *     <li>CB r          : 2 M (read/write reg on cycle 2)</li>
   *     <li>BIT n,(HL)    : 3 M (read on cycle 3)</li>
   *     <li>CB (HL) RMW   : 4 M (read on cycle 3, write on cycle 4)</li>
   *   </ul>
   */
  public void handle(short opcode) {
    int op = opcode & 0xFF;
    int z = op & 0x7;
    CPUState.R reg = OPERANDS[z];
    boolean memory = (reg == null);
    int high = op >> 3;
    int block = op >> 6;

    if (memory) {
      int address = ((state.getReg(CPUState.R.H) & 0xFF) << 8)
                    | (state.getReg(CPUState.R.L) & 0xFF);

      if (block == 0x1) {
        // BIT n, (HL) - 3 M-cycle. CB prefix is cycle 1, opcode fetch is
        // cycle 2, HL read is cycle 3.
        clockCounter.add(2);
        short value = mmu.get(address);
        int bit = (op >> 3) & 0x7;
        bit_n(bit, value);
        return;
      }

      // Read-modify-write on (HL) - 4 M-cycles. Cycle 2 is opcode fetch,
      // cycle 3 is the read, cycle 4 is the write.
      clockCounter.add(2);
      short value = mmu.get(address);
      value = applyCbOp(high, block, op, value);
      clockCounter.add(1);
      mmu.set(address, value);
      return;
    }

    // Register operations - 2 M-cycles total (1 for CB fetch + 1 here).
    clockCounter.add(1);
    short value = state.getReg(reg);
    if (block == 0x1) {
      int bit = (op >> 3) & 0x7;
      bit_n(bit, value);
      return;
    }
    value = applyCbOp(high, block, op, value);
    state.setReg(reg, value);
  }

  private short applyCbOp(int high, int block, int op, short value) {
    switch (high) {
      case 0x00: return rlc(value);
      case 0x01: return rrc(value);
      case 0x02: return rl(value);
      case 0x03: return rr(value);
      case 0x04: return sla(value);
      case 0x05: return sra(value);
      case 0x06: return swap(value);
      case 0x07: return srl(value);
      default: break;
    }
    int bit = (op >> 3) & 0x7;
    if (block == 0x2) return (short)(value & ~(1 << bit) & 0xFF);
    if (block == 0x3) return (short)((value | (1 << bit)) & 0xFF);
    return value;
  }

  private short rlc(short v) {
    int b = v & 0xFF;
    int bit7 = (b >> 7) & 1;
    int r = ((b << 1) | bit7) & 0xFF;
    setShiftFlags(r, bit7);
    return (short)r;
  }

  private short rrc(short v) {
    int b = v & 0xFF;
    int bit0 = b & 1;
    int r = ((b >> 1) | (bit0 << 7)) & 0xFF;
    setShiftFlags(r, bit0);
    return (short)r;
  }

  private short rl(short v) {
    int b = v & 0xFF;
    int bit7 = (b >> 7) & 1;
    int carryIn = state.getFlag(CPUState.Flag.C) ? 1 : 0;
    int r = ((b << 1) | carryIn) & 0xFF;
    setShiftFlags(r, bit7);
    return (short)r;
  }

  private short rr(short v) {
    int b = v & 0xFF;
    int bit0 = b & 1;
    int carryIn = state.getFlag(CPUState.Flag.C) ? 1 : 0;
    int r = ((b >> 1) | (carryIn << 7)) & 0xFF;
    setShiftFlags(r, bit0);
    return (short)r;
  }

  private short sla(short v) {
    int b = v & 0xFF;
    int bit7 = (b >> 7) & 1;
    int r = (b << 1) & 0xFF;
    setShiftFlags(r, bit7);
    return (short)r;
  }

  private short sra(short v) {
    int b = v & 0xFF;
    int bit0 = b & 1;
    int bit7 = b & 0x80;
    int r = ((b >> 1) | bit7) & 0xFF;
    setShiftFlags(r, bit0);
    return (short)r;
  }

  private short srl(short v) {
    int b = v & 0xFF;
    int bit0 = b & 1;
    int r = (b >> 1) & 0xFF;
    setShiftFlags(r, bit0);
    return (short)r;
  }

  private short swap(short v) {
    int b = v & 0xFF;
    int r = ((b >> 4) | (b << 4)) & 0xFF;
    state.resetFlags();
    state.setFlag(CPUState.Flag.Z, r == 0);
    return (short)r;
  }

  private void bit_n(int bit, short value) {
    boolean bitSet = ((value & 0xFF) & (1 << bit)) != 0;
    state.setFlag(CPUState.Flag.Z, !bitSet);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, true);
  }

  private void setShiftFlags(int result, int carryOut) {
    state.resetFlags();
    state.setFlag(CPUState.Flag.Z, (result & 0xFF) == 0);
    state.setFlag(CPUState.Flag.C, carryOut != 0);
  }
}
