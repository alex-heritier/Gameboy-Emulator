
class ClockCounter {

  private int time;

  public ClockCounter() {
    time = 0;
  }

  public void add(int amount) {
    time += amount;
  }

  public int count() {
    return time;
  }
}
