
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

class BasicScreen implements Screen {

  private static final int VRAM_SCALE = 2;
  private static final int JOYPAD_SCALE = 2;

  private PPU ppu;
  private BufferedImage buffer;
  private int[] bufferData;
  private JFrame frame;
  private JPanel gamePanel;
  private JPanel vramPanel;
  private JPanel joypadPanel;

  public BasicScreen(MMU mmu, PPU ppu) {
    this.ppu = ppu;
    this.buffer = new BufferedImage(PPU.SCALED_SCREEN_WIDTH, PPU.SCALED_SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
    this.bufferData = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();
    this.frame = new JFrame("Gameboy");
    this.gamePanel = createGamePanel(PPU.SCALED_SCREEN_WIDTH, PPU.SCALED_SCREEN_HEIGHT, buffer);
    this.gamePanel.setVisible(true);
    this.gamePanel.setPreferredSize(new Dimension(PPU.SCALED_SCREEN_WIDTH, PPU.SCALED_SCREEN_HEIGHT));
    this.vramPanel = createVRAMPanel(VRAM_SCALE * 8 * 16, VRAM_SCALE * 8 * 24, mmu);
    this.vramPanel.setVisible(true);
    this.vramPanel.setPreferredSize(new Dimension(PPU.SCALED_SCREEN_WIDTH/2, PPU.SCALED_SCREEN_HEIGHT));
    this.joypadPanel = createJoypadPanel(JOYPAD_SCALE * 8 * 16, JOYPAD_SCALE * 8 * 24, mmu);
    this.joypadPanel.setVisible(true);
    this.joypadPanel.setPreferredSize(new Dimension(PPU.SCALED_SCREEN_WIDTH/2, PPU.SCALED_SCREEN_HEIGHT));
    JPanel mainPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
    mainPanel.add(this.gamePanel);
    mainPanel.add(this.vramPanel);
    mainPanel.add(this.joypadPanel);
    mainPanel.setVisible(true);
    mainPanel.setPreferredSize(mainPanel.getPreferredSize());
    this.frame.add(mainPanel);
    this.frame.setVisible(true);
    this.frame.pack();
    this.frame.setResizable(false);
    this.frame.show();

    setupKeyListener();
  }

  public void setPixel(int x, int y, short color) {
    // Assign 'color' to red, green, and blue
    int rgb = color;
    rgb = (rgb << 8) + color;
    rgb = (rgb << 8) + color;

    x %= buffer.getWidth();
    y %= buffer.getHeight();

    // Util.log("X: " + x + "\tY: " + y + "\tColor: " + rgb);

    // buffer.setRGB(x, y, rgb);
    int index = y * PPU.SCALED_SCREEN_WIDTH + x;
    bufferData[index] = rgb;
  }

  public void draw() {
    frame.repaint();
  }

  private JPanel createGamePanel(int w, int h, BufferedImage buffer) {
    return new JPanel() {
      private int width = w;
      private int height = h;

      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.drawImage(buffer, 0, 0, this);
      }
    };
  }

  private JPanel createVRAMPanel(int w, int h, MMU _mmu) {
    return new JPanel() {
      private int width = w;
      private int height = h;
      private MMU mmu = _mmu;

      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Color grey = new Color(0xCC, 0xCC, 0xCC);
        g.setColor(grey);

        BufferedImage vramBuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] vramBufferData = ((DataBufferInt) vramBuffer.getRaster().getDataBuffer()).getData();

        for (int tile = 0x8000; tile < 0x9800; tile += 0x10) {
          short bgPalette = mmu.get(PPU.BG_PALETTE);

          for (int y = 0; y < 8; y++) {
            int tileLine = tile + 2 * y; // The tile's horizontal pixelY being drawn
            short tileLineByte1 = mmu.get(tileLine);  // Lower byte
            short tileLineByte2 = mmu.get(CPUMath.add16(tileLine, 1));  // Upper byte

            for (int x = 0; x < 8; x++) {

              int colorCode = PPU.getColorCode(tileLineByte1, tileLineByte2, 7 - x);
              int paletteColor = PPU.getPaletteColor(colorCode, bgPalette);
              short color = PPU.getColor(paletteColor);

              int rgb = color;
              rgb = (rgb << 8) + color;
              rgb = (rgb << 8) + color;

              int baseY = (tile - 0x8000) / (16 * 16) * 8 + y;
              int baseX = ((tile - 0x8000) / 16) % 16 * 8 + x;

              // Upscale the pixel according to scale factor
              for (int i = 0 ; i < VRAM_SCALE; i++) {
                for (int j = 0; j < VRAM_SCALE; j++) {

                  int drawX = VRAM_SCALE * baseX + j;
                  int drawY = VRAM_SCALE * baseY + i;
                  try {
                    // vramBuffer.setRGB(drawX, drawY, rgb);
                    int index = width * drawY + drawX;
                    vramBufferData[index] = rgb;
                  } catch (Exception e) {
                    Util.log("DRAW X - " + Util.hex(drawX));
                    Util.log("DRAW Y - " + Util.hex(drawY));
                    System.exit(0);
                  }
                }
              }
            }
          }
        }

        g.drawImage(vramBuffer, 0, 0, this);
      }
    };
  }

  private JPanel createJoypadPanel(int w, int h, MMU _mmu) {
    return new JPanel() {
      private int width = w;
      private int height = h;
      private MMU mmu = _mmu;

      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.drawString("A\t\t" + Joypad.getState(Joypad.A), 0, 12);
        g.drawString("B\t\t" + Joypad.getState(Joypad.B), 0, 24);
        g.drawString("Sel\t" + Joypad.getState(Joypad.SELECT), 0, 36);
        g.drawString("Str\t" + Joypad.getState(Joypad.START), 0, 48);
        g.drawString("LEFT\t" + Joypad.getState(Joypad.LEFT), 0, 60);
        g.drawString("RIGHT\t" + Joypad.getState(Joypad.RIGHT), 0, 72);
        g.drawString("UP\t" + Joypad.getState(Joypad.UP), 0, 84);
        g.drawString("DOWN\t" + Joypad.getState(Joypad.DOWN), 0, 96);
        g.drawString("PPU Mode\t" + ppu.getMode(), 0, 120);
      }
    };
  }

  private void setupKeyListener() {
    frame.addKeyListener(new KeyListener() {
      @Override
      public void keyPressed(KeyEvent e) {
        short key = keyCodeToJoypadCode(e);
        if (key != -1)
          Joypad.setState(key, Joypad.ON);
      }

      @Override
      public void keyReleased(KeyEvent e) {
        short key = keyCodeToJoypadCode(e);
        if (key != -1)
          Joypad.setState(key, Joypad.OFF);
      }

      @Override
      public void keyTyped(KeyEvent e) {}
    });
  }

  private short keyCodeToJoypadCode(KeyEvent e) {
    short key = -1;
    switch (e.getKeyCode()) {
      case KeyEvent.VK_LEFT:
        key = Joypad.LEFT;
        break;
      case KeyEvent.VK_RIGHT:
        key = Joypad.RIGHT;
        break;
      case KeyEvent.VK_UP:
        key = Joypad.UP;
        break;
      case KeyEvent.VK_DOWN:
        key = Joypad.DOWN;
        break;
      case KeyEvent.VK_Z:
        key = Joypad.A;
        break;
      case KeyEvent.VK_X:
        key = Joypad.B;
        break;
      case KeyEvent.VK_ENTER:
        key = Joypad.START;
        break;
      case KeyEvent.VK_SHIFT:
        key = Joypad.SELECT;
        break;
      default:
        // Util.log("BasicScreen.setupJoypad - Invalid key code " + e.getKeyCode());
        break;
    }
    return key;
  }
}
