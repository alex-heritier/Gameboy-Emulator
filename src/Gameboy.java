
import java.io.Console;
import java.util.ArrayList;
import java.util.Collections;
import java.util.StringTokenizer;

class Gameboy {
  private CPUState state;
  private ClockCounter clockCounter;
  private CPU cpu;
  private PPU ppu;
  private MMU mmu;
  private TimerHandler timer;
  private Cart cart;

  // For debugging
  private ArrayList<Integer> breakpoints;
  private boolean step;

  public Gameboy(Cart cart) {
    this.cart = cart;
    this.state = new CPUState();
    this.clockCounter = new ClockCounter();
    this.ppu = new PPU(clockCounter);
    this.mmu = new MMU(ppu);
    this.ppu.setMMU(mmu);
    this.cpu = new CPU(state, mmu, cart, clockCounter);
    this.timer = new TimerHandler(mmu, clockCounter);

    breakpoints = new ArrayList<Integer>();
    breakpoints.add(0x0);
    step = false;
  }

  public void run() {

    // Util.debug = false;

    state.setPC(0x100);
    breakpoints.add(0x100);
    breakpoints.add(0x156);

    while (true) {
      int pc = state.PC();
      if (breakpoints.contains(pc)) debug_break();
      else if (step)                {cpu.dump(); prompt();}

      tick();
    }
  }

  private void tick() {
    timer.tick();
    cpu.tick();
    ppu.tick();
  }


  /*
   *  Debugger methods
   */
  private void debug_break() {
    Util.log("##########################");
    cpu.dump();
    prompt();
    Util.log("##########################");
  }

  private void prompt() {
    Console c = System.console();
    if (c != null) {
      boolean cnt = true;
      while (cnt) {
        c.format("> ");
        String input = c.readLine();
        cnt = interpret(input, c);
      }
    }
  }

  private boolean interpret(String input, Console c) {
    if (input == null) return false;

    boolean cnt = true;
    StringTokenizer st = new StringTokenizer(input, " ");

    // happens when user hits enter without typing anything
    if (!st.hasMoreTokens()) {
      return false;
    }

    String cmd = st.nextToken();
    switch (cmd) {
      case "":
        break;
      case "dump":
        cpu.dump();
        break;
      case "mem":
        mem(st);
        break;
      case "set":
        setBreakpoint(st);
        break;
      case "unset":
        unsetBreakpoint(st);
        break;
      case "stack":
        stack(st);
        break;
      case "step":
        step = true;
        break;
      case "run":
        step = false;
        cnt = false;
        break;
      case "timer":
        timer();
        break;
      default:
        cnt = false;
        break;
    }
    return cnt;
  }

  private void mem(StringTokenizer st) {
    if (!st.hasMoreTokens()) {
      mmu.dump();
      return;
    }

    int from = 0;
    int to = 0;
    try {
      from = Integer.decode("0x" + st.nextToken());
    } catch (Exception e) {
        Util.log("Usage: mem <16bit hex> <16bit hex>");
        return;
    }
    try {
      to = Integer.decode("0x" + st.nextToken());
    } catch (Exception e) {
      to = from;
    }

    Util.log("Memory " + Util.hex(from) + " - " + Util.hex(to));
    for (; from <= to; to--) {
      Util.log(Util.hex(to) + "\t" + Util.hex(mmu.get(to)));
    }
    Util.log();
  }

  private void setBreakpoint(StringTokenizer st) {
    if (!st.hasMoreTokens()) {
      Util.log("Breakpoints");
      Collections.sort(breakpoints);
      for (int i = 0; i < breakpoints.size(); i++) {
        Util.log(Util.hex(breakpoints.get(i)));
      }
      Util.log();
      return;
    }

    int breakpoint = 0;
    try {
      breakpoint = Integer.decode("0x" + st.nextToken());
    } catch (Exception e) {
      Util.log("Usage: set <16 bit hex>");
      return;
    }

    if (!breakpoints.contains(breakpoint))
      breakpoints.add(breakpoint);
  }

  private void unsetBreakpoint(StringTokenizer st) {
    if (!st.hasMoreTokens()) {
      breakpoints.clear();
      return;
    }

    int breakpoint = 0;
    try {
      breakpoint = Integer.decode("0x" + st.nextToken());
    } catch (Exception e) {
      Util.log("Usage: unset <16 bit hex>");
      return;
    }

    if (breakpoints.contains(breakpoint))
      breakpoints.remove(Integer.valueOf(breakpoint));
  }

  private void stack(StringTokenizer st) {
    int base = 0xFFFE;

    Util.log("Stack");
    for (int i = base; i > state.SP(); i--) {
      Util.log(Util.hex(i) + "\t" + Util.hex(mmu.get(i)));
    }
    Util.log();
  }

  private void timer() {
    short div = mmu.get(TimerHandler.DIV);
    short timerCounter = mmu.get(TimerHandler.TIMER_COUNTER);
    short timerModulo = mmu.get(TimerHandler.TIMER_MODULO);
    short timerControl = mmu.get(TimerHandler.TIMER_CONTROL);
    boolean timerStatus = CPUMath.getBit(timerControl, 2) > 0;

    Util.log("Timer");
    Util.log("DIV - " + Util.hex(div));
    Util.log("TIMA - " + Util.hex(timerCounter));
    Util.log("TMA - " + Util.hex(timerModulo));
    Util.log("Timer is " + (timerStatus ? "ON" : "OFF"));
    Util.log("Timer frequency - " + TimerHandler.getTimerIncrementInterval(timerControl));
  }
}
