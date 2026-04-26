
import java.io.Console;
import java.util.ArrayList;
import java.util.Collections;
import java.util.StringTokenizer;

/**
 * Top-level Game Boy. Wires the CPU, MMU, PPU, APU, joypad, cart, and
 * timer together and drives the emulation loop.
 *
 * <p>{@link #run()} runs the emulator in real time (with frame pacing
 * and audio), while {@link #runUntilSerialResult(long)} drives the
 * CPU as fast as possible for headless testing: it watches the serial
 * output for Blargg-style "Passed"/"Failed" markers and returns when
 * one is seen (or when the maxTicks budget runs out).
 */
class Gameboy {
  public static enum TestStatus { RUNNING, PASSED, FAILED, TIMEOUT }

  private static final int SERIAL_BUFFER_LIMIT = 4096;
  private static final double M_CYCLES_PER_FRAME = 17556.0; // ~59.73 Hz
  private static final double NS_PER_FRAME = 1_000_000_000.0 / 59.7275;

  private final CPUState state;
  private final ClockCounter clockCounter;
  private final CPU cpu;
  private final PPU ppu;
  private final MMU mmu;
  private final APU apu;
  private final TimerHandler timer;
  private final Cart cart;

  private final ArrayList<Integer> breakpoints = new ArrayList<>();
  private final ArrayList<Integer> watchpoints = new ArrayList<>();
  private boolean step;
  private long tickCounter;
  private final StringBuilder serialOutput = new StringBuilder();
  private TestStatus testStatus = TestStatus.RUNNING;

  public Gameboy(Cart cart) { this(cart, null, true, true); }

  public Gameboy(Cart cart, Screen screen, boolean printSerialToStdout) {
    this(cart, screen, printSerialToStdout, screen != null);
  }

  public Gameboy(Cart cart, Screen screen, boolean printSerialToStdout, boolean audioEnabled) {
    this.cart = cart;
    this.state = new CPUState();
    this.clockCounter = new ClockCounter();
    this.mmu = new MMU(cart, (short data) -> onSerialWriteByte(data), printSerialToStdout);
    this.ppu = new PPU(clockCounter, mmu, screen);
    this.apu = new APU(audioEnabled);
    this.timer = new TimerHandler(mmu, clockCounter);
    this.mmu.attach(timer, ppu, apu);
    this.cpu = new CPU(state, mmu, cart, clockCounter);
  }

  public MMU mmu() { return mmu; }
  public PPU ppu() { return ppu; }
  public APU apu() { return apu; }
  public Joypad joypad() { return mmu.joypad(); }

  /**
   * Run forever at ~real Game Boy speed, pacing each emulated frame to
   * a wall-clock 59.7 Hz.
   */
  public void run() {
    Util.debug = false;
    long frameStartNs = System.nanoTime();
    while (true) {
      int cyclesBefore = clockCounter.count();
      while (clockCounter.count() - cyclesBefore < M_CYCLES_PER_FRAME) {
        tick();
      }
      int elapsedM = clockCounter.count() - cyclesBefore;
      apu.tick(elapsedM);

      long now = System.nanoTime();
      long targetNs = frameStartNs + (long) NS_PER_FRAME;
      if (now < targetNs) {
        try { Thread.sleep((targetNs - now) / 1_000_000L, (int)((targetNs - now) % 1_000_000L)); }
        catch (InterruptedException ignored) {}
      }
      frameStartNs = Math.max(targetNs, System.nanoTime() - (long)(NS_PER_FRAME * 2));
    }
  }

  /**
   * Drives the emulator as fast as possible until {@code maxTicks}
   * expires or until a Blargg-style pass/fail marker is seen over
   * serial (or printed to the BG tile map, for tests like halt_bug
   * that only write to the LCD). Returns the final {@link TestStatus}.
   */
  public TestStatus runUntilSerialResult(long maxTicks) {
    Util.debug = false;
    tickCounter = 0;
    serialOutput.setLength(0);
    testStatus = TestStatus.RUNNING;

    // Some Blargg ROMs only write their result to the LCD, not serial,
    // so we periodically scan the BG tile map for pass/fail text too.
    final long VRAM_SCAN_INTERVAL = 200_000L;
    long nextScan = VRAM_SCAN_INTERVAL;

    while (tickCounter < maxTicks) {
      tick();
      if (testStatus == TestStatus.PASSED || testStatus == TestStatus.FAILED) {
        return testStatus;
      }
      if (tickCounter >= nextScan) {
        String screen = readBgTileMapAsText();
        String lower = screen.toLowerCase();
        if (lower.contains("failed")) {
          if (serialOutput.length() == 0) serialOutput.append(screen);
          testStatus = TestStatus.FAILED; return testStatus;
        }
        if (lower.contains("passed")) {
          if (serialOutput.length() == 0) serialOutput.append(screen);
          testStatus = TestStatus.PASSED; return testStatus;
        }
        nextScan = tickCounter + VRAM_SCAN_INTERVAL;
      }
    }

    testStatus = TestStatus.TIMEOUT;
    return testStatus;
  }

  /**
   * Reads the current BG tile map as ASCII. Blargg ROMs render text by
   * storing tile indices equal to ASCII codes in the tile map and
   * loading the matching glyphs into VRAM, so reading the map as chars
   * reconstructs the on-screen text.
   */
  private String readBgTileMapAsText() {
    int lcdc = mmu.forceGet(PPU.LCDC) & 0xFF;
    int mapBase = (lcdc & 0x08) != 0 ? 0x9C00 : 0x9800;
    StringBuilder sb = new StringBuilder();
    for (int row = 0; row < 18; row++) {
      for (int col = 0; col < 20; col++) {
        int tile = mmu.forceGet(mapBase + row * 32 + col) & 0xFF;
        char c = (tile >= 0x20 && tile < 0x7F) ? (char)tile : ' ';
        sb.append(c);
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  public String serialOutput() { return serialOutput.toString(); }
  public long ticks() { return tickCounter; }
  public TestStatus testStatus() { return testStatus; }

  private void tick() {
    if (breakpoints.contains(state.PC())) debugBreak();
    else if (step) { cpu.dump(); prompt(); }

    cpu.tick();
    tickCounter++;
  }

  private void onSerialWriteByte(short data) {
    char c = (char)(data & 0xFF);
    serialOutput.append(c);
    if (serialOutput.length() > SERIAL_BUFFER_LIMIT) {
      serialOutput.delete(0, serialOutput.length() - SERIAL_BUFFER_LIMIT);
    }
    String out = serialOutput.toString().toLowerCase();
    if (out.contains("failed")) testStatus = TestStatus.FAILED;
    else if (out.contains("passed")) testStatus = TestStatus.PASSED;
  }

  public void shutdown() {
    if (cart != null) cart.saveBatteryRam();
    if (apu != null) apu.stop();
  }

  // --- debugger -----------------------------------------------------
  private void debugBreak() {
    Util.log("##########################");
    cpu.dump();
    prompt();
    Util.log("##########################");
  }

  private void prompt() {
    Console c = System.console();
    if (c == null) return;
    boolean cnt = true;
    while (cnt) {
      c.format("> ");
      cnt = interpret(c.readLine(), c);
    }
  }

  private boolean interpret(String input, Console c) {
    if (input == null) return false;
    StringTokenizer st = new StringTokenizer(input, " ");
    if (!st.hasMoreTokens()) return false;

    String cmd = st.nextToken();
    switch (cmd) {
      case "dump":  cpu.dump(); return true;
      case "mem":   mem(st); return true;
      case "set":   setBreakpoint(st); return true;
      case "unset": unsetBreakpoint(st); return true;
      case "stack": stack(); return true;
      case "step":  step = true; return true;
      case "run":   step = false; return false;
      case "setmem":setmem(st); return true;
      case "watch": watch(st); return true;
      default: return false;
    }
  }

  private void mem(StringTokenizer st) {
    if (!st.hasMoreTokens()) return;
    int from;
    try { from = Integer.decode("0x" + st.nextToken()); }
    catch (Exception e) { Util.log("Usage: mem <16bit hex> <16bit hex>"); return; }
    int to = from;
    if (st.hasMoreTokens()) {
      try { to = Integer.decode("0x" + st.nextToken()); } catch (Exception e) { to = from; }
    }
    Util.log("Memory " + Util.hex(from) + " - " + Util.hex(to));
    for (int i = from; i <= to; i++) {
      Util.log(Util.hex(i) + "\t" + Util.hex(mmu.get(i)));
    }
  }

  private void setBreakpoint(StringTokenizer st) {
    if (!st.hasMoreTokens()) {
      Collections.sort(breakpoints);
      for (Integer bp : breakpoints) Util.log(Util.hex(bp));
      return;
    }
    try {
      int bp = Integer.decode("0x" + st.nextToken());
      if (!breakpoints.contains(bp)) breakpoints.add(bp);
    } catch (Exception e) { Util.log("Usage: set <16 bit hex>"); }
  }

  private void unsetBreakpoint(StringTokenizer st) {
    if (!st.hasMoreTokens()) { breakpoints.clear(); return; }
    try {
      int bp = Integer.decode("0x" + st.nextToken());
      breakpoints.remove(Integer.valueOf(bp));
    } catch (Exception e) { Util.log("Usage: unset <16 bit hex>"); }
  }

  private void stack() {
    Util.log("Stack");
    for (int i = 0xFFFE; i > state.SP(); i--) {
      Util.log(Util.hex(i) + "\t" + Util.hex(mmu.get(i)));
    }
  }

  private void setmem(StringTokenizer st) {
    if (st.countTokens() != 2) return;
    try {
      int address = Integer.decode("0x" + st.nextToken());
      short value = Short.decode("0x" + st.nextToken());
      mmu.forceSet(address, value);
      Util.log(Util.hex(address) + " = " + Util.hex(mmu.get(address)));
    } catch (Exception e) { Util.log("Usage: memset <a16> <d8>"); }
  }

  private void watch(StringTokenizer st) {
    if (!st.hasMoreTokens()) {
      Collections.sort(watchpoints);
      for (Integer wp : watchpoints) Util.log(Util.hex(wp));
      return;
    }
    try {
      int address = Integer.decode("0x" + st.nextToken());
      if (!watchpoints.contains(address)) watchpoints.add(address);
    } catch (Exception e) { Util.log("Usage: watch <a16>"); }
  }
}
