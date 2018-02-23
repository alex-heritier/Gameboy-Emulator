
class Joypad {

  public static final boolean ON = true;
  public static final boolean OFF = false;

  public static final int RIGHT = 0;
  public static final int LEFT = 1;
  public static final int UP = 2;
  public static final int DOWN = 3;
  public static final int A = 4;
  public static final int B = 5;
  public static final int SELECT = 6;
  public static final int START = 7;

  private static short joypadState = 0;

  public static short getState(short joypadValue) {
    short state = 0;
    if ((joypadValue & 0x20) == 0)
      state = getButtonState();
    else if ((joypadValue & 0x10) == 0)
      state = getDirectionalState();

    return (short)(state | (joypadValue & 0xF0));
  }

  private static short getDirectionalState() {
    return (short)(joypadState & 0x0F);
  }

  private static short getButtonState() {
    return (short)((joypadState >> 4) & 0x0F);
  }

  public static void setState(int code, boolean set) {
    // Util.log("Joypad.setState - " + code + " = " + set);

    if (set)
      joypadState = CPUMath.resetBit(joypadState, code);
    else
      joypadState = CPUMath.setBit(joypadState, code);
  }
}
