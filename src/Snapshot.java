
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;

/**
 * Headless ROM runner that produces PNG-style PGM screen captures at
 * specified tick offsets, optionally injecting joypad input. Intended for
 * automated visual smoke testing of small ROMs.
 *
 * Usage:
 *   java -cp src/ Snapshot <rom> <out.pgm> [ticks=2000000]
 *   java -cp src/ Snapshot <rom> <out_prefix> --series t1,t2,... [--input frame:btn,frame:btn,...]
 *
 * Buttons: U=Up, D=Down, L=Left, R=Right, A, B, S=Start, T=Select.
 */
class Snapshot implements Screen {
  private static final int W = PPU.SCALED_SCREEN_WIDTH;
  private static final int H = PPU.SCALED_SCREEN_HEIGHT;
  private static final int OUT_W = PPU.SCREEN_WIDTH;
  private static final int OUT_H = PPU.SCREEN_HEIGHT;
  private final short[] buf = new short[W * H];

  public void setPixel(int x, int y, short color) {
    if (x < 0 || y < 0 || x >= W || y >= H) return;
    buf[y * W + x] = color;
  }

  public void draw() {}

  public void writePgm(String path) throws Exception {
    // Downsample the 3x scaled framebuffer to native 160x144 by sampling the
    // top-left of each 3x3 cell.
    try (FileOutputStream fos = new FileOutputStream(path)) {
      String header = "P5\n" + OUT_W + " " + OUT_H + "\n255\n";
      fos.write(header.getBytes());
      byte[] row = new byte[OUT_W * OUT_H];
      int scale = PPU.SCALE;
      for (int y = 0; y < OUT_H; y++) {
        for (int x = 0; x < OUT_W; x++) {
          row[y * OUT_W + x] = (byte) (buf[(y * scale) * W + (x * scale)] & 0xFF);
        }
      }
      fos.write(row);
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: Snapshot <rom> <out.pgm> [ticks]");
      System.exit(2);
    }
    String romPath = args[0];
    String outPath = args[1];
    long ticks = 2_000_000L;
    long[] series = null;
    String inputSpec = null;
    for (int i = 2; i < args.length; i++) {
      if (args[i].equals("--series") && i + 1 < args.length) {
        String[] parts = args[++i].split(",");
        series = new long[parts.length];
        for (int j = 0; j < parts.length; j++) series[j] = Long.parseLong(parts[j]);
      } else if (args[i].equals("--input") && i + 1 < args.length) {
        inputSpec = args[++i];
      } else {
        try { ticks = Long.parseLong(args[i]); } catch (Exception ignored) {}
      }
    }

    Cart cart = new Cart(romPath);
    Snapshot scr = new Snapshot();
    Gameboy gb = new Gameboy(cart, scr, false, false);
    gb.ppu().setScreen(scr);

    // Pre-program input schedule
    HashSet<Long> pressTimes = new HashSet<>();
    java.util.TreeMap<Long, Joypad.Button> press = new java.util.TreeMap<>();
    java.util.TreeMap<Long, Joypad.Button> release = new java.util.TreeMap<>();
    if (inputSpec != null) {
      for (String tok : inputSpec.split(",")) {
        String[] kv = tok.split(":");
        if (kv.length != 2) continue;
        long t = Long.parseLong(kv[0]);
        Joypad.Button btn = parseButton(kv[1]);
        if (btn == null) continue;
        press.put(t, btn);
        release.put(t + 60_000L, btn); // hold ~60K ticks then release
      }
    }

    if (series == null) series = new long[]{ticks};

    long currentTick = 0;
    int seriesIdx = 0;
    int totalCalls = 0;
    int batch = 1000; // run in chunks of cycles via runUntilSerialResult won't work
    // Reflect into the existing Gameboy.runUntilSerialResult? Not accessible.
    // Use a direct ticking loop similar to runUntilSerialResult.

    // We can't easily reach private tick(); but Gameboy exposes runUntilSerialResult.
    // The simplest is to run with maxTicks = series[seriesIdx], then capture.

    long lastTickRun = 0;
    long maxOverall = series[series.length - 1];

    // Apply inputs by maintaining a thread that polls? simpler: chunk-run.
    long chunk = 50_000L;
    Joypad joy = gb.joypad();
    while (currentTick < maxOverall) {
      // apply press/release events whose time has passed
      for (java.util.Map.Entry<Long, Joypad.Button> e : new java.util.ArrayList<>(press.entrySet())) {
        if (e.getKey() <= currentTick) {
          joy.set(e.getValue(), true);
          press.remove(e.getKey());
        }
      }
      for (java.util.Map.Entry<Long, Joypad.Button> e : new java.util.ArrayList<>(release.entrySet())) {
        if (e.getKey() <= currentTick) {
          joy.set(e.getValue(), false);
          release.remove(e.getKey());
        }
      }
      long target = Math.min(currentTick + chunk, maxOverall);
      gb.runUntilSerialResult(target - currentTick);
      currentTick = target;

      if (seriesIdx < series.length && currentTick >= series[seriesIdx]) {
        String path = (series.length == 1) ? outPath
            : String.format("%s.%d.pgm", outPath, seriesIdx);
        scr.writePgm(path);
        System.out.println("Wrote " + path + " at tick " + currentTick);
        seriesIdx++;
      }
    }
    while (seriesIdx < series.length) {
      String path = (series.length == 1) ? outPath
          : String.format("%s.%d.pgm", outPath, seriesIdx);
      scr.writePgm(path);
      seriesIdx++;
    }

    // Final VRAM tilemap dump (rows 0..17, cols 0..19) as a sibling .map file.
    int lcdc = gb.mmu().forceGet(0xFF40) & 0xFF;
    int scx = gb.mmu().forceGet(0xFF43) & 0xFF;
    int scy = gb.mmu().forceGet(0xFF42) & 0xFF;
    int bgBase = (lcdc & 0x08) != 0 ? 0x9C00 : 0x9800;
    StringBuilder bg = new StringBuilder();
    bg.append(String.format("LCDC=%02X SCX=%02X SCY=%02X bgBase=%04X%n",
        lcdc, scx, scy, bgBase));
    for (int y = 0; y < 18; y++) {
      bg.append(String.format("y=%2d:", y));
      for (int x = 0; x < 20; x++) {
        bg.append(String.format(" %02x", gb.mmu().forceGet(bgBase + y * 32 + x) & 0xFF));
      }
      bg.append('\n');
    }
    java.nio.file.Files.writeString(java.nio.file.Paths.get(outPath + ".map"), bg.toString());

    // Dump tile data $8000..$80FF to a hex file (16 tiles).
    StringBuilder td = new StringBuilder();
    for (int t = 0; t < 16; t++) {
      td.append(String.format("tile %02x:", t));
      for (int b = 0; b < 16; b++) {
        td.append(String.format(" %02x", gb.mmu().forceGet(0x8000 + t * 16 + b) & 0xFF));
      }
      td.append('\n');
    }
    java.nio.file.Files.writeString(java.nio.file.Paths.get(outPath + ".tiles"), td.toString());
  }

  private static Joypad.Button parseButton(String s) {
    s = s.toUpperCase();
    if (s.equals("U") || s.equals("UP")) return Joypad.Button.UP;
    if (s.equals("D") || s.equals("DOWN")) return Joypad.Button.DOWN;
    if (s.equals("L") || s.equals("LEFT")) return Joypad.Button.LEFT;
    if (s.equals("R") || s.equals("RIGHT")) return Joypad.Button.RIGHT;
    if (s.equals("A")) return Joypad.Button.A;
    if (s.equals("B")) return Joypad.Button.B;
    if (s.equals("S") || s.equals("START")) return Joypad.Button.START;
    if (s.equals("T") || s.equals("SELECT")) return Joypad.Button.SELECT;
    return null;
  }
}
