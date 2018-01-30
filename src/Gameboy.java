
class Gameboy {
  private CPU cpu;
  private MMU mmu;
  private Cart cart;

  public Gameboy(Cart cart) {
    this.cart = cart;
    this.mmu = new MMU();
    this.cpu = new CPU(mmu, cart);
  }

  public void run() {
    // cpu.dump();
    for (int i = 0; i < 0x30; i++) {
      cpu.tick();
    }
    // cpu.dump();
  }
}
