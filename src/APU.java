
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Audio Processing Unit.
 *
 * <p>Emulates the four DMG sound channels:
 *   1. Square with frequency sweep
 *   2. Square
 *   3. Programmable wave
 *   4. Noise (LFSR)
 * plus the master mixer (NR50/NR51/NR52).
 *
 * <p>The APU is ticked once per M-cycle. A frame sequencer runs at
 * 512 Hz (every 2048 M-cycles) and drives length, envelope, and sweep
 * units. Samples are produced at {@code SAMPLE_RATE} Hz by accumulating
 * the mixed output and pushing an averaged sample into a
 * {@link SourceDataLine} once per sample interval.
 *
 * <p>Audio may be disabled globally via {@link #setEnabled(boolean)}; in
 * that mode the APU still maintains register state (so games that read
 * back state behave correctly) but no samples are emitted.
 */
class APU {
  public static final int SAMPLE_RATE = 48000;
  public static final int CPU_CLOCK_M = 1_048_576; // CPU M-cycles per second

  // M-cycles between 512 Hz frame-sequencer ticks.
  private static final int FRAME_SEQUENCER_PERIOD = CPU_CLOCK_M / 512;

  private SourceDataLine line;
  private boolean audioEnabled;
  private boolean masterEnabled = true;
  private final byte[] buffer = new byte[1024];
  private int bufferIndex;

  // Sample timing in M-cycles.
  private final double cyclesPerSample = (double) CPU_CLOCK_M / SAMPLE_RATE;
  private double sampleCounter;

  private int frameSequencerCounter;
  private int frameStep;

  private final SquareChannel ch1 = new SquareChannel(true);
  private final SquareChannel ch2 = new SquareChannel(false);
  private final WaveChannel   ch3 = new WaveChannel();
  private final NoiseChannel  ch4 = new NoiseChannel();

  // NR50 / NR51 / NR52 mirror bytes.
  private int nr50 = 0x77;
  private int nr51 = 0xF3;

  public APU() { this(true); }

  public APU(boolean audioEnabled) {
    this.audioEnabled = audioEnabled;
    if (audioEnabled) tryOpenLine();
  }

  private void tryOpenLine() {
    try {
      AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 8, 2, true, false);
      DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
      line = (SourceDataLine) AudioSystem.getLine(info);
      line.open(fmt, 4096);
      line.start();
    } catch (LineUnavailableException e) {
      Util.errn("APU audio output unavailable: " + e.getMessage());
      line = null;
      audioEnabled = false;
    }
  }

  public void setEnabled(boolean enabled) {
    if (audioEnabled == enabled) return;
    audioEnabled = enabled;
    if (enabled && line == null) tryOpenLine();
    else if (!enabled && line != null) {
      line.flush();
    }
  }

  public boolean isEnabled() { return audioEnabled; }

  public void stop() {
    if (line != null) {
      line.drain();
      line.stop();
      line.close();
      line = null;
    }
  }

  public void tick(int mCycles) {
    for (int i = 0; i < mCycles; i++) step();
  }

  private void step() {
    if (masterEnabled) {
      ch1.step();
      ch2.step();
      ch3.step();
      ch4.step();

      if (++frameSequencerCounter >= FRAME_SEQUENCER_PERIOD) {
        frameSequencerCounter = 0;
        stepFrameSequencer();
      }
    }

    sampleCounter += 1.0;
    if (sampleCounter >= cyclesPerSample) {
      sampleCounter -= cyclesPerSample;
      emitSample();
    }
  }

  private void stepFrameSequencer() {
    switch (frameStep) {
      case 0: case 4:
        ch1.clockLength(); ch2.clockLength(); ch3.clockLength(); ch4.clockLength();
        break;
      case 2: case 6:
        ch1.clockLength(); ch2.clockLength(); ch3.clockLength(); ch4.clockLength();
        ch1.clockSweep();
        break;
      case 7:
        ch1.clockEnvelope(); ch2.clockEnvelope(); ch4.clockEnvelope();
        break;
    }
    frameStep = (frameStep + 1) & 7;
  }

  private void emitSample() {
    int left = 0, right = 0;

    if (masterEnabled) {
      int c1 = ch1.output();
      int c2 = ch2.output();
      int c3 = ch3.output();
      int c4 = ch4.output();

      if ((nr51 & 0x10) != 0) left  += c1;
      if ((nr51 & 0x20) != 0) left  += c2;
      if ((nr51 & 0x40) != 0) left  += c3;
      if ((nr51 & 0x80) != 0) left  += c4;
      if ((nr51 & 0x01) != 0) right += c1;
      if ((nr51 & 0x02) != 0) right += c2;
      if ((nr51 & 0x04) != 0) right += c3;
      if ((nr51 & 0x08) != 0) right += c4;

      int leftVol  = ((nr50 >> 4) & 0x7) + 1;
      int rightVol = (nr50 & 0x7) + 1;
      left  = (left  * leftVol)  / 8;
      right = (right * rightVol) / 8;
    }

    int signedLeft  = Math.max(-128, Math.min(127, left  * 4 - 64));
    int signedRight = Math.max(-128, Math.min(127, right * 4 - 64));

    if (!audioEnabled || line == null) return;

    buffer[bufferIndex++] = (byte)signedLeft;
    buffer[bufferIndex++] = (byte)signedRight;
    if (bufferIndex >= buffer.length) flushBuffer();
  }

  private void flushBuffer() {
    if (line == null || bufferIndex == 0) return;
    if (line.available() >= bufferIndex) {
      line.write(buffer, 0, bufferIndex);
    }
    bufferIndex = 0;
  }

  public short read(int address) {
    int offset = address & 0xFF;
    switch (offset) {
      case 0x10: return (short)(ch1.nr10() | 0x80);
      case 0x11: return (short)(ch1.nrX1() | 0x3F);
      case 0x12: return (short)ch1.nrX2();
      case 0x13: return (short)0xFF;
      case 0x14: return (short)(ch1.nrX4() | 0xBF);
      case 0x15: return (short)0xFF;
      case 0x16: return (short)(ch2.nrX1() | 0x3F);
      case 0x17: return (short)ch2.nrX2();
      case 0x18: return (short)0xFF;
      case 0x19: return (short)(ch2.nrX4() | 0xBF);
      case 0x1A: return (short)(ch3.nr30() | 0x7F);
      case 0x1B: return (short)0xFF;
      case 0x1C: return (short)(ch3.nr32() | 0x9F);
      case 0x1D: return (short)0xFF;
      case 0x1E: return (short)(ch3.nrX4() | 0xBF);
      case 0x1F: return (short)0xFF;
      case 0x20: return (short)0xFF;
      case 0x21: return (short)ch4.nrX2();
      case 0x22: return (short)ch4.nr43();
      case 0x23: return (short)(ch4.nrX4() | 0xBF);
      case 0x24: return (short)nr50;
      case 0x25: return (short)nr51;
      case 0x26: return (short)((masterEnabled ? 0x80 : 0) |
                                (ch4.isActive() ? 0x08 : 0) |
                                (ch3.isActive() ? 0x04 : 0) |
                                (ch2.isActive() ? 0x02 : 0) |
                                (ch1.isActive() ? 0x01 : 0) | 0x70);
      default:
        if (offset >= 0x30 && offset <= 0x3F) return (short)ch3.readWaveRam(offset - 0x30);
        return (short)0xFF;
    }
  }

  public void write(int address, short value) {
    int offset = address & 0xFF;
    int v = value & 0xFF;

    if (!masterEnabled && offset != 0x26 && !(offset >= 0x30 && offset <= 0x3F)) return;

    switch (offset) {
      case 0x10: ch1.setNr10(v); return;
      case 0x11: ch1.setNrX1(v); return;
      case 0x12: ch1.setNrX2(v); return;
      case 0x13: ch1.setNrX3(v); return;
      case 0x14: ch1.setNrX4(v); return;
      case 0x16: ch2.setNrX1(v); return;
      case 0x17: ch2.setNrX2(v); return;
      case 0x18: ch2.setNrX3(v); return;
      case 0x19: ch2.setNrX4(v); return;
      case 0x1A: ch3.setNr30(v); return;
      case 0x1B: ch3.setNr31(v); return;
      case 0x1C: ch3.setNr32(v); return;
      case 0x1D: ch3.setNrX3(v); return;
      case 0x1E: ch3.setNrX4(v); return;
      case 0x20: ch4.setNr41(v); return;
      case 0x21: ch4.setNrX2(v); return;
      case 0x22: ch4.setNr43(v); return;
      case 0x23: ch4.setNrX4(v); return;
      case 0x24: nr50 = v; return;
      case 0x25: nr51 = v; return;
      case 0x26:
        boolean newEnabled = (v & 0x80) != 0;
        if (!newEnabled && masterEnabled) { ch1.reset(); ch2.reset(); ch3.reset(); ch4.reset(); }
        masterEnabled = newEnabled;
        return;
      default:
        if (offset >= 0x30 && offset <= 0x3F) ch3.writeWaveRam(offset - 0x30, v);
        return;
    }
  }

  // --------------------------------------------------------------------
  // Square channels (NR10-NR14 and NR21-NR24).
  // --------------------------------------------------------------------
  private static final int[][] DUTY_TABLE = {
    {0,0,0,0,0,0,0,1},
    {1,0,0,0,0,0,0,1},
    {1,0,0,0,0,1,1,1},
    {0,1,1,1,1,1,1,0},
  };

  private static final class SquareChannel {
    private final boolean sweepEnabled;
    private int nr10, nrX1, nrX2, nrX3, nrX4;
    private int timer;
    private int dutyStep;
    private int lengthCounter;
    private int envelopeTimer;
    private int envelopeVolume;
    private int volume;
    private int sweepTimer;
    private int sweepShadow;
    private boolean sweepRunning;
    private boolean active;

    SquareChannel(boolean sweepEnabled) { this.sweepEnabled = sweepEnabled; }

    void reset() { nr10 = nrX1 = nrX2 = nrX3 = nrX4 = 0; active = false; lengthCounter = 0; }
    boolean isActive() { return active; }

    int nr10() { return nr10; }
    int nrX1() { return nrX1; }
    int nrX2() { return nrX2; }
    int nrX4() { return nrX4; }

    void setNr10(int v) { nr10 = v; }
    void setNrX1(int v) { nrX1 = v; lengthCounter = 64 - (v & 0x3F); }
    void setNrX2(int v) {
      nrX2 = v;
      if ((v & 0xF8) == 0) active = false;
    }
    void setNrX3(int v) { nrX3 = v; }
    void setNrX4(int v) {
      nrX4 = v;
      if ((v & 0x80) != 0) trigger();
    }

    private int frequency() { return ((nrX4 & 0x07) << 8) | (nrX3 & 0xFF); }
    private int duty() { return (nrX1 >> 6) & 0x3; }

    private void trigger() {
      active = (nrX2 & 0xF8) != 0;
      if (lengthCounter == 0) lengthCounter = 64;
      timer = (2048 - frequency());
      envelopeTimer = nrX2 & 0x07;
      if (envelopeTimer == 0) envelopeTimer = 8;
      envelopeVolume = (nrX2 >> 4) & 0x0F;
      volume = envelopeVolume;
      if (sweepEnabled) {
        sweepShadow = frequency();
        int period = (nr10 >> 4) & 0x07;
        sweepTimer = period == 0 ? 8 : period;
        sweepRunning = period != 0 || (nr10 & 0x07) != 0;
        if ((nr10 & 0x07) != 0) calcSweep(false);
      }
    }

    void step() {
      if (--timer <= 0) {
        timer = (2048 - frequency());
        dutyStep = (dutyStep + 1) & 7;
      }
    }

    void clockLength() {
      if ((nrX4 & 0x40) != 0 && lengthCounter > 0) {
        if (--lengthCounter == 0) active = false;
      }
    }

    void clockEnvelope() {
      if (--envelopeTimer <= 0) {
        int period = nrX2 & 0x07;
        envelopeTimer = period == 0 ? 8 : period;
        if (period != 0) {
          if ((nrX2 & 0x08) != 0 && volume < 15) volume++;
          else if ((nrX2 & 0x08) == 0 && volume > 0) volume--;
        }
      }
    }

    void clockSweep() {
      if (!sweepEnabled || !sweepRunning) return;
      if (--sweepTimer <= 0) {
        int period = (nr10 >> 4) & 0x07;
        sweepTimer = period == 0 ? 8 : period;
        if (period != 0) {
          int newFreq = calcSweep(true);
          if (newFreq < 2048 && (nr10 & 0x07) != 0) {
            sweepShadow = newFreq;
            nrX3 = newFreq & 0xFF;
            nrX4 = (nrX4 & 0xF8) | ((newFreq >> 8) & 0x07);
            calcSweep(false);
          }
        }
      }
    }

    private int calcSweep(boolean apply) {
      int shift = nr10 & 0x07;
      int delta = sweepShadow >> shift;
      if ((nr10 & 0x08) != 0) delta = -delta;
      int newFreq = sweepShadow + delta;
      if (newFreq >= 2048) active = false;
      return newFreq;
    }

    int output() {
      if (!active || (nrX2 & 0xF8) == 0) return 0;
      int bit = DUTY_TABLE[duty()][dutyStep];
      return bit * volume;
    }
  }

  // --------------------------------------------------------------------
  // Wave channel (NR30-NR34 + wave RAM 0xFF30-0xFF3F).
  // --------------------------------------------------------------------
  private static final class WaveChannel {
    private int nr30, nr31, nr32, nrX3, nrX4;
    private final int[] waveRam = new int[16];
    private int timer;
    private int position;
    private int lengthCounter;
    private boolean active;

    void reset() { nr30 = nr31 = nr32 = nrX3 = nrX4 = 0; active = false; lengthCounter = 0; }
    boolean isActive() { return active; }

    int nr30() { return nr30; }
    int nr32() { return nr32; }
    int nrX4() { return nrX4; }

    void setNr30(int v) { nr30 = v; if ((v & 0x80) == 0) active = false; }
    void setNr31(int v) { nr31 = v; lengthCounter = 256 - (v & 0xFF); }
    void setNr32(int v) { nr32 = v; }
    void setNrX3(int v) { nrX3 = v; }
    void setNrX4(int v) {
      nrX4 = v;
      if ((v & 0x80) != 0) trigger();
    }

    int readWaveRam(int i) { return waveRam[i & 0xF] & 0xFF; }
    void writeWaveRam(int i, int v) { waveRam[i & 0xF] = v & 0xFF; }

    private int frequency() { return ((nrX4 & 0x07) << 8) | (nrX3 & 0xFF); }

    private void trigger() {
      active = (nr30 & 0x80) != 0;
      if (lengthCounter == 0) lengthCounter = 256;
      timer = (2048 - frequency()) * 2;
      position = 0;
    }

    void step() {
      if (!active) return;
      if (--timer <= 0) {
        timer = (2048 - frequency()) * 2;
        position = (position + 1) & 0x1F;
      }
    }

    void clockLength() {
      if ((nrX4 & 0x40) != 0 && lengthCounter > 0) {
        if (--lengthCounter == 0) active = false;
      }
    }

    int output() {
      if (!active || (nr30 & 0x80) == 0) return 0;
      int byteIndex = position >> 1;
      int sample = (position & 1) == 0 ? ((waveRam[byteIndex] >> 4) & 0xF)
                                        : (waveRam[byteIndex] & 0xF);
      int volumeShift;
      switch ((nr32 >> 5) & 0x3) {
        case 1:  volumeShift = 0; break;
        case 2:  volumeShift = 1; break;
        case 3:  volumeShift = 2; break;
        default: return 0;
      }
      return sample >> volumeShift;
    }
  }

  // --------------------------------------------------------------------
  // Noise channel (NR41-NR44).
  // --------------------------------------------------------------------
  private static final class NoiseChannel {
    private int nr41, nrX2, nr43, nrX4;
    private int timer;
    private int lfsr = 0x7FFF;
    private int lengthCounter;
    private int envelopeTimer;
    private int volume;
    private boolean active;

    void reset() { nr41 = nrX2 = nr43 = nrX4 = 0; active = false; lengthCounter = 0; }
    boolean isActive() { return active; }

    int nrX2() { return nrX2; }
    int nr43() { return nr43; }
    int nrX4() { return nrX4; }

    void setNr41(int v) { nr41 = v; lengthCounter = 64 - (v & 0x3F); }
    void setNrX2(int v) {
      nrX2 = v;
      if ((v & 0xF8) == 0) active = false;
    }
    void setNr43(int v) { nr43 = v; }
    void setNrX4(int v) {
      nrX4 = v;
      if ((v & 0x80) != 0) trigger();
    }

    private void trigger() {
      active = (nrX2 & 0xF8) != 0;
      if (lengthCounter == 0) lengthCounter = 64;
      timer = periodFromNr43();
      envelopeTimer = nrX2 & 0x07;
      if (envelopeTimer == 0) envelopeTimer = 8;
      volume = (nrX2 >> 4) & 0x0F;
      lfsr = 0x7FFF;
    }

    private int periodFromNr43() {
      int ratio = nr43 & 0x07;
      int shift = (nr43 >> 4) & 0x0F;
      int base = ratio == 0 ? 8 : (ratio << 4);
      return base << shift;
    }

    void step() {
      if (!active) return;
      if (--timer <= 0) {
        timer = periodFromNr43();
        int bit = ((lfsr ^ (lfsr >> 1)) & 1);
        lfsr >>= 1;
        lfsr |= bit << 14;
        if ((nr43 & 0x08) != 0) {
          lfsr &= ~(1 << 6);
          lfsr |= bit << 6;
        }
      }
    }

    void clockLength() {
      if ((nrX4 & 0x40) != 0 && lengthCounter > 0) {
        if (--lengthCounter == 0) active = false;
      }
    }

    void clockEnvelope() {
      if (--envelopeTimer <= 0) {
        int period = nrX2 & 0x07;
        envelopeTimer = period == 0 ? 8 : period;
        if (period != 0) {
          if ((nrX2 & 0x08) != 0 && volume < 15) volume++;
          else if ((nrX2 & 0x08) == 0 && volume > 0) volume--;
        }
      }
    }

    int output() {
      if (!active || (nrX2 & 0xF8) == 0) return 0;
      return ((~lfsr) & 1) * volume;
    }
  }
}
