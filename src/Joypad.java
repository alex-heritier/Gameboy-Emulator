
import java.util.HashMap;

abstract class Joypad {
  public static final boolean ON = true;
  public static final boolean OFF = false;

  public enum Button {
    RIGHT,
    LEFT,
    UP,
    DOWN,
    A,
    B,
    SELECT,
    START,
  };

  private static HashMap<Button, Boolean> buttons = new HashMap();
  static {
    Joypad.buttons.put(Button.RIGHT, false);
    Joypad.buttons.put(Button.LEFT, false);
    Joypad.buttons.put(Button.UP, false);
    Joypad.buttons.put(Button.DOWN, false);
    Joypad.buttons.put(Button.A, false);
    Joypad.buttons.put(Button.B, false);
    Joypad.buttons.put(Button.SELECT, false);
    Joypad.buttons.put(Button.START, false);
  }

  public static short getState(short memValue) {
    short state = 0;
    if ((memValue & 0x20) == 0)
      state = getButtonState();
    else if ((memValue & 0x10) == 0)
      state = getDirectionalState();

    return (short)(state | (memValue & 0xF0));
  }

  private static short getButtonState() {
    short state = 0;
    if (read(Button.START)) state = CPUMath.setBit(state, 3);
    if (read(Button.SELECT)) state = CPUMath.setBit(state, 2);
    if (read(Button.B)) state = CPUMath.setBit(state, 1);
    if (read(Button.A)) state = CPUMath.setBit(state, 0);
    // Util.log("Joypad.getButtonState - " + Util.bin(state));

    return (short)(state & 0x0F);
  }

  private static short getDirectionalState() {
    short state = 0;
    if (read(Button.DOWN)) state = CPUMath.setBit(state, 3);
    if (read(Button.UP)) state = CPUMath.setBit(state, 2);
    if (read(Button.LEFT)) state = CPUMath.setBit(state, 1);
    if (read(Button.RIGHT)) state = CPUMath.setBit(state, 0);
    // Util.log("Joypad.getDirectionalState - " + Util.bin(state));

    return (short)(state & 0x0F);
  }

  public static boolean read(Button button) {
    return buttons.get(button);
  }

  public static void set(Button button, boolean val) {
    buttons.put(button, val);
  }
}
