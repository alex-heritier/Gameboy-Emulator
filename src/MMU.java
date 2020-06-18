
import java.util.ArrayList;
import java.util.Collections;

class MMU implements DataSource {
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

  public int lastAccessed;


  public MMU() {
    mem = new short[0xFFFF + 1];
    init();
  }

  private void _write(int address, short value) {
    if (address == 0xFF00)
      value = Joypad.getState(value);

    if (address > 0x8000 && address <= 0x9800) {
      // Thread.dumpStack();
    }

    mem[address] = value;

    lastAccessed = address;
  }

  private short _read(int address) {
    // Handle joypad data
    if (address == 0xFF00) {
      short joypadState = Joypad.getState(mem[address]);
      // Util.log("MMU - from Joypad: " + Util.bin(joypadState));
      return joypadState;
    }

    return mem[address];
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
      return _read(address - 0x2000);
    }

    return _read(address);
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

    _write(address, value);

    // IO
    if (address >= 0xFF00 && address < 0xFF80) {
      handleIO(address, value);
    }
    // Not usable
    else if (address >= 0xFEA0 && address < 0xFF00) {
      _write(address, (short)0);
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
    Util.debug("Raising interrupt " + Util.getInterruptName(interruptIndex));
    set(0xFF0F, CPUMath.setBit(interruptFlags, interruptIndex));
  }

  private void handleIO(int address, short value) {
    // Log Link Cable writes
    if (address == 0xFF02 && value == 0x81) {
      short data = get(0xFF01);
      System.out.print((char)data);
    }

    // DIV timer
    else if (address == 0xFF04) _write(address, (short)0);

    // OAM DMA
    else if (address == 0xFF46) dma(value);
  }

  private void init() {
    clear();
    forceSet(0xFF00, (short)0x1F);  // initialize joypad
    forceSet(0xFF40, (short)0x80);
  }

  private void clear() {
    for (int i = 0; i <= 0xFFFF; i++) {
      set(i, (short)0x00);
    }
  }

  private void dma(short offset) {
    int sourceBase = (offset << 8) & 0xFF00;
    int destinationBase = 0xFE00;
    for (int i = 0; i < 0xA0; i++) {
      short sourceValue = get(sourceBase + i);
      int destination = destinationBase + i;
      set(destination, sourceValue);
    }
  }

  public int lastAccessed() {
    int z = lastAccessed;
    lastAccessed = -1;
    return z;
  }

  public String toString() {
    String z = "MEM";
    return z;
  }
}
