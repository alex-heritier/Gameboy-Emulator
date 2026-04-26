
import java.util.EnumSet;

/**
 * Emulates the Gameboy joypad at 0xFF00.
 *
 * Register layout (P1/JOYP):
 *   Bit 7 - unused (reads 1)
 *   Bit 6 - unused (reads 1)
 *   Bit 5 - P15: selects "action" buttons  (0 = selected)
 *   Bit 4 - P14: selects directional pad   (0 = selected)
 *   Bit 3 - P13: Down / Start   (0 = pressed)
 *   Bit 2 - P12: Up / Select    (0 = pressed)
 *   Bit 1 - P11: Left / B       (0 = pressed)
 *   Bit 0 - P10: Right / A      (0 = pressed)
 *
 * When both select lines (bits 4 and 5) are 0, the result is the bitwise
 * AND of both column's button states. When neither line is selected
 * (both 1), all input lines read as 1 (nothing pressed).
 *
 * The joypad interrupt (IF bit 4) is requested on every high-to-low
 * transition on any of the input lines (P10-P13) - i.e. when a button
 * that is currently being read becomes pressed.
 */
class Joypad {
  public enum Button {
    RIGHT, LEFT, UP, DOWN,
    A, B, SELECT, START,
  }

  // The column-select nibble as last written by the CPU (bits 4 and 5).
  // Bits 0-3 are always driven by the current button state.
  private short selectNibble = 0x30; // neither column selected

  private final EnumSet<Button> pressed = EnumSet.noneOf(Button.class);
  private final MMU mmu;

  public Joypad(MMU mmu) {
    this.mmu = mmu;
  }

  /** Called by MMU when the CPU writes to 0xFF00. Only bits 4/5 are writable. */
  public void write(short value) {
    this.selectNibble = (short)(value & 0x30);
  }

  /** Called by MMU when the CPU reads 0xFF00. */
  public short read() {
    // Upper two bits always read as 1.
    short result = (short)(0xC0 | selectNibble);

    boolean actionSelected = (selectNibble & 0x20) == 0;
    boolean dirSelected    = (selectNibble & 0x10) == 0;

    // Start with all input lines released (1 = released).
    int lowNibble = 0x0F;
    if (actionSelected) lowNibble &= actionLines();
    if (dirSelected)    lowNibble &= dirLines();

    return (short)(result | (lowNibble & 0x0F));
  }

  public boolean isPressed(Button b) { return pressed.contains(b); }

  /**
   * Update a button's state. Raises a joypad interrupt if the button is
   * currently visible (selected column) and transitions to pressed.
   */
  public void set(Button button, boolean isPressed) {
    boolean wasPressed = pressed.contains(button);
    if (isPressed) pressed.add(button);
    else           pressed.remove(button);

    if (isPressed && !wasPressed && mmu != null && isButtonVisible(button)) {
      mmu.raiseInterrupt(4); // joypad interrupt
    }
  }

  /** Low nibble when the "action" column (P15) is selected. */
  private int actionLines() {
    int v = 0x0F;
    if (pressed.contains(Button.START))  v &= ~(1 << 3);
    if (pressed.contains(Button.SELECT)) v &= ~(1 << 2);
    if (pressed.contains(Button.B))      v &= ~(1 << 1);
    if (pressed.contains(Button.A))      v &= ~(1 << 0);
    return v & 0x0F;
  }

  /** Low nibble when the "directional" column (P14) is selected. */
  private int dirLines() {
    int v = 0x0F;
    if (pressed.contains(Button.DOWN))  v &= ~(1 << 3);
    if (pressed.contains(Button.UP))    v &= ~(1 << 2);
    if (pressed.contains(Button.LEFT))  v &= ~(1 << 1);
    if (pressed.contains(Button.RIGHT)) v &= ~(1 << 0);
    return v & 0x0F;
  }

  private boolean isButtonVisible(Button b) {
    switch (b) {
      case START: case SELECT: case A: case B:
        return (selectNibble & 0x20) == 0;
      case UP: case DOWN: case LEFT: case RIGHT:
        return (selectNibble & 0x10) == 0;
      default:
        return false;
    }
  }
}
