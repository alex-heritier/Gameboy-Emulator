
class PPU {

  private MMU mmu;

  public void setMMU(MMU mmu) {
    this.mmu = mmu;
  }

  public void tick() {
  }

  public void handle(int address, short value) {
    // Log Link Cable writes
    if (address == 0xFF02 && value == 0x81) {
      short data = mmu.get(0xFF01);
      System.out.print((char)data);
    }

    // Set V-Blank when CPU checks status
    if (address == 0xFF40 && CPUMath.getBit(value, 7) > 0) {
      int lcdc_y_coordinate = 0xFF44;
      mmu.set(lcdc_y_coordinate, (short)0x90);
    }
  }
}
