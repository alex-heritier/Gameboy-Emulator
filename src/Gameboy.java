
import java.io.Console;

class Gameboy {
  private CPUState state;
  private CPU cpu;
  private MMU mmu;
  private Cart cart;

  public Gameboy(Cart cart) {
    this.cart = cart;
    this.state = new CPUState();
    this.mmu = new MMU();
    this.cpu = new CPU(state, mmu, cart);
  }

  public void run() {
    cpu.dump();
    for (int i = 0; i < 0xF; i++) {
      cpu.tick();
    }
    cpu.dump();

    Util.log("CPU checksum: " + Util.hex(cpu.checksum()));
  }

  private void getInput() {
    Console c = System.console();
    if (c != null) {
      String input = c.readLine();
      handleInput(input, c);
    }
  }

  private void handleInput(String input, Console c) {
    if (input == null || input.equals("")) return;

    if (input.equals("dump")) {
      cpu.dump();
    }
    else if (input.equals("mem")) {
      mmu.dump();
    }
  }
}
