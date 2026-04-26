
/**
 * Picture processing unit.
 *
 * <p>Drives the LCD state machine (OAM scan -> pixel transfer -> HBlank
 * -> VBlank), raises VBlank / STAT interrupts, and paints each visible
 * scanline into the attached {@link Screen}.
 *
 * <p>Timing is in M-cycles (1 M == 4 dots):
 *   Mode 2 (OAM scan)      20
 *   Mode 3 (pixel xfer)    43
 *   Mode 0 (HBlank)        51
 *   Total per scanline     114
 *   Mode 1 (VBlank)        10 scanlines of 114
 *
 * <p>Exposes {@link #readLY()}, {@link #readStat()}, {@link #writeStat(short)}
 * which are called by the MMU so that disabling the LCD resets the PPU
 * in a deterministic way and writes to STAT cannot clobber read-only
 * bits.
 */
class PPU {

  public static final int SCREEN_WIDTH = 160;
  public static final int SCREEN_HEIGHT = 144;
  public static final int SCALE = 3;
  public static final int SCALED_SCREEN_WIDTH = SCREEN_WIDTH * SCALE;
  public static final int SCALED_SCREEN_HEIGHT = SCREEN_HEIGHT * SCALE;

  public static final int OAM = 0xFE00;
  public static final int LCDC = 0xFF40;
  public static final int STAT = 0xFF41;
  public static final int SCY = 0xFF42;
  public static final int SCX = 0xFF43;
  public static final int LY = 0xFF44;
  public static final int LYC = 0xFF45;
  public static final int BGP = 0xFF47;
  public static final int OBP0 = 0xFF48;
  public static final int OBP1 = 0xFF49;
  public static final int WY = 0xFF4A;
  public static final int WX = 0xFF4B;

  public static final int COLOR_BLACK     = 0x00;
  public static final int COLOR_DARK_GREY = 0x55;
  public static final int COLOR_LIGHT_GREY= 0xAA;
  public static final int COLOR_WHITE     = 0xFF;
  public static final int TRANSPARENT     = -1;

  // Mode lengths expressed in M-cycles.
  private static final int MODE_2_CYCLES = 20;
  private static final int MODE_3_CYCLES = 43;
  private static final int MODE_0_CYCLES = 51;
  private static final int SCANLINE_CYCLES = MODE_2_CYCLES + MODE_3_CYCLES + MODE_0_CYCLES; // 114
  private static final int VBLANK_LINES = 10;
  private static final int TOTAL_LINES = 154;

  public static final int MODE_0 = 0;
  public static final int MODE_1 = 1;
  public static final int MODE_2 = 2;
  public static final int MODE_3 = 3;

  private final MMU mmu;
  private Screen screen;

  private int cycleInLine;
  private int ly;
  private int mode;
  private boolean lastStatLine;
  private int windowLineCounter;

  public PPU(ClockCounter clockCounter, MMU mmu) { this(clockCounter, mmu, null); }

  public PPU(ClockCounter clockCounter, MMU mmu, Screen screen) {
    this.mmu = mmu;
    this.screen = (screen != null) ? screen : new HeadlessScreen();
    clockCounter.addListener(this::tick);
    reset();
  }

  public void setScreen(Screen screen) { this.screen = screen; }
  public int getMode() { return mode; }

  private void reset() {
    cycleInLine = 0;
    ly = 0;
    mode = MODE_2;
    lastStatLine = false;
    windowLineCounter = 0;
  }

  public short readLY() { return (short)(ly & 0xFF); }

  public short readStat() {
    int stat = mmu.forceGet(STAT) & 0xFF;
    stat &= 0xF8;
    if (ly == (mmu.forceGet(LYC) & 0xFF)) stat |= 0x04;
    stat |= mode & 0x3;
    return (short)(stat | 0x80);
  }

  public void writeStat(short value) {
    int old = mmu.forceGet(STAT) & 0x07;
    int next = (value & 0xF8) | old;
    mmu.forceSet(STAT, (short)next);
  }

  public void tick() {
    if (!isLCDEnabled()) {
      ly = 0;
      cycleInLine = 0;
      mode = MODE_0;
      windowLineCounter = 0;
      mmu.forceSet(LY, (short)0);
      updateStatRegister();
      return;
    }
    step();
  }

  private void step() {
    cycleInLine++;

    if (ly < SCREEN_HEIGHT) {
      int newMode;
      if (cycleInLine <= MODE_2_CYCLES)                            newMode = MODE_2;
      else if (cycleInLine <= MODE_2_CYCLES + MODE_3_CYCLES)       newMode = MODE_3;
      else                                                          newMode = MODE_0;

      if (newMode != mode) {
        if (mode == MODE_3 && newMode == MODE_0) {
          renderScanline();
        }
        mode = newMode;
      }
    } else if (mode != MODE_1) {
      mode = MODE_1;
      mmu.raiseInterrupt(MMU.VBLANK);
      screen.draw();
      windowLineCounter = 0;
    }

    if (cycleInLine >= SCANLINE_CYCLES) {
      cycleInLine = 0;
      ly++;
      if (ly >= TOTAL_LINES) ly = 0;
      mmu.forceSet(LY, (short)(ly & 0xFF));
      if (ly < SCREEN_HEIGHT) {
        mode = MODE_2;
      } else if (ly == SCREEN_HEIGHT) {
        // handled above at next step
      }
    }

    updateStatRegister();
  }

  private void updateStatRegister() {
    int stat = mmu.forceGet(STAT) & 0xFF;
    int lyc = mmu.forceGet(LYC) & 0xFF;
    boolean coincidence = (ly == lyc);
    int newStat = (stat & 0xF8) | (coincidence ? 0x04 : 0x00) | (mode & 0x3);
    mmu.forceSet(STAT, (short)newStat);

    boolean statLine = false;
    if ((newStat & 0x40) != 0 && coincidence) statLine = true;
    if ((newStat & 0x20) != 0 && mode == MODE_2) statLine = true;
    if ((newStat & 0x10) != 0 && mode == MODE_1) statLine = true;
    if ((newStat & 0x08) != 0 && mode == MODE_0) statLine = true;

    if (statLine && !lastStatLine) mmu.raiseInterrupt(MMU.LCD_STAT);
    lastStatLine = statLine;
  }

  private boolean isLCDEnabled() {
    return (mmu.forceGet(LCDC) & 0x80) != 0;
  }

  private boolean isBGEnabled() { return (mmu.forceGet(LCDC) & 0x01) != 0; }
  private boolean isWindowEnabled() { return (mmu.forceGet(LCDC) & 0x20) != 0; }
  private boolean areSpritesEnabled() { return (mmu.forceGet(LCDC) & 0x02) != 0; }

  private int bgTileDataBase() {
    return (mmu.forceGet(LCDC) & 0x10) != 0 ? 0x8000 : 0x8800;
  }
  private int windowTileMapBase() {
    return (mmu.forceGet(LCDC) & 0x40) != 0 ? 0x9C00 : 0x9800;
  }
  private int bgTileMapBase() {
    return (mmu.forceGet(LCDC) & 0x08) != 0 ? 0x9C00 : 0x9800;
  }
  private int spriteHeight() {
    return (mmu.forceGet(LCDC) & 0x04) != 0 ? 16 : 8;
  }

  private void renderScanline() {
    int[] line = new int[SCREEN_WIDTH];
    int[] bgColorCode = new int[SCREEN_WIDTH];
    renderBackground(line, bgColorCode);
    renderWindow(line, bgColorCode);
    renderSprites(line, bgColorCode);
    blitLine(line);
  }

  private void renderBackground(int[] line, int[] bgColorCode) {
    if (!isBGEnabled()) {
      for (int x = 0; x < SCREEN_WIDTH; x++) {
        line[x] = COLOR_WHITE;
        bgColorCode[x] = 0;
      }
      return;
    }

    int scy = mmu.forceGet(SCY) & 0xFF;
    int scx = mmu.forceGet(SCX) & 0xFF;
    short bgp = mmu.forceGet(BGP);
    int y = (ly + scy) & 0xFF;
    int tileRow = (y >> 3) & 0x1F;
    int mapBase = bgTileMapBase();
    int tileData = bgTileDataBase();

    for (int x = 0; x < SCREEN_WIDTH; x++) {
      int pixelX = (x + scx) & 0xFF;
      int tileCol = (pixelX >> 3) & 0x1F;
      int tileIndex = mmu.forceGet(mapBase + tileRow * 32 + tileCol) & 0xFF;
      int tileAddr = tileAddress(tileData, tileIndex);
      int lineAddr = tileAddr + (y & 7) * 2;
      int lo = mmu.forceGet(lineAddr) & 0xFF;
      int hi = mmu.forceGet(lineAddr + 1) & 0xFF;
      int bit = 7 - (pixelX & 7);
      int code = ((hi >> bit) & 1) << 1 | ((lo >> bit) & 1);
      bgColorCode[x] = code;
      line[x] = paletteColor(code, bgp);
    }
  }

  private void renderWindow(int[] line, int[] bgColorCode) {
    if (!isWindowEnabled()) return;

    int wy = mmu.forceGet(WY) & 0xFF;
    if (ly < wy) return;

    int wx = (mmu.forceGet(WX) & 0xFF) - 7;
    if (wx >= SCREEN_WIDTH) return;

    short bgp = mmu.forceGet(BGP);
    int y = windowLineCounter;
    int tileRow = (y >> 3) & 0x1F;
    int mapBase = windowTileMapBase();
    int tileData = bgTileDataBase();

    for (int x = Math.max(0, wx); x < SCREEN_WIDTH; x++) {
      int pixelX = x - wx;
      int tileCol = (pixelX >> 3) & 0x1F;
      int tileIndex = mmu.forceGet(mapBase + tileRow * 32 + tileCol) & 0xFF;
      int tileAddr = tileAddress(tileData, tileIndex);
      int lineAddr = tileAddr + (y & 7) * 2;
      int lo = mmu.forceGet(lineAddr) & 0xFF;
      int hi = mmu.forceGet(lineAddr + 1) & 0xFF;
      int bit = 7 - (pixelX & 7);
      int code = ((hi >> bit) & 1) << 1 | ((lo >> bit) & 1);
      bgColorCode[x] = code;
      line[x] = paletteColor(code, bgp);
    }
    windowLineCounter++;
  }

  private void renderSprites(int[] line, int[] bgColorCode) {
    if (!areSpritesEnabled()) return;
    int spriteH = spriteHeight();
    int drawn = 0;
    int[] drawnX = new int[10];
    int drawnCount = 0;

    for (int i = 0; i < 40 && drawn < 10; i++) {
      int addr = OAM + i * 4;
      int y = (mmu.forceGet(addr) & 0xFF) - 16;
      int x = (mmu.forceGet(addr + 1) & 0xFF) - 8;
      int tileIndex = mmu.forceGet(addr + 2) & 0xFF;
      int attrs = mmu.forceGet(addr + 3) & 0xFF;

      if (ly < y || ly >= y + spriteH) continue;
      drawn++;

      if (spriteH == 16) tileIndex &= 0xFE;

      boolean flipY = (attrs & 0x40) != 0;
      boolean flipX = (attrs & 0x20) != 0;
      int row = ly - y;
      if (flipY) row = spriteH - 1 - row;

      int tileAddr = 0x8000 + tileIndex * 16 + row * 2;
      int lo = mmu.forceGet(tileAddr) & 0xFF;
      int hi = mmu.forceGet(tileAddr + 1) & 0xFF;

      short palette = mmu.forceGet((attrs & 0x10) != 0 ? OBP1 : OBP0);
      boolean behindBg = (attrs & 0x80) != 0;

      for (int px = 0; px < 8; px++) {
        int sx = x + px;
        if (sx < 0 || sx >= SCREEN_WIDTH) continue;

        int bit = flipX ? px : 7 - px;
        int code = ((hi >> bit) & 1) << 1 | ((lo >> bit) & 1);
        if (code == 0) continue;

        boolean skip = false;
        for (int d = 0; d < drawnCount; d++) {
          if (drawnX[d] == sx) { skip = true; break; }
        }
        if (skip) continue;

        if (behindBg && bgColorCode[sx] != 0) continue;
        line[sx] = paletteColor(code, palette);
      }

      if (drawnCount < drawnX.length) drawnX[drawnCount++] = x;
    }
  }

  private void blitLine(int[] line) {
    for (int x = 0; x < SCREEN_WIDTH; x++) {
      short color = (short)line[x];
      for (int i = 0; i < SCALE; i++) {
        for (int j = 0; j < SCALE; j++) {
          screen.setPixel(SCALE * x + i, SCALE * ly + j, color);
        }
      }
    }
  }

  private static int tileAddress(int base, int tileIndex) {
    if (base == 0x8000) return 0x8000 + tileIndex * 16;
    // 0x8800 uses signed index based at 0x9000.
    return 0x9000 + ((byte)tileIndex) * 16;
  }

  private static int paletteColor(int colorCode, short palette) {
    int shade = (palette >> (colorCode * 2)) & 0x3;
    switch (shade) {
      case 0: return COLOR_WHITE;
      case 1: return COLOR_LIGHT_GREY;
      case 2: return COLOR_DARK_GREY;
      default: return COLOR_BLACK;
    }
  }
}
