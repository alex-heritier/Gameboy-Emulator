
import java.util.HashMap;

class CPU {

  private MMU mmu;
  private Cart cart;
  private CPUState state;
  private Instructions ins;


  public CPU(MMU mmu, Cart cart) {
    this.mmu = mmu;
    this.cart = cart;
    this.state = new CPUState();
    this.ins = new Instructions(state, mmu);

    loadROM();
  }

  // Load MMU's romBank00 and romBank01 with cart data
  private void loadROM() {
    for (int i = 0; i < cart.size(); i++) {
      mmu.set(i, cart.get(i));
    }
  }

  // Run instruction at mmu[PC]
  public void tick() {
    short instruction = mmu.get(ins.PC());
    Util.log(Util.hex(ins.PC()) + "\t" + Util.hex(instruction));

    decode(instruction);
    ins.incPC();

    //dump();
  }

  public void dump() {
    Util.log("");
    Util.log("A\t" + Util.hex(state.getReg(CPUState.R.A)));
    Util.log("F\t" + Util.hex(state.getReg(CPUState.R.F)));
    Util.log("B\t" + Util.hex(state.getReg(CPUState.R.B)));
    Util.log("C\t" + Util.hex(state.getReg(CPUState.R.C)));
    Util.log("D\t" + Util.hex(state.getReg(CPUState.R.D)));
    Util.log("E\t" + Util.hex(state.getReg(CPUState.R.E)));
    Util.log("H\t" + Util.hex(state.getReg(CPUState.R.H)));
    Util.log("L\t" + Util.hex(state.getReg(CPUState.R.L)));
    int SP = state.getReg16(CPUState.R.SP_0, CPUState.R.SP_1);
    Util.log("SP\t" + Util.hex(SP));
    int PC = state.getReg16(CPUState.R.PC_0, CPUState.R.PC_1);
    Util.log("PC\t" + Util.hex(PC));
    Util.log("");
  }

  /*
   * Instruction helpers
   */
  private void decode(short instruction) {
    switch ((int) instruction) {  // cast to remove lossy conversion errors
      case 0x00:  // NOP
        break;
      case 0x01:  // LD BC,d16
        ins.ld16(CPUState.R.B, CPUState.R.C);
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
      case 0x08:  // LD (a16),SP
        ins.ld16_push(CPUState.R.SP_0, CPUState.R.SP_1);
        break;
      case 0x0C:  // INC C
        ins.inc(CPUState.R.C);
        break;
      case 0x0E:  // LD C,d8
        ins.ld8(CPUState.R.C);
        break;
      case 0x10:  // STOP 0
        stop();
        break;
      case 0x11:  // LD DE,d16
        ins.ld16(CPUState.R.D, CPUState.R.E);
        break;
      case 0x13:  // INC DE
        ins.inc(CPUState.R.D, CPUState.R.E);
        break;
      case 0x1A:  // LD A,(DE)
        ins.ld8_pop(CPUState.R.A, CPUState.R.D, CPUState.R.E);
        break;
      case 0x20:  // JR NZ,r8
        ins.jrn(CPUState.Flag.Z);
        break;
      case 0x21:  // LD HL,d16
        ins.ld16(CPUState.R.H, CPUState.R.L);
        break;
      case 0x22:  // LD (HL+),A
        ins.ld16_hli(CPUState.R.H, CPUState.R.L, CPUState.R.A);
        break;
      case 0x23:  // INC HL
        ins.inc(CPUState.R.H, CPUState.R.L);
        break;
      case 0x26:  // LD H,d8
        ins.ld8(CPUState.R.H);
        break;
      case 0x31:  // LD SP,d16
        ins.ld16(CPUState.R.SP_0, CPUState.R.SP_1);
        break;
      case 0x32:  // LD (HL-),A
        ins.ld16_hld(CPUState.R.H, CPUState.R.L, CPUState.R.A);
        break;
      case 0x34:  // INC (HL)
        ins.inc_mem(CPUState.R.H, CPUState.R.L);
        break;
      case 0x3E:  // LD A,d8
        ins.ld8(CPUState.R.A);
        break;
      case 0x47:  // LD B, A
        ins.ld8(CPUState.R.B, CPUState.R.A);
        break;
      case 0x77:  // LD (HL),A
        ins.ld16_hl(CPUState.R.H, CPUState.R.L, CPUState.R.A);
        break;
      case 0x7B:  // LD A,E
        ins.ld8(CPUState.R.A, CPUState.R.E);
        break;
      case 0x7C:  // LD A,H
        ins.ld8(CPUState.R.A, CPUState.R.H);
        break;
      case 0x80:  // ADD A,B
        ins.add(CPUState.R.A, CPUState.R.B);
        break;
      case 0x95:  // SUB L
        ins.sub(CPUState.R.L);
        break;
      case 0x96:  // SUB (HL)
        ins.sub_pop(CPUState.R.H, CPUState.R.L);
        break;
      case 0x9F:  // SBC A,A
        ins.sbc(CPUState.R.A);
        break;
      case 0xAF:  // XOR A
        ins.xor8(CPUState.R.A);
        break;
      case 0xCB:  // PREFIX CB
        ins.CB();
        break;
      case 0xCD:  // CALL a16
        ins.call();
        break;
      case 0xD8:  // RET C
        ins.ret(CPUState.Flag.C);
        break;
      case 0xE0:  // LDH (a8),A
        ins.ldh_push();
        break;
      case 0xE2:  // LD (C),A
        ins.ld8_push(CPUState.R.C, CPUState.R.A);
        break;
      case 0xF3:  // DI
        ins.DI();
        break;
      case 0xF9:  // LD SP,HL
        ins.ld16(CPUState.R.SP_0, CPUState.R.SP_1, CPUState.R.H, CPUState.R.L);
        break;
      case 0xFB:  // EI
        ins.EI();
        break;
      case 0xFC:  // N/A
        unimplemented(instruction);
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

  private void stop() {
    Util.log("STOP");
  }

  private void unimplemented(short instruction) {
    Util.errn("Tried to run unimplemented instruction 0x" + instruction);
  }


  public static void main(String args[]) {
    CPU cpu = new CPU(new MMU(), new Cart("../roms/bios.gb"));

    Util.log("SP == " + cpu.state.getReg16(CPUState.R.SP_0, CPUState.R.SP_1));
  }
}
