
class CBInstructions {
  private CPUState state;
  private MMU mmu;

  public CBInstructions(CPUState state, MMU mmu) {
    this.state = state;
    this.mmu = mmu;
  }
  private CBInstructions() {
  }

  public void handle(int instruction) {
    // TODO do some smart math to decode instruction instead
    switch ((short) instruction) {
      case 0x7C: // BIT 7,H
        bit(7, CPUState.R.H);
        break;
      default:
        Util.errn("CB prefixed instructions not implemented.");
        break;
    }
  }

   // - 8bit rotations/shifts and bit instructions (prefix CB)
   //     - <INS> B
   //     - <INS> [HL]
   //         - <INS> = {rlc, rrc, rl, rr, sla, sra, swap, srl, bit <N>, res <N>, set <N>}
   //         - <N> = 0-7

  private void bit(int index, CPUState.R reg) {
    short registerValue = state.getReg(reg);
    registerValue <<= (7 - index);  // clear the left
    registerValue >>= index;        // clear the right

    state.setFlag(CPUState.Flag.Z, registerValue == 0);
    state.setFlag(CPUState.Flag.N, false);
    state.setFlag(CPUState.Flag.H, true);
  }
}
