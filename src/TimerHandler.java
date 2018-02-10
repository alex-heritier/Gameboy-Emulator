
class TimerHandler {

  public static final int DIV = 0xFF04; // incremented 4 times per clock
  public static final int TIMER_COUNTER = 0xFF05;
  public static final int TIMER_MODULO = 0xFF06;
  public static final int TIMER_CONTROL = 0xFF04;

  private MMU mmu;
  private ClockCounter clockCounter;

  private int lastClockCount;

  public TimerHandler(MMU mmu, ClockCounter clockCounter) {
    this.mmu = mmu;
    this.clockCounter = clockCounter;
    this.lastClockCount = clockCounter.count();
  }

  public void tick() {
    int delta = clockCounter.count() - lastClockCount;
    lastClockCount = clockCounter.count();

    if (!isTimerOn()) return;

    // Increment DIV 4 times for each clock that has passed since last tick()
    short currentDiv = mmu.get(DIV);
    mmu.set(DIV, (short)((currentDiv + delta * 4) & 0xFF));

    // Increment TIMER_COUNTER based on TIMER_CONTROL's clock rate
    short counterIncrement = getCounterIncrement();
    short currentCounter = mmu.get(TIMER_COUNTER);
    short newCounter = (short)((currentCounter + counterIncrement * delta) & 0xFF);

    // Check for overflow
    if (newCounter <= currentCounter) {
      short timerModulo = mmu.get(TIMER_MODULO);
      newCounter = CPUMath.add8(newCounter, timerModulo).get8();
      mmu.set(TIMER_COUNTER, newCounter);

      mmu.raiseInterrupt(2);
    }
  }

  private boolean isTimerOn() {
    short status = mmu.get(TIMER_CONTROL);
    return CPUMath.getBit(status, 2) > 0;
  }

  private short getCounterIncrement() {
    short clockSelect = mmu.get(TIMER_CONTROL);
    clockSelect = (short)(clockSelect & 3);

    short inc = 0;
    switch (clockSelect) {
      case 0: inc = 1; break;
      case 1: inc = 64; break;
      case 2: inc = 16; break;
      case 3: inc = 4; break;
      default:
        Util.errn("TimerHandler.getCounterIncrement - Bad timer control value.");
        break;
    }
    return inc;
  }
}
