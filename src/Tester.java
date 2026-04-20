import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

class Tester {
  private static final String DEFAULT_ROM = "roms/dr_mario.gb";
  private static final String DEFAULT_TEST_ROM_DIR = "roms/tests";
  private static final long DEFAULT_MAX_TICKS = 30000000L;
  private static final int EXIT_NO_ROMS = 2;
  private static final int SERIAL_SUMMARY_LIMIT = 120;

  public static void main(String args[]) {
    if (args.length > 0 && args[0].equals("--test")) {
      int exitCode = runTests(args);
      System.exit(exitCode);
      return;
    }

    String romPath = args.length > 0 ? args[0] : DEFAULT_ROM;
    Util.log("Gameboy Emulator");
    Util.log("################");
    Cart cart = new Cart(romPath);
    Gameboy gb = new Gameboy(cart);
    gb.run();
  }

  private static int runTests(String[] args) {
    String romDir = args.length > 1 ? args[1] : testRomDir();
    long maxTicks = args.length > 2 ? parseMaxTicks(args[2]) : parseMaxTicks(System.getenv("GB_TEST_MAX_TICKS"));
    ArrayList<File> roms = findRoms(romDir);

    Util.log("GB CLI test suite");
    Util.log("#################");
    Util.log("ROM dir: " + romDir);
    Util.log("Max ticks/ROM: " + maxTicks);
    Util.log();

    if (roms.isEmpty()) {
      Util.errn("No .gb ROM files found in " + romDir);
      Util.errn("Set GB_TEST_ROM_DIR or pass path: java -cp src/ Tester --test <rom_dir>");
      return EXIT_NO_ROMS;
    }

    int passed = 0;
    int failed = 0;
    int timedOut = 0;

    for (int i = 0; i < roms.size(); i++) {
      File rom = roms.get(i);
      Gameboy gb = new Gameboy(new Cart(rom.getPath()), new HeadlessScreen(), false);
      Gameboy.TestStatus status = gb.runUntilSerialResult(maxTicks);
      String serialSummary = summarizeSerial(gb.serialOutput());

      if (status == Gameboy.TestStatus.PASSED) passed++;
      else if (status == Gameboy.TestStatus.FAILED) failed++;
      else timedOut++;

      Util.log("[" + statusLabel(status) + "] " + rom.getPath() + " (" + gb.ticks() + " ticks)");
      if (!serialSummary.isEmpty())
        Util.log("  serial: " + serialSummary);
    }

    Util.log();
    Util.log("Summary: " + passed + " passed, " + failed + " failed, " + timedOut + " timeout");
    if (failed == 0 && timedOut == 0) return 0;
    return 1;
  }

  private static String testRomDir() {
    String env = System.getenv("GB_TEST_ROM_DIR");
    if (env == null || env.trim().isEmpty()) return DEFAULT_TEST_ROM_DIR;
    return env;
  }

  private static long parseMaxTicks(String value) {
    if (value == null || value.trim().isEmpty()) return DEFAULT_MAX_TICKS;
    try {
      return Long.parseLong(value);
    } catch (Exception e) {
      Util.errn("Invalid max tick value: " + value + ". Using " + DEFAULT_MAX_TICKS);
      return DEFAULT_MAX_TICKS;
    }
  }

  private static ArrayList<File> findRoms(String romDir) {
    ArrayList<File> roms = new ArrayList<File>();
    File root = new File(romDir);
    collectRoms(root, roms);
    Collections.sort(roms);
    return roms;
  }

  private static void collectRoms(File node, ArrayList<File> roms) {
    if (node == null || !node.exists()) return;
    if (node.isDirectory()) {
      File[] children = node.listFiles();
      if (children == null) return;
      for (int i = 0; i < children.length; i++) {
        collectRoms(children[i], roms);
      }
      return;
    }

    String name = node.getName().toLowerCase();
    if (name.endsWith(".gb")) roms.add(node);
  }

  private static String summarizeSerial(String serialOutput) {
    if (serialOutput == null) return "";
    String z = serialOutput.replace('\n', ' ');
    z = z.replace('\r', ' ');
    z = z.trim();
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
