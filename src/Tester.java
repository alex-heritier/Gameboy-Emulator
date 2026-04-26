
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Entry point. Without arguments, launches the emulator with
 * {@code roms/dr_mario.gb} loaded in an interactive window. With
 * {@code --test}, runs every {@code .gb} file under
 * {@code roms/tests/} headless-ly and prints a pass/fail summary based
 * on serial output (Blargg convention).
 *
 * <p>CLI usage:
 *   <pre>
 *     java -cp src/ Tester                       # run Dr. Mario
 *     java -cp src/ Tester path/to/game.gb       # run a specific rom
 *     java -cp src/ Tester --test                # run roms/tests/**.gb
 *     java -cp src/ Tester --test path/to/dir    # run a different dir
 *     java -cp src/ Tester --test dir 30000000   # change tick budget
 *   </pre>
 */
class Tester {
  private static final String DEFAULT_ROM = "roms/pokemon_blue.gb";
  private static final String DEFAULT_TEST_ROM_DIR = "roms/tests";
  private static final long DEFAULT_MAX_TICKS = 50_000_000L;
  private static final int EXIT_NO_ROMS = 2;
  private static final int SERIAL_SUMMARY_LIMIT = 160;

  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("--test")) {
      int exitCode = runTests(args);
      System.exit(exitCode);
      return;
    }

    String romPath = args.length > 0 ? args[0] : DEFAULT_ROM;
    Util.log("Game Boy Emulator");
    Util.log("#################");
    Cart cart = new Cart(romPath);
    final Gameboy gb = new Gameboy(cart, null, false, true);
    BasicScreen screen = new BasicScreen(gb.joypad(), gb.apu(), cart.title());
    gb.ppu().setScreen(screen);

    Runtime.getRuntime().addShutdownHook(new Thread(gb::shutdown));
    gb.run();
  }

  private static int runTests(String[] args) {
    String romDir = args.length > 1 ? args[1] : testRomDir();
    long maxTicks = args.length > 2
        ? parseMaxTicks(args[2])
        : parseMaxTicks(System.getenv("GB_TEST_MAX_TICKS"));
    ArrayList<File> roms = findRoms(romDir);

    Util.log("Game Boy test suite");
    Util.log("###################");
    Util.log("ROM dir: " + romDir);
    Util.log("Max ticks/ROM: " + maxTicks);
    Util.log();

    if (roms.isEmpty()) {
      Util.errn("No .gb ROM files found in " + romDir);
      Util.errn("Tip: pass an explicit dir: java -cp src/ Tester --test <dir>");
      return EXIT_NO_ROMS;
    }

    int passed = 0, failed = 0, timedOut = 0;
    long startNs = System.nanoTime();

    for (File rom : roms) {
      Gameboy gb = new Gameboy(new Cart(rom.getPath()), new HeadlessScreen(), false, false);
      long runStart = System.nanoTime();
      Gameboy.TestStatus status = gb.runUntilSerialResult(maxTicks);
      double runMs = (System.nanoTime() - runStart) / 1e6;
      String summary = summarizeSerial(gb.serialOutput());

      if      (status == Gameboy.TestStatus.PASSED) passed++;
      else if (status == Gameboy.TestStatus.FAILED) failed++;
      else                                          timedOut++;

      Util.log(String.format("[%s] %-55s %9d ticks %8.1f ms",
          statusLabel(status), rom.getPath(), gb.ticks(), runMs));
      if (!summary.isEmpty()) Util.log("         " + summary);
    }

    double totalSec = (System.nanoTime() - startNs) / 1e9;
    Util.log();
    Util.log(String.format(
        "Summary: %d passed, %d failed, %d timed out in %.2fs",
        passed, failed, timedOut, totalSec));
    return (failed == 0 && timedOut == 0) ? 0 : 1;
  }

  private static String testRomDir() {
    String env = System.getenv("GB_TEST_ROM_DIR");
    if (env == null || env.trim().isEmpty()) return DEFAULT_TEST_ROM_DIR;
    return env;
  }

  private static long parseMaxTicks(String value) {
    if (value == null || value.trim().isEmpty()) return DEFAULT_MAX_TICKS;
    try { return Long.parseLong(value.trim()); }
    catch (Exception e) {
      Util.errn("Invalid max tick value: " + value + ". Using " + DEFAULT_MAX_TICKS);
      return DEFAULT_MAX_TICKS;
    }
  }

  private static ArrayList<File> findRoms(String romDir) {
    ArrayList<File> roms = new ArrayList<>();
    collectRoms(new File(romDir), roms);
    Collections.sort(roms);
    return roms;
  }

  private static void collectRoms(File node, ArrayList<File> roms) {
    if (node == null || !node.exists()) return;
    if (node.isDirectory()) {
      File[] kids = node.listFiles();
      if (kids == null) return;
      for (File k : kids) collectRoms(k, roms);
      return;
    }
    if (node.getName().toLowerCase().endsWith(".gb")) roms.add(node);
  }

  private static String summarizeSerial(String serial) {
    if (serial == null) return "";
    String z = serial.replace('\n', ' ').replace('\r', ' ').trim();
    if (z.length() > SERIAL_SUMMARY_LIMIT) {
      z = z.substring(0, SERIAL_SUMMARY_LIMIT - 3) + "...";
    }
    return z;
  }

  private static String statusLabel(Gameboy.TestStatus status) {
    if (status == Gameboy.TestStatus.PASSED) return "PASS";
    if (status == Gameboy.TestStatus.FAILED) return "FAIL";
    if (status == Gameboy.TestStatus.TIMEOUT) return "TIME";
    return "????";
  }
}
