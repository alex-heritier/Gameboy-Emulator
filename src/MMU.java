
import java.util.ArrayList;
import java.util.Collections;

class MMU implements DataSource {

  private ArrayList<Pair> memoryAccesses;
  private boolean logWrites;

  private PPU ppu;

  private short ier;             // FFFF,        Interrupt Enable Register
  private short[] highRAM;       // FF80 - FFFE
  private short[] io;            // FF00 - FF7F, IO ports
  private short[] notUsable;     // FEA0 - FEFF, not  usable
  private short[] oam;           // FE00 - FE9F, sprite attribute table
  private short[] echo;          // E000 - FDFF, copy of C000 - DDFF
  private short[] workRAM1;      // D000 - DFFF, switchable banks 1-7 in CGB
  private short[] workRAM0;      // C000 - CFFF
  private short[] externalRAM;   // A000 - BFFF, bankable
  private short[] vram;          // 8000 - 9FFF, 2 banks
  private short[] romBank01;     // 4000 - 7FFF, bankable
  private short[] romBank00;     // 0000 - 3FFF

  /*
  Jump Vectors in First ROM Bank
The following addresses are supposed to be used as jump vectors:
  0000,0008,0010,0018,0020,0028,0030,0038   for RST commands
  0040,0048,0050,0058,0060                  for Interrupts
However, the memory may be used for any other purpose in case that your program
doesn't use any (or only some) RST commands or Interrupts. RST commands are
1-byte opcodes that work similiar to CALL opcodes, except that the destination
address is fixed.

Cartridge Header in First ROM Bank
The memory at 0100-014F contains the cartridge header. This area contains
information about the program, its entry point, checksums, information about
the used MBC chip, the ROM and RAM sizes, etc. Most of the bytes in this area
are required to be specified correctly. For more information read the chapter
about The Cartridge Header.
*/

  public MMU(PPU ppu) {
    memoryAccesses = new ArrayList<Pair>();
    logWrites = false;

    this.ppu = ppu;

    highRAM       = new short[0xFFFF - 0xFF80];
    io            = new short[0xFF80 - 0xFF00];
    notUsable     = new short[0xFF00 - 0xFEA0];
    oam           = new short[0xFEA0 - 0xFE00];
    echo          = new short[0xFE00 - 0xE000];
    workRAM1      = new short[0xE000 - 0xD000];
    workRAM0      = new short[0xD000 - 0xC000];
    externalRAM   = new short[0xC000 - 0xA000];
    vram          = new short[0xA000 - 0x8000];
    romBank01     = new short[0x8000 - 0x4000];
    romBank00     = new short[0x4000];

    clear();
  }

  @Override
  public short get(int address) {
    // check for out-of-bounds
    if (address < 0 || address > 0xFFFF) {
      Util.errn("MMU.get - out of bounds memory access: 0x" + Util.hex(address));
      return 0;
    }

    // 0xFFFF
    if (address == 0xFFFF)
      return ier;
    // 0xFF80 - 0xFFFE
    else if (address >= 0xFF80)
      return highRAM[address - 0xFF80];
    else if (address >= 0xFF00)
      return io[address - 0xFF00];
    else if (address >= 0xFEA0)
      return notUsable[address - 0xFEA0];
    else if (address >= 0xFE00)
      return oam[address - 0xFE00];
    else if (address >= 0xE000)
      return echo[address - 0xE000];
    else if (address >= 0xD000)
      return workRAM1[address - 0xD000];
    else if (address >= 0xC000)
      return workRAM0[address - 0xC000];
    else if (address >= 0xA000)
      return externalRAM[address - 0xA000];
    else if (address >= 0x8000)
      return vram[address - 0x8000];
    else if (address >= 0x4000)
      return romBank01[address - 0x4000];
    else
      return romBank00[address];
  }

  @Override
  public void set(int address, short value) {
    // check for out-of-bounds
    if (address < 0 || address > 0xFFFF) {
      Util.errn("MMU.set - out of bounds memory access: 0x" + Util.hex(address));
      return;
    }

    if (logWrites) memoryAccesses.add(new Pair(address, value));

    // 0xFFFF
    if (address == 0xFFFF)
      ier = value;
    // 0xFF80 - 0xFFFE
    else if (address >= 0xFF80)
      highRAM[address - 0xFF80] = value;
    else if (address >= 0xFF00) {
      io[address - 0xFF00] = value;
      handleIO(address, value);
    }
    else if (address >= 0xFEA0)
      notUsable[address - 0xFEA0] = value;
    else if (address >= 0xFE00)
      oam[address - 0xFE00] = value;
    else if (address >= 0xE000)
      echo[address - 0xE000] = value;
    else if (address >= 0xD000)
      workRAM1[address - 0xD000] = value;
    else if (address >= 0xC000)
      workRAM0[address - 0xC000] = value;
    else if (address >= 0xA000)
      externalRAM[address - 0xA000] = value;
    else if (address >= 0x8000)
      vram[address - 0x8000] = value;
    else if (address >= 0x4000)
      romBank01[address - 0x4000] = value;
    else
      romBank00[address] = value;
  }

  private void handleIO(int address, short value) {
    ppu.handle(address, value);
  }

  private void clear() {
    for (int i = 0; i < 0x10000; i++) {
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

    // romBank00
    // z += "Rom Bank 00\n0x" + Util.hex(i) + "\t";
    // for (i = 0; i < 0x4000; i++) {
    //   z += Util.hex(get(i));
    //
    //   if ((i + 1) % rowMarker == 0)        z += "\n0x" + Util.hex(i + 1) + "\t";
    //   else if ((i + 1) % columnMarker == 0)    z += " ";
    // }
    // z += "\n";
    //
    // // romBank01
    // z += "Rom Bank 01\n";
    // for (i = 0x4000; i < 0x8000; i++) {
    //   z += Util.hex(get(i));
    //
    //   if ((i + 1) % rowMarker == 0)        z += "\n";
    //   else if ((i + 1) % columnMarker == 0)    z += " ";
    // }
    // z += "\n";


    // VRAM
    z += "VRAM\n";
    for (i = 0x8000; i < 0xA000; i++) {
      z += Util.hex(get(i));

      if ((i + 1) % rowMarker == 0)        z += "\n";
      else if ((i + 1) % columnMarker == 0)    z += " ";
    }
    z += "\n";

    // everything else
    // z += "Everything else\n";
    // for (; i < 0x10000; i++) {
    //   z += Util.hex(get(i));
    //
    //   if ((i + 1) % rowMarker == 0)        z += "\n";
    //   else if ((i + 1) % columnMarker == 0)    z += " ";
    // }
    // z += "\n";

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
