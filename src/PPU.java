
class PPU {

  public static final int FULL_LINE_CLOCK_COUNT = (456 / 4);
  public static final int HBLANK_CLOCK_COUNT = (202 / 4);
  public static final int READING_OAM_CLOCK_COUNT = (82 / 4);
  public static final int DRAWING_LINE_CLOCK_COUNT = (172 / 4);

  public static final int VIDEO_WIDTH = 256;
  public static final int VIDEO_HEIGHT = 256;
  public static final int SCREEN_WIDTH = 160;
  public static final int SCREEN_HEIGHT = 144;

  public static final int LCDC_CONTROL = 0xFF40;
  public static final int LCDC_STATUS = 0xFF41;
  public static final int SCY = 0xFF42; // 0 - 0xFF
  public static final int SCX = 0xFF43; // 0 - 0xFF
  public static final int LY = 0xFF44;  // 0 - 0x99
  public static final int LYC = 0xFF45; // when LY == LYC, trigger
  public static final int DMA = 0xFF46;
  public static final int BG_PALETTE = 0xFF47;
  public static final int OBJ_PALETTE_0 = 0xFF48;
  public static final int OBJ_PALETTE_1 = 0xFF49;
  public static final int WINDOW_Y_POSITION = 0xFF4A;
  public static final int WINDOW_X_POSITION = 0xFF4B; // really window.x - 7

  private MMU mmu;
  private ClockCounter clockCounter;
  private Screen screen;

  private int lastClockCount;
  private int lineCounter;

  public PPU(ClockCounter clockCounter) {
    this.clockCounter = clockCounter;
    this.screen = new BasicScreen();
    this.lastClockCount = clockCounter.count();
    this.lineCounter = 0;
  }

  public void setMMU(MMU mmu) {
    this.mmu = mmu;
  }

  public void tick() {
    int delta = clockCounter.count() - lastClockCount;
    lastClockCount += delta;

    if (!isLCDEnabled()) {
      lineCounter = lastClockCount / FULL_LINE_CLOCK_COUNT;  // Keep line count synced
      return;
    }

    // TODO implement LCD status interrupts (mode0, mode1, etc)

    // If lastClockCount exceeds the time it takes to draw a line
    if (lineCounter < (lastClockCount / FULL_LINE_CLOCK_COUNT)) {
      lineCounter++;

      // Increment LY
      short lyNext = (short)(CPUMath.inc8(mmu.get(LY)).get8() % 0x9A);
      mmu.set(LY, lyNext);

      // Util.log("LY - " + Util.hex(lyNext));

      // Compare LY and LYC
      short ly = mmu.get(LY);
      short lyc = mmu.get(LYC);

      // If VBlank entered
      if (ly == 0x90) {
        if (screen != null) {
          // Draw pixels
          updateScreen();
          screen.draw();
        }
      }


      if (ly == lyc) {
        handleCoincidence();
      } else {
        // Reset coincidence bit
        short lcdcStatus = CPUMath.resetBit(mmu.get(LCDC_STATUS), 2);
        mmu.set(LCDC_STATUS, lcdcStatus);
      }
    }
  }

  private boolean isLCDEnabled() {
    return CPUMath.getBit(mmu.get(LCDC_CONTROL), 7) > 0;
  }

  private void handleCoincidence() {
    short lcdcStatus = mmu.get(LCDC_STATUS);

    // Update LCDC status
    lcdcStatus = CPUMath.setBit(lcdcStatus, 2);
    mmu.set(LCDC_STATUS, lcdcStatus);

    // Check if coincidence interrupt is enabled
    if (CPUMath.getBit(lcdcStatus, 6) > 0) {
      // Set interrupt flag
      short interruptFlags = CPUMath.setBit(mmu.get(0xFF0F), 1);
      mmu.set(0xFF0F, interruptFlags);
    }
  }

  private int getWindowTileDate() { return getBGTileData(); }
  private int getBGTileData() {
    int selector = CPUMath.getBit(mmu.get(LCDC_CONTROL), 4);
    return selector == 0 ? 0x8800 : 0x8000;
  }

  private int getBGTileMap() {
    int selector = CPUMath.getBit(mmu.get(LCDC_CONTROL), 3);
    return selector == 0 ? 0x9800 : 0x9C00;
  }

  private void updateScreen() {
    updateBG();
  }

  private void updateBG() {
    int bgMap = getBGTileMap();
    int bgTiles = getBGTileData();
    int tileOffset = 128; // Needed for tile data at 0x8800
    int tileSize = 16;  // in bytes
    short bgPalette = mmu.get(BG_PALETTE);
    short yScroll = mmu.get(SCY);
    short xScroll = mmu.get(SCX);

    // Util.log("X SCROLL - " + xScroll);
    // Util.log("Y SCROLL - " + yScroll);

    // Draw 256 horizontal lines
    for (int line = 0; line < VIDEO_HEIGHT; line++) {
      int yPos = (line + yScroll - (VIDEO_HEIGHT - SCREEN_HEIGHT) / 2) & 0xFF;
      int tileRow = (yPos / 8) * 32;  // bgMap tile row

      // Util.log("TILE ROW - " + Util.hex(tileRow));

      // Draw a horizontal line of pixels
      for (int pixel = 0; pixel < VIDEO_WIDTH; pixel++) {
        int xPos = (pixel + xScroll - (VIDEO_WIDTH - SCREEN_WIDTH) / 2) & 0xFF;
        int tileCol = xPos / 8;  // bgMap tile column
        int tileIndexAddress = bgMap + tileRow + tileCol;  // tile index in the tile data
        int tileIndex = mmu.get(tileIndexAddress);
        int tile = 0; // tile base address
        if (bgTiles == 0x8000)
          tile = bgTiles + tileSize * tileIndex;
        else
          tile = bgTiles + tileSize * ((byte)tileIndex + (byte)tileOffset);

        int tileLine = tile + 2 * (yPos % 8); // The tile's horizontal line being drawn
        short tileLineByte1 = mmu.get(tileLine);  // Lower byte
        short tileLineByte2 = mmu.get(CPUMath.add16(tileLine, 1));  // Upper byte
        int colorCode = getColorCode(tileLineByte1, tileLineByte2, 7 - (xPos % 8), bgPalette);
        short color = getColor(colorCode);

        if (colorCode == 0x00) {
          // Util.log("COLOR CODE - " + Util.hex(colorCode));
          // Util.log("COLOR - " + Util.hex(color));
        }



        if (tileIndex != 0) {
          // Util.log();
          // Util.log("TILE ROW - " + Util.hex(tileRow));
          // Util.log("TILE COL - " + Util.hex(tileCol));
          // Util.log("TILE INDEX ADDRESS - " + Util.hex(tileIndexAddress));
          // Util.log("TILE INDEX VALUE - " + Util.hex(tileIndex));
          // Util.log("TILE BASE ADDRESS - " + Util.hex(tile));
        }



        screen.setPixel(pixel, line, color);

        // Util.log("TILE - " + Util.hex(tile));
      }
    }
  }

  private int getColorCode(short lowerByte, short upperByte, int bit, short palette) {
    int lowerBit = CPUMath.getBit(lowerByte, bit);
    int upperBit = CPUMath.getBit(upperByte, bit);

    short paletteCode = (short)((upperBit << 1) + lowerBit);
    if (paletteCode < 0 || paletteCode > 3) {
      Util.errn("PPU.getColorCode - Bad palette code.");
      return -1;
    }

    // Util.log("Lower bit - " + Util.bin(lowerBit));
    // Util.log("Upper bit - " + Util.bin(upperBit));
    // Util.log("Palette Code - " + Util.bin(paletteCode));

    int shiftAmount = 8 - 2 * (paletteCode + 1);
    int colorCode = (0xFF & (palette << shiftAmount)) >> 6;

    // Util.log("Color Code - " + Util.bin(colorCode));

    return colorCode;
  }

  private short getColor(int colorCode) {
    if (colorCode < 0 || colorCode > 3) {
      Util.errn("PPU.getColor - Bad color code.");
      return -1;
    }

    // Util.log("Color Code - " + Util.bin(colorCode));

    short color = 0;
    switch (colorCode) {
      case 3: color = 0x00; break;
      case 2: color = 0x77; break;
      case 1: color = 0xCC; break;
      case 0: color = 0xFF; break;
      default: color = 0x22; break;
    }
    return color;
  }

  public void handleIOPortWrite(int address, short value) {}


  public static void main(String args[]) {
    PPU ppu = new PPU(new ClockCounter());

    short lowerByte = 0x7F;
    short upperByte = 0xFF;
    short palette = 0xFC;

    int colorCode = ppu.getColorCode(lowerByte, upperByte, 7, palette);
    short color = ppu.getColor(colorCode);
    Util.log("COLOR CODE - " + colorCode);
    Util.log("COLOR - " + color);
  }
}
