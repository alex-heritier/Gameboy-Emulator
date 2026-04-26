
/**
 * DIV / TIMA timer unit.
 *
 * <p>The DMG timer is driven by an internal 16-bit counter that ticks
 * once every T-cycle. The {@code DIV} register is the upper 8 bits of
 * that counter (so it increments every 256 T-cycles / 64 M-cycles).
 * TIMA is incremented whenever a specific bit of this counter
 * transitions from 1 to 0 (the select depends on TAC, see Pan Docs).
 *
 * <p>Writing to {@code DIV} resets the full internal counter. Writing
 * to {@code TAC} can cause a phantom TIMA tick, which this
 * implementation models via the pre/post falling-edge check.
 */
class TimerHandler {

  public static final int DIV_ADDR = 0xFF04;
  public static final int TIMA_ADDR = 0xFF05;
  public static final int TMA_ADDR = 0xFF06;
  public static final int TAC_ADDR = 0xFF07;

  // Bit index into the internal counter that each TAC select watches.
  // In M-cycles (14-bit counter, since one M-cycle == 4 T-cycles):
  //   TAC=00 (4096 Hz)    -> bit 7
  //   TAC=01 (262144 Hz)  -> bit 1
  //   TAC=10 (65536 Hz)   -> bit 3
  //   TAC=11 (16384 Hz)   -> bit 5
  private static final int[] TAC_FALLING_EDGE_BITS = { 7, 1, 3, 5 };

  private final MMU mmu;

  private int internalCounter;
  private int tima;
  private int tma;
  private int tac;
  private boolean lastBit;
  private boolean reloading;

  public TimerHandler(MMU mmu, ClockCounter clockCounter) {
    this.mmu = mmu;
    clockCounter.addListener(this::step);
  }

  private void step() {
    if (reloading) {
      tima = tma & 0xFF;
      mmu.raiseInterrupt(MMU.TIMER);
      reloading = false;
    }

    internalCounter = (internalCounter + 1) & 0x3FFF;
    mmu.forceSet(DIV_ADDR, (short)((internalCounter >> 6) & 0xFF));

    boolean newBit = tacBit();
    if (lastBit && !newBit) incrementTima();
    lastBit = newBit;
  }

  private boolean tacBit() {
    boolean enabled = (tac & 0x04) != 0;
    if (!enabled) return false;
    int bit = TAC_FALLING_EDGE_BITS[tac & 0x3];
    return ((internalCounter >> bit) & 1) != 0;
  }

  private void incrementTima() {
    tima = (tima + 1) & 0xFF;
    if (tima == 0) {
      reloading = true;
    }
  }

  public short readDiv()  { return (short)((internalCounter >> 6) & 0xFF); }
  public short readTima() { return (short)(tima & 0xFF); }
  public short readTma()  { return (short)(tma & 0xFF); }
  public short readTac()  { return (short)(0xF8 | (tac & 0x07)); }

  public void writeDiv(short v) {
    internalCounter = 0;
    mmu.forceSet(DIV_ADDR, (short)0);
    boolean newBit = tacBit();
    if (lastBit && !newBit) incrementTima();
    lastBit = newBit;
  }

  public void writeTima(short v) {
    tima = v & 0xFF;
    reloading = false;
  }

  public void writeTma(short v) {
    tma = v & 0xFF;
  }

  public void writeTac(short v) {
    tac = v & 0x07;
    mmu.forceSet(TAC_ADDR, (short)(0xF8 | tac));
    boolean newBit = tacBit();
    if (lastBit && !newBit) incrementTima();
    lastBit = newBit;
  }
}
