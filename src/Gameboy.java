
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
    for (int i = 0; i < 0xF; i++) {
      cpu.tick();
    }
  }
}
