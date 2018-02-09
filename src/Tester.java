
class Tester {

  public static void main(String args[]) {
    Util.log("Gameboy Emulator");
    Util.log("################");

    String tetris = "roms/tetris.gb";
    String bios = "roms/bios.gb";
    String test = "dev/test.gb";
    String blargg_1 = "roms/tests/cpu_instrs/individual/01-special.gb";
    String blargg_2 = "roms/tests/cpu_instrs/individual/02-interrupts.gb";
    String blargg_3 = "roms/tests/cpu_instrs/individual/03-op sp,hl.gb";
    String blargg_4 = "roms/tests/cpu_instrs/individual/04-op r,imm.gb";
    String blargg_5 = "roms/tests/cpu_instrs/individual/05-op rp.gb";
    String blargg_6 = "roms/tests/cpu_instrs/individual/06-ld r,r.gb";
    String blargg_7 = "roms/tests/cpu_instrs/individual/07-jr,jp,call,ret,rst.gb";
    String blargg_8 = "roms/tests/cpu_instrs/individual/08-misc instrs.gb";
    String blargg_9 = "roms/tests/cpu_instrs/individual/09-op r,r.gb";
    String blargg_10 = "roms/tests/cpu_instrs/individual/10-bit ops.gb";
    String blargg_11 = "roms/tests/cpu_instrs/individual/11-op a,(hl).gb";

    Cart cart = new Cart(blargg_9);
    Gameboy gb = new Gameboy(cart);
    gb.run();
  }
}
