
/**
 * Memory Management Unit.
 *
 * Owns the 64 KiB CPU address space and routes reads/writes through the
 * cartridge MBC, I/O register handlers, joypad, and PPU/APU.
 *
 * Memory map (from {@code http://bgb.bircd.org/pandocs.htm}):
 *   0x0000-0x3FFF  ROM bank 00 (via MBC)
 *   0x4000-0x7FFF  ROM bank 01..N (via MBC)
 *   0x8000-0x9FFF  VRAM
 *   0xA000-0xBFFF  External cart RAM (via MBC)
 *   0xC000-0xDFFF  Work RAM
 *   0xE000-0xFDFF  Echo of 0xC000-0xDDFF
 *   0xFE00-0xFE9F  OAM
 *   0xFEA0-0xFEFF  Prohibited
 *   0xFF00-0xFF7F  I/O registers
 *   0xFF80-0xFFFE  HRAM
 *   0xFFFF          Interrupt enable
 */
class MMU {
  public static final int IF = 0xFF0F;
  public static final int IE = 0xFFFF;

  public static final int VBLANK = 0;
  public static final int LCD_STAT = 1;
  public static final int TIMER = 2;
  public static final int SERIAL = 3;
  public static final int JOYPAD = 4;

  private final short[] mem = new short[0x10000];
  private final Cart cart;
  private final Joypad joypad;

  private SerialListener serialListener;
  private boolean printSerialToStdout;

  // Forward references populated after construction.
  private TimerHandler timer;
  private PPU ppu;
  private APU apu;

  public int lastAccessed;

  public MMU(Cart cart, SerialListener serialListener, boolean printSerialToStdout) {
    this.cart = cart;
    this.serialListener = serialListener;
    this.printSerialToStdout = printSerialToStdout;
    this.joypad = new Joypad(this);
    resetIoRegisters();
  }

  public void attach(TimerHandler timer, PPU ppu, APU apu) {
    this.timer = timer;
    this.ppu = ppu;
    this.apu = apu;
  }

  public Joypad joypad() { return joypad; }
  public Cart cart() { return cart; }

  /** Reads a byte from the CPU address space. */
  public short get(int address) {
    address &= 0xFFFF;
    lastAccessed = address;

    if (address < 0x8000) return cart != null ? cart.readRom(address) : (short)0xFF;
    if (address < 0xA000) return mem[address];
    if (address < 0xC000) return cart != null ? cart.readRam(address) : (short)0xFF;
    if (address < 0xE000) return mem[address];
    if (address < 0xFE00) return mem[address - 0x2000]; // echo RAM
    if (address < 0xFEA0) return mem[address];
    if (address < 0xFF00) return (short)0xFF;           // prohibited region

    return readIO(address);
  }

  /** Writes a byte to the CPU address space. */
  public void set(int address, short value) {
    address &= 0xFFFF;
    value = (short)(value & 0xFF);
    lastAccessed = address;

    if (address < 0x8000) {
      if (cart != null) cart.writeRom(address, value);
      return;
    }
    if (address < 0xA000) { mem[address] = value; return; }
    if (address < 0xC000) {
      if (cart != null) cart.writeRam(address, value);
      return;
    }
    if (address < 0xE000) { mem[address] = value; return; }
    if (address < 0xFE00) { mem[address - 0x2000] = value; return; }
    if (address < 0xFEA0) { mem[address] = value; return; }
    if (address < 0xFF00) { return; } // prohibited region

    writeIO(address, value);
  }

  /** Writes directly to the backing memory, bypassing all handlers. */
  public void forceSet(int address, short value) {
    address &= 0xFFFF;
    mem[address] = (short)(value & 0xFF);
  }

  /** Reads directly from the backing memory, bypassing all handlers. */
  public short forceGet(int address) {
    return mem[address & 0xFFFF];
  }

  public void raiseInterrupt(int index) {
    if (index < 0 || index > 4) {
      Util.errn("MMU.raiseInterrupt - bad index " + index);
      return;
    }
    short flags = mem[IF];
    flags = (short)((flags | (1 << index)) & 0xFF);
    mem[IF] = flags;
  }

  // --------------------------------------------------------------------
  // I/O handling
  // --------------------------------------------------------------------
  private short readIO(int address) {
    switch (address) {
      case 0xFF00: return joypad.read();
      case 0xFF04: return timer != null ? timer.readDiv() : mem[address];
      case 0xFF05: return timer != null ? timer.readTima() : mem[address];
      case 0xFF06: return timer != null ? timer.readTma() : mem[address];
      case 0xFF07: return timer != null ? timer.readTac() : (short)(0xF8 | (mem[address] & 0x07));
      case 0xFF0F: return (short)(0xE0 | (mem[IF] & 0x1F));
      case 0xFF41: return ppu != null ? ppu.readStat() : (short)(0x80 | (mem[address] & 0x7F));
      case 0xFF44: return ppu != null ? ppu.readLY() : mem[address];
      default:
        break;
    }

    // APU
    if (address >= 0xFF10 && address <= 0xFF3F) {
      if (apu != null) return apu.read(address);
      return mem[address];
    }

    if (address == 0xFFFF) return mem[IE];
    return mem[address];
  }

  private void writeIO(int address, short value) {
    switch (address) {
      case 0xFF00:
        joypad.write(value);
        return;
      case 0xFF01:
        mem[address] = value;
        return;
      case 0xFF02:
        mem[address] = value;
        if ((value & 0x81) == 0x81) {
          short data = mem[0xFF01];
          if (serialListener != null) serialListener.onSerialWrite(data);
          if (printSerialToStdout) System.out.print((char)data);
          // Completed transfer; clear transfer-start bit and raise serial interrupt.
          mem[address] = (short)(value & 0x7F);
          raiseInterrupt(SERIAL);
        }
        return;
      case 0xFF04:
        if (timer != null) timer.writeDiv(value); else mem[address] = 0;
        return;
      case 0xFF05:
        if (timer != null) timer.writeTima(value); else mem[address] = value;
        return;
      case 0xFF06:
        if (timer != null) timer.writeTma(value); else mem[address] = value;
        return;
      case 0xFF07:
        if (timer != null) timer.writeTac(value); else mem[address] = value;
        return;
      case 0xFF0F:
        mem[IF] = (short)(value & 0x1F);
        return;
      case 0xFF41:
        if (ppu != null) ppu.writeStat(value); else mem[address] = value;
        return;
      case 0xFF44:
        // LY is read-only; writes are ignored on DMG.
        return;
      case 0xFF46:
        dma(value);
        mem[address] = value;
        return;
      default:
        break;
    }

    // APU registers
    if (address >= 0xFF10 && address <= 0xFF3F) {
      if (apu != null) apu.write(address, value); else mem[address] = value;
      return;
    }
    if (address == 0xFFFF) {
      mem[IE] = value;
      return;
    }
    mem[address] = value;
  }

  private void dma(short offset) {
    int src = (offset & 0xFF) << 8;
    for (int i = 0; i < 0xA0; i++) {
      mem[0xFE00 + i] = get(src + i);
    }
  }

  private void resetIoRegisters() {
    // DMG post-boot I/O state (see Pan Docs "Power Up Sequence")
    forceSet(0xFF00, (short)0xCF);
    forceSet(0xFF01, (short)0x00);
    forceSet(0xFF02, (short)0x7E);
    forceSet(0xFF04, (short)0xAB);
    forceSet(0xFF05, (short)0x00);
    forceSet(0xFF06, (short)0x00);
    forceSet(0xFF07, (short)0xF8);
    forceSet(0xFF0F, (short)0xE1);
    forceSet(0xFF10, (short)0x80);
    forceSet(0xFF11, (short)0xBF);
    forceSet(0xFF12, (short)0xF3);
    forceSet(0xFF14, (short)0xBF);
    forceSet(0xFF16, (short)0x3F);
    forceSet(0xFF17, (short)0x00);
    forceSet(0xFF19, (short)0xBF);
    forceSet(0xFF1A, (short)0x7F);
    forceSet(0xFF1B, (short)0xFF);
    forceSet(0xFF1C, (short)0x9F);
    forceSet(0xFF1E, (short)0xBF);
    forceSet(0xFF20, (short)0xFF);
    forceSet(0xFF21, (short)0x00);
    forceSet(0xFF22, (short)0x00);
    forceSet(0xFF23, (short)0xBF);
    forceSet(0xFF24, (short)0x77);
    forceSet(0xFF25, (short)0xF3);
    forceSet(0xFF26, (short)0xF1);
    forceSet(0xFF40, (short)0x91);
    forceSet(0xFF41, (short)0x85);
    forceSet(0xFF42, (short)0x00);
    forceSet(0xFF43, (short)0x00);
    forceSet(0xFF44, (short)0x00);
    forceSet(0xFF45, (short)0x00);
    forceSet(0xFF46, (short)0xFF);
    forceSet(0xFF47, (short)0xFC);
    forceSet(0xFF48, (short)0xFF);
    forceSet(0xFF49, (short)0xFF);
    forceSet(0xFF4A, (short)0x00);
    forceSet(0xFF4B, (short)0x00);
    forceSet(0xFFFF, (short)0x00);
  }

  public int lastAccessed() {
    int z = lastAccessed;
    lastAccessed = -1;
    return z;
  }
}
