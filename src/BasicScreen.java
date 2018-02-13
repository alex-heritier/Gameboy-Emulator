
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

class BasicScreen implements Screen {

  private BufferedImage buffer;
  private JFrame frame;
  private JPanel panel;
  private PPU.JoypadListener joypadListener;

  public BasicScreen(PPU.JoypadListener joypadListener) {
    this.buffer = new BufferedImage(PPU.SCALED_VIDEO_WIDTH, PPU.SCALED_VIDEO_HEIGHT, BufferedImage.TYPE_INT_RGB);
    this.frame = new JFrame("Gameboy");
    this.panel = createPanel(PPU.SCALED_VIDEO_WIDTH, PPU.SCALED_VIDEO_HEIGHT, buffer);
    this.panel.setVisible(true);
    this.panel.setPreferredSize(new Dimension(PPU.SCALED_VIDEO_WIDTH, PPU.SCALED_VIDEO_HEIGHT));
    this.frame.add(this.panel);
    this.frame.setVisible(true);
    this.frame.pack();
    this.frame.setResizable(false);
    this.frame.show();

    this.joypadListener = joypadListener;
    setupJoypad();
  }

  public void setPixel(int x, int y, short color) {
    // Assign 'color' to red, green, and blue
    int rgb = color;
    rgb = (rgb << 8) + color;
    rgb = (rgb << 8) + color;

    x %= buffer.getWidth();
    y %= buffer.getHeight();

    // Util.log("X: " + x + "\tY: " + y + "\tColor: " + rgb);

    buffer.setRGB(x, y, rgb);
  }

  public void draw() {
    frame.repaint();
  }

  public void clear() {

  }

  private JPanel createPanel(int w, int h, BufferedImage buffer) {
    return new JPanel() {
      private int width = w;
      private int height = h;

      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.drawImage(buffer, 0, 0, this);
        g.setColor(Color.BLACK);
        g.drawRect((w - PPU.SCALED_SCREEN_WIDTH) / 2,
          (h - PPU.SCALED_SCREEN_HEIGHT) / 2,
          PPU.SCALED_SCREEN_WIDTH,
          PPU.SCALED_SCREEN_HEIGHT);
      }
    };
  }

  private void setupJoypad() {
    frame.addKeyListener(new KeyListener() {
      @Override
      public void keyPressed(KeyEvent e) {
        int key = keyCodeToJoypadCode(e);
        joypadListener.onButtonPress(key);
      }

      @Override
      public void keyReleased(KeyEvent e) {
        int key = keyCodeToJoypadCode(e);
        joypadListener.onButtonRelease(key);
      }

      @Override
      public void keyTyped(KeyEvent e) {}
    });
  }

  private int keyCodeToJoypadCode(KeyEvent e) {
    int key = 0;
    switch (e.getKeyCode()) {
      case KeyEvent.VK_LEFT:
        key = PPU.JoypadListener.LEFT;
        break;
      case KeyEvent.VK_RIGHT:
        key = PPU.JoypadListener.RIGHT;
        break;
      case KeyEvent.VK_UP:
        key = PPU.JoypadListener.UP;
        break;
      case KeyEvent.VK_DOWN:
        key = PPU.JoypadListener.DOWN;
        break;
      case KeyEvent.VK_Z:
        key = PPU.JoypadListener.A;
        break;
      case KeyEvent.VK_X:
        key = PPU.JoypadListener.B;
        break;
      case KeyEvent.VK_ENTER:
        key = PPU.JoypadListener.START;
        break;
      case KeyEvent.VK_SHIFT:
        key = PPU.JoypadListener.SELECT;
        break;
      default:
        Util.log("BasicScreen.setupJoypad - Invalid key code " + e.getKeyCode());
        break;
    }
    return key;
  }
}
