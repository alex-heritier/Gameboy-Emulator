
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
      case 0x21:  // LD HL,d16
        incPC();
        tmp1 = mmu.get(PC);
        incPC();
        tmp2 = mmu.get(PC);

        H = tmp2;
        L = tmp1;

        break;
      case 0x26:
      case 0x31:  // LD SP,d16
        incPC();
        tmp1 = mmu.get(PC);
        incPC();
        tmp2 = mmu.get(PC);

        SP = word(tmp2, tmp1);
        Util.log("result!!!!! " + Util.hex(SP));
        break;
      case 0x32:
      case 0x7C:
      case 0x9F:  // SBC A,A
        tmp1 = (short)(A + (getFlag(Flag.C) ? 1 : 0));

        A -= tmp1;

        setFlag(Flag.Z, A == 0);
        setFlag(Flag.N, true);
        setFlag(Flag.H, subHasHalfCarry(A, tmp1));
        setFlag(Flag.C, subHasCarry(A, tmp1));

        break;
      case 0xAF:  // XOR A
        A ^= A; // should always set A to 0

        resetFlags();
        setFlag(Flag.Z, A == 0);  // should always be set

        break;
      case 0xCB:
      case 0xFB:
      case 0xFE:  // CP d8
        incPC();
        tmp1 = mmu.get(PC);
        tmp2 = (short)(A - tmp1);

        // flags
        setFlag(Flag.Z, tmp2 == 0);
        setFlag(Flag.N, true);
        setFlag(Flag.H, subHasHalfCarry(A, tmp1));
        setFlag(Flag.C, subHasCarry(A, tmp1));


        break;
      case 0xFF:  // RST 0x38
        push(PC);

        jump(0x38);

        break;
      default:
        Util.errn("CPU.decode - defaulted on instruction: " + Util.hex(instruction));
        break;
    }
  }

  private static int word(short a, short b) {
    return (((int)a) << 8) + b;
  }

  private static boolean addHasHalfCarry(short a, short b) {
    return (((a & 0xF) + (b & 0xF)) & 0x10) == 0x10;
  }

  private static boolean subHasHalfCarry(short a, short b) {
    return ((a & 0xF) - (b & 0xF)) < 0;
  }

  private static boolean subHasCarry(short a, short b) {
    return a < b;
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
    else {
      Util.errn("CPU.getFlag - bad flag " + flag);
    }

    return val;
  }

  private void resetFlags() {
    F &= 0x0F;  // set all flags to 0
  }

  // Stack manipulators
  /*
   *  Pushes the word onto the stack in 2 parts
   *  example usage: PUSH BC
   *    [--SP] = B; [--SP] = C;
   */
  private void push(int word) {
    short[] bytes = getBytes(word);

    decSP();  // SP--
    mmu.set(SP, bytes[0]);  // [SP] = byte1

    decSP();
    mmu.set(SP, bytes[1]);
  }

  private int pop() {
    short byte1 = 0;
    short byte2 = 0;

    byte1 = mmu.get(SP);
    incSP();
    byte2 = mmu.get(SP);
    incSP();

    return word(byte2, byte1);
  }

  private static short[] getBytes(int word) {
    short[] bytes = new short[2];

    bytes[0] = (short)(word >> 8);
    bytes[1] = (short)(word & 0xFF);

    return bytes;
  }

  private void incSP() { SP++; SP = SP % 0x10000; }
  private void decSP() { SP = SP == 0 ? 0xFFFF : SP - 1; }


  // Jump helpers
  private void jump(int address) {
    if (address < 0 || address > 0xFFFF) {
      Util.errn("CPU.jump - out of bounds error: 0x" + Util.hex(address));
      return;
    }

    PC = address;
  }

  public static void main(String args[]) {
    CPU cpu = new CPU(new MMU(), new Cart("../roms/bios.gb"));

    Util.log("SP == " + cpu.SP);
  }
}
