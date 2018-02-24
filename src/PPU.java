
class PPU {

  // Screen dimensions
  public static final int VIDEO_WIDTH = 256;
  public static final int VIDEO_HEIGHT = 256;
  public static final int SCREEN_WIDTH = 160;
  public static final int SCREEN_HEIGHT = 144;
  public static final int SCALE = 3;
  public static final int SCALED_VIDEO_WIDTH = VIDEO_WIDTH * SCALE;
  public static final int SCALED_VIDEO_HEIGHT = VIDEO_WIDTH * SCALE;
  public static final int SCALED_SCREEN_WIDTH = SCREEN_WIDTH * SCALE;
  public static final int SCALED_SCREEN_HEIGHT = SCREEN_HEIGHT * SCALE;

  // Video register addresses
  public static final int OAM = 0xFE00;
  public static final int JOYPAD = 0xFF00;
  public static final int LCDC_CONTROL = 0xFF40;
  public static final int LCDC_STATUS = 0xFF41;
  public static final int SCY = 0xFF42;
  public static final int SCX = 0xFF43;
  public static final int LY = 0xFF44;  // 0 - 0x99
  public static final int LYC = 0xFF45; // when LY == LYC, trigger
  public static final int DMA = 0xFF46;
  public static final int BG_PALETTE = 0xFF47;
  public static final int WINDOW_PALETTE = BG_PALETTE;
  public static final int OBJ_PALETTE_0 = 0xFF48;
  public static final int OBJ_PALETTE_1 = 0xFF49;
  public static final int WINDOW_Y_POSITION = 0xFF4A;
  public static final int WINDOW_X_POSITION = 0xFF4B; // really window.x - 7

  // LCD colors
  public static final int COLOR_BLACK       = 0x00;
  public static final int COLOR_DARK_GREY   = 0x77;
  public static final int COLOR_LIGHT_GREY  = 0xCC;
  public static final int COLOR_WHITE       = 0xFF;
  public static final int TRANSPARENT = -1;

  // LCD mode clock counts
  public static final int MODE_0_CC = 202 / 4;    // H-Blank
  public static final int MODE_1_CC = 4560 / 4;   // V-Blank
  public static final int MODE_2_CC = 82 / 4;     // Reading from OAM
  public static final int MODE_3_CC = 172 / 4;    // Transferring data to LCD
  public static final int SCANLINE_CC = (MODE_0_CC + MODE_2_CC + MODE_3_CC) / 4;

  // LCD mode codes
  public static final int MODE_0 = 0; // H-Blank
  public static final int MODE_1 = 1; // V-Blank
  public static final int MODE_2 = 2; // Reading from OAM
  public static final int MODE_3 = 3; // Transferring to LCD


  private MMU mmu;
  private ClockCounter clockCounter;
  private Screen screen;

  // Timing variables
  private int lastClockCount; // The ClockCounter's clock count as of last tick()
  private int modeCounter;    // The tick's spent in current mode
  private int mode;           // The LCD's current mode

  public PPU(ClockCounter clockCounter, MMU mmu) {
    this.clockCounter = clockCounter;
    this.mmu = mmu;
    this.screen = new BasicScreen(mmu);
    this.lastClockCount = clockCounter.count();
    reset();
  }

  private void reset() {
    this.modeCounter = 0;
    this.mode = MODE_2;
  }

  public void tick() {
    int delta = clockCounter.count() - lastClockCount;
    lastClockCount += delta;

    if (!isLCDEnabled()) {
      modeCounter = lastClockCount / SCANLINE_CC;  // Keep line count synced
      reset(); // slows down and bugs the whole display
      return;
    }

    // TODO implement LCD status interrupts (mode0, mode1, etc)

    handleClockChange(delta);
  }

  private void handleClockChange(int delta) {
    // Update mode counter
    modeCounter += delta;

    // Util.log("MODE - " + mode);
    // Util.log("MODE COUNTER - " + modeCounter);
    // Util.log("LY - " + mmu.get(LY));

    // If in V-Blank
    if (mode == MODE_1) {

      // If in new scanline
      if (modeCounter > SCANLINE_CC) {
        // Move to next scanline
        modeCounter -= SCANLINE_CC;

        // Increment LY
        short oldLY = mmu.get(LY);
        short newLY = (short)(CPUMath.inc8(oldLY).get8() % 0x9A);
        mmu.set(LY, newLY);
      }

      short ly = mmu.get(LY);
      if (ly == 0) {
        // Move to mode 2
        mode = MODE_2;

        // Update LCD status
        short lcdStatus = mmu.get(LCDC_STATUS);
        lcdStatus = CPUMath.setBit(lcdStatus, 1);
        lcdStatus = CPUMath.resetBit(lcdStatus, 0);
        mmu.set(LCDC_STATUS, lcdStatus);

        // Attempt to raise interrupt
        if (CPUMath.getBit(lcdStatus, 5) > 0)
          mmu.raiseInterrupt(1);
      }
    }
    // Reading from OAM
    else if (mode == MODE_2) {

      // Is no longer in mode2
      if (modeCounter > MODE_2_CC) {
        // Move to next mode
        modeCounter -= MODE_2_CC;
        mode = MODE_3;

        // Update LCD status
        short lcdStatus = mmu.get(LCDC_STATUS);
        lcdStatus = CPUMath.setBit(lcdStatus, 1);
        lcdStatus = CPUMath.resetBit(lcdStatus, 0);
        mmu.set(LCDC_STATUS, lcdStatus);
      }
    }
    // Transferring to LCD
    else if (mode == MODE_3) {

      // Is no longer in mode3
      if (modeCounter > MODE_3_CC) {
        // Move to next mode
        modeCounter -= MODE_3_CC;
        mode = MODE_0;

        // Update LCD status
        short lcdStatus = mmu.get(LCDC_STATUS);
        lcdStatus = CPUMath.setBit(lcdStatus, 1);
        lcdStatus = CPUMath.setBit(lcdStatus, 0);
        mmu.set(LCDC_STATUS, lcdStatus);

        // Attempt to raise interrupt
        if (CPUMath.getBit(lcdStatus, 3) > 0)
          mmu.raiseInterrupt(1);
      }
    }
    // H-Blank
    else {

      // Is no longer in mode0
      if (modeCounter > MODE_0_CC) {

        // Increment LY
        short oldLY = mmu.get(LY);
        short newLY = (short)(CPUMath.inc8(oldLY).get8() % 0x9A);
        mmu.set(LY, newLY);

        // Compare LY and LYC
        short lyc = mmu.get(LYC);

        if (newLY == lyc) {
          handleCoincidence();  // raises STAT interrupt if enabled
        } else {
          // Reset coincidence bit
          short lcdStatus = mmu.get(LCDC_STATUS);
          lcdStatus = CPUMath.resetBit(lcdStatus, 2);
          mmu.set(LCDC_STATUS, lcdStatus);
        }

        // Is in V-Blank
        if (newLY == 0x90) {

          // Move to next mode
          mode = MODE_1;
          modeCounter -= MODE_0_CC;

          // Update LCD status
          short lcdStatus = mmu.get(LCDC_STATUS);
          lcdStatus = CPUMath.resetBit(lcdStatus, 1);
          lcdStatus = CPUMath.setBit(lcdStatus, 0);
          mmu.set(LCDC_STATUS, lcdStatus);

          // Attemt to raise interrupt (Different from dedicated V-Blank interrupt)
          if (CPUMath.getBit(lcdStatus, 4) > 0)
            mmu.raiseInterrupt(1);

          // Raise dedicated V-Blank interrupt
          mmu.raiseInterrupt(0);
        }
        // Is entering mode2
        else {

          // Move to next mode
          mode = MODE_2;
          modeCounter -= MODE_0_CC;

          // Update LCD status
          short lcdStatus = mmu.get(LCDC_STATUS);
          lcdStatus = CPUMath.setBit(lcdStatus, 1);
          lcdStatus = CPUMath.resetBit(lcdStatus, 0);
          mmu.set(LCDC_STATUS, lcdStatus);

          // Attempt to raise interrupt
          if (CPUMath.getBit(lcdStatus, 5) > 0)
            mmu.raiseInterrupt(1);

          // Draw screen
          if (screen != null) {
            updateScreen();
            screen.draw();
          }
        }
      }
    }


    // // If lastClockCount exceeds the time it takes to draw a line (line is "done")
    // if (modeCounter <= (lastClockCount / SCANLINE_CC)) {
    //   short ly = mmu.get(LY);
    //
    //   // Draw line if outside of V-Blank
    //   if (screen != null && ly < 0x90) {
    //     updateScreen();
    //     screen.draw();
    //   }
    //
    //   // Move to next line
    //   modeCounter++;
    //
    //   // Increment LY
    //   short lyNext = (short)(CPUMath.inc8(mmu.get(LY)).get8() % 0x9A);
    //   mmu.set(LY, lyNext);
    //
    //   // If VBlank entered
    //   if (lyNext == 0x90)
    //     mmu.raiseInterrupt(0);
    //
    //   // Compare LY and LYC
    //   short lyc = mmu.get(LYC);
    //
    //   if (ly == lyc) {
    //     handleCoincidence();  // raises STAT interrupt if enabled
    //   } else {
    //     // Reset coincidence bit
    //     short lcdcStatus = CPUMath.resetBit(mmu.get(LCDC_STATUS), 2);
    //     mmu.set(LCDC_STATUS, lcdcStatus);
    //   }
    // }
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
      mmu.raiseInterrupt(1);
    }
  }

  private int getWindowTileData() { return getBGTileData(); }
  private int getBGTileData() {
    int selector = CPUMath.getBit(mmu.get(LCDC_CONTROL), 4);
    return selector == 0 ? 0x8800 : 0x8000;
  }

  private int getSpriteTileData() {
    return 0x8000;
  }

  private int getWindowTileMap() {
    int selector = CPUMath.getBit(mmu.get(LCDC_CONTROL), 6);
    return selector == 0 ? 0x9800 : 0x9C00;
  }

  private int getBGTileMap() {
    int selector = CPUMath.getBit(mmu.get(LCDC_CONTROL), 3);
    return selector == 0 ? 0x9800 : 0x9C00;
  }

  private boolean isBGEnabled() {
    short lcdcValue = mmu.get(LCDC_CONTROL);
    return CPUMath.getBit(lcdcValue, 0) > 0;
  }

  private boolean isWindowEnabled() {
    short lcdcValue = mmu.get(LCDC_CONTROL);
    return CPUMath.getBit(lcdcValue, 5) > 0;
  }

  private boolean areSpritesEnabled() {
    short lcdcValue = mmu.get(LCDC_CONTROL);
    return CPUMath.getBit(lcdcValue, 1) > 0;
  }

  private void updateScreen() {
    int y = mmu.get(LY);

    // Draw a horizontal line of pixels
    for (int x = 0; x < SCREEN_WIDTH; x++) {
      short color = getPixelColor(x, y);

      // Upscale the pixel according to scale factor
      for (int i = 0 ; i < SCALE; i++) {
        for (int j = 0; j < SCALE; j++) {
          screen.setPixel(SCALE * x + i, SCALE * y + j, color);
        }
      }
    }
  }

  private short getPixelColor(int pixelX, int pixelY) {
    if (!isLCDEnabled()) return COLOR_WHITE;

    short activePixel = getWindowPixelColor(pixelX, pixelY);
    if (activePixel == 0xFF)
      activePixel = getBackgroundPixelColor(pixelX, pixelY);

    // A transparent pixel will return -1
    short spritePixel = getSpritePixelColor(pixelX, pixelY);
    if (spritePixel != TRANSPARENT)
      activePixel = spritePixel;

    return activePixel;
  }

  private short getBackgroundPixelColor(int pixelX, int pixelY) {
    // Draw white if the background is disabled
    if (!isBGEnabled())
      return (short)0xFF;

    int bgMap = getBGTileMap();
    int bgTiles = getBGTileData();
    short bgPalette = mmu.get(BG_PALETTE);
    short scrollY = mmu.get(SCY);
    short scrollX = mmu.get(SCX);
    int posY = (pixelY + scrollY) & 0xFF;
    int posX = (pixelX + scrollX) & 0xFF;
    int tileRow = (posY / 8) * 32;  // bgMap tile row
    int tileCol = posX / 8;  // bgMap tile column
    int tileIndexAddress = bgMap + tileRow + tileCol;  // tile index in the tile data
    int tileIndex = mmu.get(tileIndexAddress);
    int tile = getTileBaseAddress(bgTiles, tileIndex); // tile base address

    int tileLine = tile + 2 * (posY % 8); // The tile's horizontal pixelY being drawn
    short tileLineByte1 = mmu.get(tileLine);  // Lower byte
    short tileLineByte2 = mmu.get(CPUMath.add16(tileLine, 1));  // Upper byte
    int colorCode = getColorCode(tileLineByte1, tileLineByte2, 7 - (posX % 8));
    int paletteColor = getPaletteColor(colorCode, bgPalette);
    short color = getColor(paletteColor);

    return color;
  }

  private short getWindowPixelColor(int pixelX, int pixelY) {
    // Draw white if the window is disabled
    if (!isWindowEnabled()) {
      return (short)0xFF;
    }

    int windowMap = getWindowTileMap();
    int windowTiles = getWindowTileData();
    short windowPalette = mmu.get(WINDOW_PALETTE);
    short windowY = mmu.get(WINDOW_Y_POSITION);
    short windowX = (short)((mmu.get(WINDOW_X_POSITION) + 7) & 0xFF);
    int posY = (pixelY - windowY) & 0xFF;
    int posX = (pixelX + windowX) & 0xFF;
    int tileRow = (posY / 8) * 32;  // windowMap tile row
    int tileCol = posX / 8;  // windowMap tile column
    int tileIndexAddress = windowMap + tileRow + tileCol;  // tile index in the tile data
    int tileIndex = mmu.get(tileIndexAddress);
    int tile = getTileBaseAddress(windowTiles, tileIndex); // tile base address

    int tileLine = tile + 2 * (posY % 8); // The tile's horizontal pixelY being drawn
    short tileLineByte1 = mmu.get(tileLine);  // Lower byte
    short tileLineByte2 = mmu.get(CPUMath.add16(tileLine, 1));  // Upper byte
    int colorCode = getColorCode(tileLineByte1, tileLineByte2, 7 - (posX % 8));
    int paletteColor = getPaletteColor(colorCode, windowPalette);
    short color = getColor(paletteColor);

    return color;
  }

  private short getSpritePixelColor(int pixelX, int pixelY) {
    // Return a transparent pixel if sprites are disabled
    if (!areSpritesEnabled())
      return (short)TRANSPARENT;

    int spriteTiles = getSpriteTileData();

    short theSpriteY = 0;
    short theSpriteX = 0;
    short theTileIndex = 0;
    short theAttributes = 0;
    int theColorCode = 0;
    int thePaletteColor = 0;

    short lowestSpriteX = Short.MAX_VALUE;
    short lowestTileIndex = Short.MAX_VALUE;

    for (int i = OAM; i < 0xFEA0; i += 4) {
      short spriteY = mmu.get(i);
      short spriteX = mmu.get(i + 1);
      short tileIndex = mmu.get(i + 2);
      short attributes = mmu.get(i + 3);

      // Util.log("OAM INDEX - " + Util.hex(i));
      if (((spriteX < lowestSpriteX) || (spriteX == lowestSpriteX && tileIndex < lowestTileIndex))
          && spriteContainsPixel(spriteX, spriteY, pixelX, pixelY)) {

        lowestSpriteX = spriteX;
        if (tileIndex < lowestTileIndex)
          lowestTileIndex = tileIndex;

        int posY = pixelY - (spriteY - 16);
        int posX = pixelX - (spriteX - 8);

        // Util.log("SPRITE Y - " + Util.hex(spriteY));
        // Util.log("SPRITE X - " + Util.hex(spriteX));
        // Util.log("PIXEL Y - " + Util.hex(pixelY));
        // Util.log("PIXEL X - " + Util.hex(pixelX));
        // Util.log("POS Y - " + Util.hex(posY));
        // Util.log("POS X - " + Util.hex(posX));
        // Util.log("SPRITE CONTAINS - " + spriteContainsPixel(spriteX, spriteY, pixelX, pixelY));
        // Util.log();

        short palette = mmu.get(getSpritePalette(attributes));
        int tile = getTileBaseAddress(spriteTiles, tileIndex);
        int tileLine = tile + 2 * posY; // The tile's horizontal pixelY being drawn
        short tileLineByte1 = mmu.get(tileLine);  // Lower byte
        short tileLineByte2 = mmu.get(CPUMath.add16(tileLine, 1));  // Upper byte
        int lineBit = 7 - (posX % 8);
        int colorCode = getColorCode(tileLineByte1, tileLineByte2, lineBit);
        int paletteColor = getPaletteColor(colorCode, palette);

        // Util.log("TILE LINE BYTE 1 - " + Util.hex(tileLineByte1));
        // Util.log("TILE LINE BYTE 2 - " + Util.hex(tileLineByte2));
        // Util.log("LINE BIT - " + Util.hex(lineBit));
        // Util.log("PALETTE - " + Util.hex(palette));
        // Util.log("####### COLOR CODE - " + Util.hex(colorCode));
        // Util.log("####### PALETTE COLOR - " + Util.hex(paletteColor));
        // Util.log("####### COLOR - " + Util.hex(getColor(colorCode)));
        //     Util.debug = true;
        // getColorCode(tileLineByte1, tileLineByte2, lineBit);
        //     Util.debug = false;
        // Util.log();

        // Util.log("TILE INDEX - " + Util.hex(tileIndex));
        // Util.log("TILE ADDRESS - " + Util.hex(tile));
        // Util.log("BYTE 1 - " + Util.hex(tileLineByte1));
        // Util.log("BYTE 2 - " + Util.hex(tileLineByte2));
        // Util.log();

        // colorCode 3 is transparent
        if (colorCode != 0) {
          theSpriteY = spriteY;
          theSpriteX = spriteX;
          theTileIndex = tileIndex;
          theAttributes = attributes;
          theColorCode = colorCode;
          thePaletteColor = paletteColor;
        }
      }
    }

    short color = TRANSPARENT;
    if (theColorCode != 0)
      color = getColor(theColorCode);

    return color;
  }

  private boolean spriteContainsPixel(short posX, short posY, int pixelX, int pixelY) {
    int spriteSize = getSpriteSize();
    return spriteContainsPixel(posX, posY, pixelX, pixelY, spriteSize);
  }
  private static boolean spriteContainsPixel(short posX, short posY, int pixelX, int pixelY, int spriteSize) {
    boolean checkY = (posY - 16) <= pixelY && (posY - 16 + spriteSize) > pixelY;
    boolean checkX = (posX - 8) <= pixelX && posX > pixelX;

    // Util.log("(spriteX - 8) == " + Util.hex(posX - 8));
    // Util.log("(spriteY - 16) == " + Util.hex(posY - 16));
    // Util.log("PIXEL X - " + Util.hex(pixelX));
    // Util.log("PIXEL Y - " + Util.hex(pixelY));
    // Util.log();

    return checkY && checkX;
  }

  private int getSpriteSize() {
    short lcdcControl = mmu.get(LCDC_CONTROL);
    int sizeSelect = CPUMath.getBit(lcdcControl, 2);
    return sizeSelect == 0 ? 8 : 16;
  }

  private int getSpritePalette(short attributes) {
    int index = CPUMath.getBit(attributes, 4);
    int palette = index == 0 ? OBJ_PALETTE_0 : OBJ_PALETTE_1;
    return palette;
  }

  private static int getTileBaseAddress(int tileData, int tileIndex) {
    int tileOffset = 128; // Needed for tile data at 0x8800
    int tileSize = 16;  // in bytes

    int tile = 0;
    if (tileData == 0x8000)
      tile = tileData + tileSize * tileIndex;
    else
      tile = 0x9000 + tileSize * (byte)tileIndex;

    return tile;
  }

  public static int getColorCode(short lowerByte, short upperByte, int bit) {
    int lowerBit = CPUMath.getBit(lowerByte, bit);
    int upperBit = CPUMath.getBit(upperByte, bit);

    short colorCode = (short)((upperBit << 1) + lowerBit);
    if (colorCode < 0 || colorCode > 3) {
      Util.errn("PPU.getColorCode - Bad color code.");
      return -1;
    }

    // Util.debug("Lower bit - " + Util.bin(lowerBit));
    // Util.debug("Upper bit - " + Util.bin(upperBit));
    // Util.debug("Color Code - " + Util.bin(colorCode));

    return colorCode;
  }

  public static int getPaletteColor(int colorCode, short palette) {
    int shiftAmount = 8 - 2 * (colorCode + 1);
    int paletteColor = (0xFF & (palette << shiftAmount)) >> 6;

    // Util.debug("Palette Color - " + Util.bin(paletteColor));

    return paletteColor;
  }

  public static short getColor(int paletteColor) {
    if (paletteColor < 0 || paletteColor > 3) {
      Util.errn("PPU.getColor - Bad palette color.");
      return COLOR_WHITE;
    }

    // Util.log("Color Code - " + Util.bin(paletteColor));

    short color = COLOR_WHITE;
    switch (paletteColor) {
      case 3: color = COLOR_BLACK; break;
      case 2: color = COLOR_DARK_GREY; break;
      case 1: color = COLOR_LIGHT_GREY; break;
      case 0: color = COLOR_WHITE; break;
      default: color = COLOR_WHITE; break;
    }
    return color;
  }


  public static void main(String args[]) {
    int tileData = 0x8800;

    for (int i = 0; i < 384; i++) {
      int base = getTileBaseAddress(tileData, i);
      Util.log("BASE #" + i + " - " + Util.hex(base));
    }
  }
}
