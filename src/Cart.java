
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A loaded Gameboy cartridge.
 *
 * The full ROM image is kept in memory and wrapped by an {@link MBC}
 * instance that handles bank switching. Battery-backed saves are stored
 * alongside the cart file with a {@code .sav} suffix.
 */
class Cart {
  private final short[] rom;
  private final MBC mbc;
  private final String path;
  private final boolean hasBattery;
  private final String title;

  public Cart(String filename) {
    this.path = filename;
    this.rom = readFile(filename);
    this.mbc = MBC.forCart(rom);
    int cartType = rom.length > 0x147 ? (rom[0x147] & 0xFF) : 0;
    this.hasBattery = isBatteryCart(cartType);
    this.title = readTitle();

    loadBatteryRam();
    Util.log("Loaded " + title + " (" + rom.length + " bytes, " +
             "type 0x" + Util.hex((short)cartType) + ")");
  }

  private static short[] readFile(String filename) {
    File file = new File(filename);
    short[] data = new short[(int)file.length()];
    try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
      byte[] tmp = new byte[(int)file.length()];
      in.readFully(tmp);
      for (int i = 0; i < tmp.length; i++) data[i] = (short)(tmp[i] & 0xFF);
    } catch (Exception e) {
      Util.errn("Failed to read " + filename + ": " + e.getMessage());
    }
    return data;
  }

  public int size() { return rom.length; }
  public MBC mbc() { return mbc; }
  public String title() { return title; }
  public String path() { return path; }

  public short readRom(int address) { return mbc.readRom(address); }
  public void writeRom(int address, short value) { mbc.writeRom(address, value); }
  public short readRam(int address) { return mbc.readRam(address); }
  public void writeRam(int address, short value) { mbc.writeRam(address, value); }

  public short headerByte(int offset) {
    if (offset < 0 || offset >= rom.length) return 0;
    return rom[offset];
  }

  private String readTitle() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0x134; i < 0x144 && i < rom.length; i++) {
      short b = rom[i];
      if (b == 0) break;
      if (b >= 0x20 && b < 0x7F) sb.append((char)b);
    }
    String name = sb.toString().trim();
    if (name.isEmpty()) {
      File f = new File(path);
      String raw = f.getName();
      int dot = raw.lastIndexOf('.');
      name = dot > 0 ? raw.substring(0, dot) : raw;
    }
    return name;
  }

  private static boolean isBatteryCart(int type) {
    switch (type) {
      case 0x03: case 0x06: case 0x09:
      case 0x0D: case 0x0F: case 0x10:
      case 0x13: case 0x1B: case 0x1E:
      case 0x22: case 0xFF:
        return true;
      default:
        return false;
    }
  }

  public void saveBatteryRam() {
    if (!hasBattery) return;
    short[] ram = mbc.ram();
    if (ram == null || ram.length == 0) return;
    try (FileOutputStream fos = new FileOutputStream(savePath())) {
      byte[] buffer = new byte[ram.length];
      for (int i = 0; i < ram.length; i++) buffer[i] = (byte)(ram[i] & 0xFF);
      fos.write(buffer);
    } catch (IOException e) {
      Util.errn("Cart.saveBatteryRam failed: " + e.getMessage());
    }
  }

  private void loadBatteryRam() {
    if (!hasBattery) return;
    File saveFile = new File(savePath());
    if (!saveFile.exists()) return;
    try (DataInputStream in = new DataInputStream(new FileInputStream(saveFile))) {
      byte[] tmp = new byte[(int)saveFile.length()];
      in.readFully(tmp);
      short[] data = new short[tmp.length];
      for (int i = 0; i < tmp.length; i++) data[i] = (short)(tmp[i] & 0xFF);
      mbc.loadRam(data);
      Util.log("Restored save file " + saveFile.getName());
    } catch (IOException e) {
      Util.errn("Cart.loadBatteryRam failed: " + e.getMessage());
    }
  }

  private String savePath() {
    int dot = path.lastIndexOf('.');
    String base = dot < 0 ? path : path.substring(0, dot);
    return base + ".sav";
  }
}
