
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
   */
  private void decode(short instruction) {
    switch ((int) instruction) {  // cast to remove lossy conversion errors
      case 0x0E:  // LD C,d8
        IH.ld8(Register.C);
        break;
      case 0x20:  // JR NZ,r8
        //dump();
        IH.jrn(Flag.Z);
        //dump();
        break;
      case 0x21:  // LD HL,d16
        IH.ld16(Register.H, Register.L);
        break;
      case 0x26:  // LD H,d8
        IH.ld8(Register.H);
        break;
      case 0x31:  // LD SP,d16
        IH.ld16(Register.SP_0, Register.SP_1);
        break;
      case 0x32:  // LD (HL-),A
        IH.ld16_hli(Register.H, Register.L, Register.A);
        break;
      case 0x7C:  // LD A,H
        IH.ld8(Register.A, Register.H);
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
      case 0xFB:  // EI
        IH.EI();
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
