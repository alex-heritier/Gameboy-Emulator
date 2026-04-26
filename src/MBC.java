
/**
 * Memory Bank Controller for Gameboy cartridges.
 *
 * A cartridge may contain more ROM than fits in the 32 KiB CPU address
 * window (0x0000 - 0x7FFF) and may provide battery-backed external RAM
 * mapped to 0xA000 - 0xBFFF.  The MBC on the cart handles mapping
 * between those logical addresses and its physical ROM/RAM, and
 * intercepts writes in specific ROM-address windows as control
 * registers.
 *
 * This implementation supports:
 *   - No MBC  (cartridge types 0x00, 0x08, 0x09)
 *   - MBC1    (0x01, 0x02, 0x03)
 *   - MBC2    (0x05, 0x06)
 *   - MBC3    (0x0F .. 0x13)
 *   - MBC5    (0x19 .. 0x1E)
 *
 * Each subclass tracks its own bank-switching state. Writes to
 * 0x0000-0x7FFF are routed to {@link #writeRom}, and reads/writes to
 * external RAM (0xA000-0xBFFF) go through {@link #readRam}/{@link #writeRam}.
 */
abstract class MBC {
  protected final short[] rom;
  protected final short[] ram;
  protected final int romBankCount;
  protected final int ramBankCount;

  protected MBC(short[] rom, int ramSize) {
    this.rom = rom;
    this.ram = new short[Math.max(ramSize, 1)];
    this.romBankCount = Math.max(rom.length / 0x4000, 1);
    // 8 KiB per bank (except MBC2 which uses 512 nibbles - handled specially)
    this.ramBankCount = ramSize / 0x2000;
  }

  public abstract short readRom(int address);
  public abstract void writeRom(int address, short value);
  public abstract short readRam(int address);
  public abstract void writeRam(int address, short value);

  public short[] ram() { return ram; }
  public void loadRam(short[] data) {
    if (data == null) return;
    int n = Math.min(data.length, ram.length);
    System.arraycopy(data, 0, ram, 0, n);
  }

  /** Returns an MBC instance appropriate for the loaded cartridge. */
  public static MBC forCart(short[] rom) {
    if (rom.length < 0x150) {
      Util.errn("Cart too small (" + rom.length + " bytes)");
      return new NoMBC(rom, 0);
    }

    int cartType = rom[0x147] & 0xFF;
    int ramSize  = ramSizeForCode(rom[0x149] & 0xFF);

    switch (cartType) {
      case 0x00: case 0x08: case 0x09:
        return new NoMBC(rom, ramSize);
      case 0x01: case 0x02: case 0x03:
        return new MBC1(rom, ramSize);
      case 0x05: case 0x06:
        return new MBC2(rom);
      case 0x0F: case 0x10: case 0x11: case 0x12: case 0x13:
        return new MBC3(rom, ramSize, cartType == 0x0F || cartType == 0x10);
      case 0x19: case 0x1A: case 0x1B: case 0x1C: case 0x1D: case 0x1E:
        return new MBC5(rom, ramSize);
      default:
        Util.errn("Unknown cart type 0x" + Util.hex((short)cartType) + " - falling back to MBC1");
        return new MBC1(rom, ramSize);
    }
  }

  public static int ramSizeForCode(int code) {
    switch (code) {
      case 0x00: return 0;
      case 0x01: return 0x0800;   // 2 KiB
      case 0x02: return 0x2000;   // 8 KiB
      case 0x03: return 0x8000;   // 32 KiB (4 banks)
      case 0x04: return 0x20000;  // 128 KiB (16 banks)
      case 0x05: return 0x10000;  // 64 KiB (8 banks)
      default:   return 0x2000;
    }
  }

  protected final short romByte(int bank, int offset) {
    int address = bank * 0x4000 + (offset & 0x3FFF);
    if (address < 0 || address >= rom.length) return (short)0xFF;
    return rom[address];
  }

  // --- NoMBC ------------------------------------------------------------
  static class NoMBC extends MBC {
    private boolean ramEnabled;

    NoMBC(short[] rom, int ramSize) { super(rom, ramSize); ramEnabled = ramSize > 0; }

    public short readRom(int address) {
      if (address < 0 || address >= rom.length) return (short)0xFF;
      return rom[address];
    }
    public void writeRom(int address, short value) { /* read-only */ }
    public short readRam(int address) {
      int offset = address - 0xA000;
      if (!ramEnabled || offset < 0 || offset >= ram.length) return (short)0xFF;
      return ram[offset];
    }
    public void writeRam(int address, short value) {
      int offset = address - 0xA000;
      if (!ramEnabled || offset < 0 || offset >= ram.length) return;
      ram[offset] = (short)(value & 0xFF);
    }
  }

  // --- MBC1 -------------------------------------------------------------
  static class MBC1 extends MBC {
    private boolean ramEnabled = false;
    private int romBank = 1;      // 5-bit low bits
    private int ramBank = 0;      // 2-bit upper bits
    private boolean advancedMode = false;

    MBC1(short[] rom, int ramSize) { super(rom, ramSize); }

    private int effectiveLowerBank() {
      // In MBC1, 0x0000-0x3FFF is fixed, except in advanced banking mode
      // where bits 5-6 of the RAM-bank register affect it on large ROMs.
      if (advancedMode) return (ramBank << 5) & (romBankCount - 1);
      return 0;
    }

    private int effectiveUpperBank() {
      int bank = ((ramBank & 0x03) << 5) | (romBank & 0x1F);
      // Banks 0x00, 0x20, 0x40, 0x60 map to +1 on real hardware.
      if ((bank & 0x1F) == 0) bank |= 1;
      if (romBankCount > 0) bank &= (romBankCount - 1);
      return bank;
    }

    public short readRom(int address) {
      if (address < 0x4000) return romByte(effectiveLowerBank(), address);
      return romByte(effectiveUpperBank(), address);
    }

    public void writeRom(int address, short value) {
      value = (short)(value & 0xFF);
      if (address < 0x2000) {
        ramEnabled = (value & 0x0F) == 0x0A;
      } else if (address < 0x4000) {
        int b = value & 0x1F;
        romBank = b == 0 ? 1 : b;
      } else if (address < 0x6000) {
        ramBank = value & 0x03;
      } else {
        advancedMode = (value & 0x01) != 0;
      }
    }

    public short readRam(int address) {
      if (!ramEnabled || ram.length == 0) return (short)0xFF;
      int bank = advancedMode ? (ramBank & 0x03) : 0;
      int offset = bank * 0x2000 + (address - 0xA000);
      if (offset < 0 || offset >= ram.length) return (short)0xFF;
      return ram[offset];
    }

    public void writeRam(int address, short value) {
      if (!ramEnabled || ram.length == 0) return;
      int bank = advancedMode ? (ramBank & 0x03) : 0;
      int offset = bank * 0x2000 + (address - 0xA000);
      if (offset < 0 || offset >= ram.length) return;
      ram[offset] = (short)(value & 0xFF);
    }
  }

  // --- MBC2 -------------------------------------------------------------
  static class MBC2 extends MBC {
    private boolean ramEnabled = false;
    private int romBank = 1;

    MBC2(short[] rom) { super(rom, 512); }

    public short readRom(int address) {
      if (address < 0x4000) return romByte(0, address);
      int bank = romBank & 0x0F;
      if (bank == 0) bank = 1;
      return romByte(bank & (romBankCount - 1), address);
    }

    public void writeRom(int address, short value) {
      value = (short)(value & 0xFF);
      if (address < 0x4000) {
        // Bit 8 of the address selects which register we're writing to.
        if ((address & 0x0100) == 0) {
          ramEnabled = (value & 0x0F) == 0x0A;
        } else {
          int b = value & 0x0F;
          romBank = b == 0 ? 1 : b;
        }
      }
    }

    public short readRam(int address) {
      if (!ramEnabled) return (short)0xFF;
      int offset = (address - 0xA000) & 0x01FF;
      return (short)(0xF0 | (ram[offset] & 0x0F));
    }

    public void writeRam(int address, short value) {
      if (!ramEnabled) return;
      int offset = (address - 0xA000) & 0x01FF;
      ram[offset] = (short)(value & 0x0F);
    }
  }

  // --- MBC3 -------------------------------------------------------------
  static class MBC3 extends MBC {
    private boolean ramAndRtcEnabled = false;
    private int romBank = 1;
    private int ramBankOrRtc = 0;
    private final boolean hasRTC;
    private final short[] rtc = new short[5]; // seconds, minutes, hours, DL, DH
    private boolean rtcLatched = false;
    private short lastLatchWrite = (short)0xFF;

    MBC3(short[] rom, int ramSize, boolean hasRTC) {
      super(rom, ramSize);
      this.hasRTC = hasRTC;
    }

    public short readRom(int address) {
      if (address < 0x4000) return romByte(0, address);
      int bank = romBank;
      if (bank == 0) bank = 1;
      if (romBankCount > 0) bank &= (romBankCount - 1);
      return romByte(bank, address);
    }

    public void writeRom(int address, short value) {
      value = (short)(value & 0xFF);
      if (address < 0x2000) {
        ramAndRtcEnabled = (value & 0x0F) == 0x0A;
      } else if (address < 0x4000) {
        romBank = value & 0x7F;
        if (romBank == 0) romBank = 1;
      } else if (address < 0x6000) {
        ramBankOrRtc = value & 0x0F;
      } else {
        // 0x00 then 0x01 latches the RTC registers.
        if (lastLatchWrite == 0x00 && value == 0x01) rtcLatched = !rtcLatched;
        lastLatchWrite = value;
      }
    }

    public short readRam(int address) {
      if (!ramAndRtcEnabled) return (short)0xFF;
      if (hasRTC && ramBankOrRtc >= 0x08 && ramBankOrRtc <= 0x0C) {
        return rtc[ramBankOrRtc - 0x08];
      }
      if (ram.length == 0) return (short)0xFF;
      int bank = ramBankOrRtc & 0x03;
      int offset = bank * 0x2000 + (address - 0xA000);
      if (offset < 0 || offset >= ram.length) return (short)0xFF;
      return ram[offset];
    }

    public void writeRam(int address, short value) {
      if (!ramAndRtcEnabled) return;
      if (hasRTC && ramBankOrRtc >= 0x08 && ramBankOrRtc <= 0x0C) {
        rtc[ramBankOrRtc - 0x08] = (short)(value & 0xFF);
        return;
      }
      if (ram.length == 0) return;
      int bank = ramBankOrRtc & 0x03;
      int offset = bank * 0x2000 + (address - 0xA000);
      if (offset < 0 || offset >= ram.length) return;
      ram[offset] = (short)(value & 0xFF);
    }
  }

  // --- MBC5 -------------------------------------------------------------
  static class MBC5 extends MBC {
    private boolean ramEnabled = false;
    private int romBank = 1; // 9-bit
    private int ramBank = 0; // 4-bit

    MBC5(short[] rom, int ramSize) { super(rom, ramSize); }

    public short readRom(int address) {
      if (address < 0x4000) return romByte(0, address);
      int bank = romBankCount > 0 ? (romBank & (romBankCount - 1)) : 0;
      return romByte(bank, address);
    }

    public void writeRom(int address, short value) {
      value = (short)(value & 0xFF);
      if (address < 0x2000) {
        ramEnabled = (value & 0x0F) == 0x0A;
      } else if (address < 0x3000) {
        romBank = (romBank & 0x100) | (value & 0xFF);
      } else if (address < 0x4000) {
        romBank = (romBank & 0x0FF) | ((value & 0x01) << 8);
      } else if (address < 0x6000) {
        ramBank = value & 0x0F;
      }
    }

    public short readRam(int address) {
      if (!ramEnabled || ram.length == 0) return (short)0xFF;
      int bank = ramBank & 0x0F;
      int offset = bank * 0x2000 + (address - 0xA000);
      if (offset < 0 || offset >= ram.length) return (short)0xFF;
      return ram[offset];
    }

    public void writeRam(int address, short value) {
      if (!ramEnabled || ram.length == 0) return;
      int bank = ramBank & 0x0F;
      int offset = bank * 0x2000 + (address - 0xA000);
      if (offset < 0 || offset >= ram.length) return;
      ram[offset] = (short)(value & 0xFF);
    }
  }
}
