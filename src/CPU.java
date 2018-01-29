
class CPU {

  private MMU mmu;
  private Cart cart;

  private short A;   // accumulator
  private short F;   // only flags, maybe combine with A?
  private short B;
  private short C;
  private short D;
  private short E;
  private short H;
  private short L;
  private int SP;  // stack pointer
  private int PC;  // program counter

  public enum Flag {
    Z,  // zero
    N,  // add/sub
    H,  // half carry
    C   // carry
  };

  public CPU(MMU mmu, Cart cart) {
    this.mmu = mmu;
    this.cart = cart;

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
    short instruction = mmu.get(PC);
    //Util.log("0x" + Util.hex(PC) + " " + Util.hex(instruction));

    decode(instruction);

    incPC();
  }

  private void decode(short instruction) {
    short tmp1 = 0;
    short tmp2 = 0;

    switch ((int) instruction) {  // cast to remove lossy conversion errors

      case 0x0E:
      case 0x20:
      case 0x21:
      case 0x26:
      case 0x31:  // LD SP,d16
        tmp1 = mmu.get(PC + 1);
        tmp2 = mmu.get(PC + 2);

        SP = tmp2;
        SP <<= 8;
        SP += tmp1;

        incPC();
        incPC();

        Util.log("result!!!!! " + Util.hex(SP));
        break;
      case 0x32:
      case 0x7C:
      case 0x9F:
      case 0xAF:
      case 0xCB:
      case 0xFB:
      case 0xFE:

      /*  TODO BROKEN - TEST HELPER FUNCTIONS
        tmp1 = mmu.get(PC + 1);
        tmp2 = (short)(A - tmp1);

        // flags
        zeroFlag(tmp2 == 0);
        arithmeticFlag(true);
        halfCarryFlag(subHasHalfCarry(A, tmp1));
        carryFlag(A < tmp1);

        incPC();

        break;
        */
      case 0xFF:
      default:
        Util.errn("CPU.decode - defaulted on instruction: " + Util.hex(instruction));
        break;
    }
  }

  private boolean addHasHalfCarry(short a, short b) {
    return (((a & 0xF) + (b & 0xF)) & 0x10) == 0x10;
  }

  private boolean subHasHalfCarry(short a, short b) {
    return ((a & 0xF) - (b & 0xF)) < 0;
  }


  private void incPC() { PC++; PC = PC % 0x10000; }
  private void decPC() { PC = PC == 0 ? 0xFFFF : PC - 1; }


  // Flag manipulators
  private void setFlag(Flag flag, boolean value) {
    if (flag == Flag.Z) {
      if (value)  F |= 0x80;
      else        F &= 0x7F;
    }
    else if (flag == Flag.N) {
      if (value)  F |= 0x40;
      else        F &= 0xBF;
    }
    else if (flag == Flag.H) {
      if (value)  F |= 0x20;
      else        F &= 0xDF;
    }
    else if (flag == Flag.C) {
      if (value)  F |= 0x10;
      else        F &= 0xEF;
    }
  }

  private boolean getFlag(Flag flag) {
    boolean val = false;

    if (flag == Flag.Z) {
      val = (F & 0x80) > 0;
    }
    else if (flag == Flag.N) {
      val = (F & 0x40) > 0;
    }
    else if (flag == Flag.H) {
      val = (F & 0x20) > 0;
    }
    else if (flag == Flag.C) {
      val = (F & 0x10) > 0;
    }

    return val;
  }

  public static void main(String args[]) {
    CPU cpu = new CPU(new MMU(), new Cart("../roms/bios.gb"));
  }
}
