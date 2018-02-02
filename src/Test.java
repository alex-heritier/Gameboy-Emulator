
class Test {

  public static void main(String args[]) {
    Util.log("Gameboy Emulator");
    Util.log("################");

    String bios = "roms/bios.gb";
    String test = "dev/test.gb";
    String blargg = "roms/tests/cpu_instrs/individual/06-ld r,r.gb";

    Cart cart = new Cart(test);
    // Util.log("GB Bios: ");
    // Util.log(cart.toString());

    Gameboy gb = new Gameboy(cart);
    gb.run();
  }
}
