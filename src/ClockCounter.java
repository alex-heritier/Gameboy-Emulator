
/**
 * M-cycle clock.
 *
 * <p>Anything that needs to advance in lockstep with the CPU (timers,
 * PPU dot clock, APU sample generator) registers a {@link TickListener}
 * so it can be stepped once per M-cycle. This lets us model
 * sub-instruction timing correctly - e.g. TIMA incrementing between
 * the opcode fetch and the memory access of an 8-bit load.
 */
class ClockCounter {

  interface TickListener {
    void tick();
  }

  private int time;
  private TickListener[] listeners = new TickListener[0];

  public void addListener(TickListener listener) {
    TickListener[] copy = new TickListener[listeners.length + 1];
    System.arraycopy(listeners, 0, copy, 0, listeners.length);
    copy[listeners.length] = listener;
    listeners = copy;
  }

  public void add(int amount) {
    for (int i = 0; i < amount; i++) {
      time++;
      for (int j = 0; j < listeners.length; j++) {
        listeners[j].tick();
      }
    }
  }

  public int count() { return time; }
}
