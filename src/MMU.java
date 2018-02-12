
import java.util.ArrayList;
import java.util.Collections;

class MMU implements DataSource {

  private ArrayList<Pair> memoryAccesses;
  private boolean logWrites;

  private PPU ppu;

  // ier;           FFFF,        Interrupt Enable Register
  // highRAM;       FF80 - FFFE
  // io;            FF00 - FF7F, IO ports
  // notUsable;     FEA0 - FEFF, not  usable
  // oam;           FE00 - FE9F, sprite attribute table
  // echo;          E000 - FDFF, copy of C000 - DDFF
  // workRAM1;      D000 - DFFF, switchable banks 1-7 in CGB
  // workRAM0;      C000 - CFFF
  // externalRAM;   A000 - BFFF, bankable
  // vram;          8000 - 9FFF, 2 banks
  // romBank01;     4000 - 7FFF, bankable
  // romBank00;     0000 - 3FFF
  private short[] mem;


  public MMU(PPU ppu) {
    memoryAccesses = new ArrayList<Pair>();
    logWrites = false;
    mem = new short[0xFFFF + 1];

    this.ppu = ppu;
    clear();
  }

  /*
   * Get a value from memory
   */
  @Override
  public short get(int address) {
    // check for out-of-bounds
    if (address < 0 || address > 0xFFFF) {
      Util.errn("MMU.get - out of bounds memory access: 0x" + Util.hex(address));
      return 0;
    }

    // Echo's 0xC000 - 0xDDFF
    if (address >= 0xE000 && address < 0xFE00) {
      return mem[address - 0x2000];
    }

    return mem[address];
  }

  /*
   * Set a value in memory
   */
  @Override
  public void set(int address, short value) {
    // check for out-of-bounds
    if (address < 0 || address > 0xFFFF) {
      Util.errn("MMU.set - out of bounds memory access: 0x" + Util.hex(address));
      return;
    }

    if (logWrites) memoryAccesses.add(new Pair(address, value));
    // Util.log("WRITING - " + Util.hex(address) + "\t" + Util.hex(value));

    mem[address] = value;

    // IO
    if (address >= 0xFF00 && address < 0xFF80) {
      handleIO(address, value);
    }
    // Not usable
    else if (address >= 0xFEA0 && address < 0xFF00) {
      mem[address] = 0;
    }
  }

  // Allows memory to be set without following normal MMU logic
  public void forceSet(int address, short value) {
    mem[address] = value;
  }


  public void raiseInterrupt(int interruptIndex) {
    if (interruptIndex < 0 || interruptIndex > 4) {
      Util.errn("MMU.raiseInterrupt - Bad interrupt index: " + interruptIndex);
      return;
    }

    short interruptFlags = get(0xFF0F);
    set(0xFF0F, CPUMath.setBit(interruptFlags, interruptIndex));
  }

  private void handleIO(int address, short value) {
    // Log Link Cable writes
    if (address == 0xFF02 && value == 0x81) {
      short data = get(0xFF01);
      System.out.print((char)data);
    }

    // 0xFF04
    if (address == 0xFF04) mem[address] = 0;

    ppu.handleIOPortWrite(address, value);
  }

  private void clear() {
    for (int i = 0; i <= 0xFFFF; i++) {
      set(i, (short)0x00);
    }
  }

  public void log(boolean b) {
    logWrites = b;
  }

  public void dump() {
    Util.log("Memory accesses");
    Collections.sort(memoryAccesses);
    for (Pair pair : memoryAccesses) {
      Util.log(Util.hex(pair.address) + "\t" + Util.hex(pair.value));
    }
  }

  public String toString() {
    String z = "";

    int rowMarker = 0x10;
    int columnMarker = 0x04;

    int i = 0;

    // VRAM
    z += "VRAM\n";
    for (i = 0x8000; i < 0xA000; i++) {
      z += Util.hex(get(i));

      if ((i + 1) % rowMarker == 0)        z += "\n";
      else if ((i + 1) % columnMarker == 0)    z += " ";
    }
    z += "\n";

    return z;
  }

  private class Pair implements Comparable {
    public int address;
    public short value;

    public Pair(int address, short value) {
      this.address = address;
      this.value = value;
    }

    public int compareTo(Object o) {
      Pair p2 = (Pair) o;
      if (address != p2.address)  return p2.address - address;
      else                        return p2.value - value;
    }
  }
}
