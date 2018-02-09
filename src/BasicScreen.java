
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.image.BufferedImage;

class BasicScreen implements Screen {

  private BufferedImage buffer;
  private JFrame frame;
  private JPanel panel;

  public BasicScreen() {
    this.buffer = new BufferedImage(PPU.VIDEO_WIDTH, PPU.VIDEO_HEIGHT, BufferedImage.TYPE_INT_RGB);
    this.frame = new JFrame("Gameboy");
    this.panel = createPanel(PPU.VIDEO_WIDTH, PPU.VIDEO_HEIGHT, buffer);
    this.panel.setVisible(true);
    this.panel.setPreferredSize(new Dimension(PPU.VIDEO_WIDTH, PPU.VIDEO_HEIGHT));
    this.frame.add(this.panel);
    this.frame.setVisible(true);
    this.frame.pack();
    this.frame.setResizable(false);
    this.frame.show();
  }

  public void setPixel(int x, int y, short color) {
    // Assign 'color' to red, green, and blue
    int rgb = color;
    rgb = (rgb << 8) + color;
    rgb = (rgb << 8) + color;

    x &= 0xFF;
    y &= 0xFF;

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
        g.drawRect((w - PPU.SCREEN_WIDTH) / 2,
          (h - PPU.SCREEN_HEIGHT) / 2,
          PPU.SCREEN_WIDTH,
          PPU.SCREEN_HEIGHT);
      }
    };
  }
}
