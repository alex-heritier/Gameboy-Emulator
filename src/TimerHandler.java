
class TimerHandler {

  public static final int DIV = 0xFF04; // incremented 4 times per clock
  public static final int TIMER_COUNTER = 0xFF05;
  public static final int TIMER_MODULO = 0xFF06;
  public static final int TIMER_CONTROL = 0xFF07;

  public static final int DIV_INC_INTERVAL = 256;

  private MMU mmu;
  private ClockCounter clockCounter;

  private int lastClockCount; // The total clock count from last tick()
  private int clocksSinceLastDiv; // Clocks since DIV was last icremented
  private int clocksSinceLastIncrement;   // Clock counts since last timer increment


  public TimerHandler(MMU mmu, ClockCounter clockCounter) {
    this.mmu = mmu;
    this.clockCounter = clockCounter;
    this.lastClockCount = clockCounter.count();
  }

  public void tick() {
    int delta = clockCounter.count() - lastClockCount;
    lastClockCount = clockCounter.count();

    clocksSinceLastDiv += delta;
    clocksSinceLastIncrement += delta;

    if (!isTimerOn()) return;

    // Util.log("Clocks since last DIV - " + Util.hex(clocksSinceLastDiv));
    // Util.log("Clocks since last increment - " + Util.hex(clocksSinceLastIncrement));

    // Increment DIV at the rate of (4.19 megahertz) / (16 384 hertz)
    if (DIV_INC_INTERVAL <= clocksSinceLastDiv) {
      clocksSinceLastDiv = clocksSinceLastDiv - DIV_INC_INTERVAL;

      short currentDiv = mmu.get(DIV);
      mmu.forceSet(DIV, (short)((currentDiv + 1) & 0xFF));
    }

    // Increment TIMER_COUNTER based on TIMER_CONTROL's clock rate
    short timerClockRate = getTimerIncrementInterval();
    short currentTimerCounter = mmu.get(TIMER_COUNTER);
    short newTimerCounter = currentTimerCounter;

    // Check if it's time to increment timer
    if (timerClockRate <= clocksSinceLastIncrement) {
      clocksSinceLastIncrement = clocksSinceLastIncrement - timerClockRate;
      newTimerCounter = (short)((currentTimerCounter + 1) & 0xFF);

      // Check for overflow
      if (newTimerCounter == 0) {
        short timerModulo = mmu.get(TIMER_MODULO);
        newTimerCounter = timerModulo;

        mmu.raiseInterrupt(2);
      }

      mmu.set(TIMER_COUNTER, newTimerCounter);
    }
  }

  private boolean isTimerOn() {
    short status = mmu.get(TIMER_CONTROL);
    return CPUMath.getBit(status, 2) > 0;
  }

  // Returns the number of clocks between increments
  public short getTimerIncrementInterval() {
    short clockSelect = mmu.get(TIMER_CONTROL);
    return getTimerIncrementInterval(clockSelect);
  }
  public static short getTimerIncrementInterval(short clockSelect) {
    clockSelect = (short)(clockSelect & 3);

    short incInterval = 0;
    switch (clockSelect) {
      case 0: incInterval = 1023; break;  // (4.19 megahertz) / (4096 hertz)
      case 1: incInterval = 16; break;    // (4.19 megahertz) / (262 144 hertz)
      case 2: incInterval = 64; break;    // (4.19 megahertz) / (65 536 hertz)
      case 3: incInterval = 256; break;   // (4.19 megahertz) / (16 384 hertz)
      default:
        Util.errn("TimerHandler.getTimerIncrementInterval - Bad timer control value.");
        break;
    }
    return incInterval;
  }
}
