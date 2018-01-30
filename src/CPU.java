
import java.util.HashMap;

class CPU {

  private MMU mmu;
  private Cart cart;
  private HashMap<Register, Short> registers;
  private InstructionHelper IH;

  public enum Flag {
    Z,  // zero
    N,  // add/sub
    H,  // half carry
    C   // carry
  };

  public enum Register { // registers
    A, F,
    B, C,
    D, E,
    H, L,
    SP_0, SP_1, // 16 bit register, so two parts
    PC_0, PC_1  // same
  }


  public CPU(MMU mmu, Cart cart) {
    this.mmu = mmu;
    this.cart = cart;
    this.registers = new HashMap<>();
    this.IH = new InstructionHelper(registers, mmu);

    setupRegisters();
    loadROM();
  }

  private void setupRegisters() {
    registers.put(Register.A, (short)0);
    registers.put(Register.F, (short)0);
    registers.put(Register.B, (short)0);
    registers.put(Register.C, (short)0);
    registers.put(Register.D, (short)0);
    registers.put(Register.E, (short)0);
    registers.put(Register.H, (short)0);
    registers.put(Register.L, (short)0);
    registers.put(Register.SP_0, (short)0);
    registers.put(Register.SP_1, (short)0);
    registers.put(Register.PC_0, (short)0);
    registers.put(Register.PC_1, (short)0);
  }

  // Load MMU's romBank00 and romBank01 with cart data
  private void loadROM() {
    for (int i = 0; i < cart.size(); i++) {
      mmu.set(i, cart.get(i));
    }
  }

  // Run instruction at mmu[PC]
  public void tick() {
    short instruction = mmu.get(IH.PC());
    Util.log(Util.hex(IH.PC()) + "\t" + Util.hex(instruction));

    decode(instruction);
    IH.incPC();

    //dump();
  }

  public void dump() {
    Util.log("");
    Util.log("A\t" + Util.hex(registers.get(Register.A)));
    Util.log("F\t" + Util.hex(registers.get(Register.F)));
    Util.log("B\t" + Util.hex(registers.get(Register.B)));
    Util.log("C\t" + Util.hex(registers.get(Register.C)));
    Util.log("D\t" + Util.hex(registers.get(Register.D)));
    Util.log("E\t" + Util.hex(registers.get(Register.E)));
    Util.log("H\t" + Util.hex(registers.get(Register.H)));
    Util.log("L\t" + Util.hex(registers.get(Register.L)));
    int SP = word(registers.get(Register.SP_0), registers.get(Register.SP_1));
    Util.log("SP\t" + Util.hex(SP));
    int PC = word(registers.get(Register.PC_0), registers.get(Register.PC_1));
    Util.log("PC\t" + Util.hex(PC));
    Util.log("");
  }

  /*
   * Instruction helpers
   *///FE 34 20 F3 11 D8 00 06 08 1A 13 22 23 05 20 F9
  private void decode(short instruction) {
    switch ((int) instruction) {  // cast to remove lossy conversion errors
      case 0x00:  // NOP
        break;
      case 0x01:  // LD BC,d16
        IH.ld16(Register.B, Register.C);
        break;
      case 0x04:  // INC B
        IH.inc(Register.B);
        break;
      case 0x05:  // DEC B
        IH.dec(Register.B);
        break;
      case 0x06:  // LD B,d8
        IH.ld8(Register.B);
        break;
      case 0x08:  // LD (a16),SP
        IH.ld16_push(Register.SP_0, Register.SP_1);
        break;
      case 0x0C:  // INC C
        IH.inc(Register.C);
        break;
      case 0x0E:  // LD C,d8
        IH.ld8(Register.C);
        break;
      case 0x10:  // STOP 0
        stop();
        break;
      case 0x11:  // LD DE,d16
        IH.ld16(Register.D, Register.E);
        break;
      case 0x13:  // INC DE
        IH.inc(Register.D, Register.E);
        break;
      case 0x1A:  // LD A,(DE)
        IH.ld8_pop(Register.A, Register.D, Register.E);
        break;
      case 0x20:  // JR NZ,r8
        IH.jrn(Flag.Z);
        break;
      case 0x21:  // LD HL,d16
        IH.ld16(Register.H, Register.L);
        break;
      case 0x22:  // LD (HL+),A
        IH.ld16_hli(Register.H, Register.L, Register.A);
        break;
      case 0x23:  // INC HL
        IH.inc(Register.H, Register.L);
        break;
      case 0x26:  // LD H,d8
        IH.ld8(Register.H);
        break;
      case 0x31:  // LD SP,d16
        IH.ld16(Register.SP_0, Register.SP_1);
        break;
      case 0x32:  // LD (HL-),A
        IH.ld16_hld(Register.H, Register.L, Register.A);
        break;
      case 0x34:  // INC (HL)
        IH.inc_mem(Register.H, Register.L);
        break;
      case 0x3E:  // LD A,d8
        IH.ld8(Register.A);
        break;
      case 0x47:  // LD B, A
        IH.ld8(Register.B, Register.A);
        break;
      case 0x77:  // LD (HL),A
        IH.ld16_hl(Register.H, Register.L, Register.A);
        break;
      case 0x7B:  // LD A,E
        IH.ld8(Register.A, Register.E);
        break;
      case 0x7C:  // LD A,H
        IH.ld8(Register.A, Register.H);
        break;
      case 0x80:  // ADD A,B
        IH.add(Register.A, Register.B);
        break;
      case 0x95:  // SUB L
        IH.sub(Register.L);
        break;
      case 0x96:  // SUB (HL)
        IH.sub_pop(Register.H, Register.L);
        break;
      case 0x9F:  // SBC A,A
        IH.sbc(Register.A);
        break;
      case 0xAF:  // XOR A
        IH.xor8(Register.A);
        break;
      case 0xCB:  // PREFIX CB
        IH.CB();
        break;
      case 0xCD:  // CALL a16
        IH.call();
        break;
      case 0xD8:  // RET C
        IH.ret(Flag.C);
        break;
      case 0xE0:  // LDH (a8),A
        IH.ldh_push();
        break;
      case 0xE2:  // LD (C),A
        IH.ld8_push(Register.C, Register.A);
        break;
      case 0xF3:  // DI
        IH.DI();
        break;
      case 0xF9:  // LD SP,HL
        IH.ld16(Register.SP_0, Register.SP_1, Register.H, Register.L);
        break;
      case 0xFB:  // EI
        IH.EI();
        break;
      case 0xFC:  // N/A
        unimplemented(instruction);
        break;
      case 0xFE:  // CP d8
        IH.cp();
        break;
      case 0xFF:  // RST 0x38
        IH.rst(0x38);
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

    Util.log("SP == " + cpu.SP());
  }

  private static int word(short a, short b) {
    return (((int)a) << 8) + b;
  }

  private int SP() {
    return word(registers.get(Register.SP_0), registers.get(Register.SP_1));
  }
}
