
class Test {

  public static void main(String args[]) {
    Util.log("Gameboy Emulator");
    Util.log("################");

    Cart cart = new Cart("roms/bios.gb");
    // Util.log("GB Bios: ");
    // Util.log(cart.toString());

    Gameboy gb = new Gameboy(cart);
    gb.run();
  }
}
