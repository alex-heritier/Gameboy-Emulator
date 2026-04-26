
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Swing-based LCD output. Hosts a key listener that drives the {@link
 * Joypad}, shows the cart title, and exposes a single toggle to turn
 * the {@link APU} on/off (the "M" key).
 *
 * <p>The frame is set up so the game panel is focusable on load - this
 * was the root cause of "Dr. Mario only registers input if you press a
 * key immediately": the game panel never received focus if the user
 * touched anything else first.
 */
class BasicScreen implements Screen {

  private final BufferedImage buffer;
  private final int[] bufferData;
  private final JFrame frame;
  private final JPanel gamePanel;
  private final JLabel status;
  private final Joypad joypad;
  private final APU apu;
  private final String title;

  public BasicScreen(Joypad joypad, APU apu, String title) {
    this.joypad = joypad;
    this.apu = apu;
    this.title = title;
    this.buffer = new BufferedImage(
        PPU.SCALED_SCREEN_WIDTH, PPU.SCALED_SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
    this.bufferData = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();

    this.frame = new JFrame("Game Boy - " + title);
    this.gamePanel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(buffer, 0, 0, this);
      }
    };
    gamePanel.setPreferredSize(new Dimension(PPU.SCALED_SCREEN_WIDTH, PPU.SCALED_SCREEN_HEIGHT));
    gamePanel.setBackground(Color.BLACK);
    gamePanel.setFocusable(true);
    gamePanel.addKeyListener(new GameKeyListener());

    this.status = new JLabel("WASD/Arrows=D-Pad  J/Z=A  K/X=B  Enter=Start  Shift=Select  M=Mute");
    this.status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
    this.status.setOpaque(true);
    this.status.setBackground(new Color(240, 240, 240));
    this.status.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));

    frame.setLayout(new BorderLayout());
    frame.add(gamePanel, BorderLayout.CENTER);
    frame.add(status, BorderLayout.SOUTH);
    frame.setResizable(false);
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent e) { gamePanel.requestFocusInWindow(); }
      @Override
      public void windowOpened(WindowEvent e)    { gamePanel.requestFocusInWindow(); }
    });
    frame.setVisible(true);
    SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);

    Timer redraw = new Timer(1000 / 60, new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) { frame.repaint(); }
    });
    redraw.start();
  }

  @Override
  public void setPixel(int x, int y, short color) {
    int c = color & 0xFF;
    int rgb = (c << 16) | (c << 8) | c;
    if (x < 0 || x >= PPU.SCALED_SCREEN_WIDTH) return;
    if (y < 0 || y >= PPU.SCALED_SCREEN_HEIGHT) return;
    bufferData[y * PPU.SCALED_SCREEN_WIDTH + x] = rgb;
  }

  @Override
  public void draw() { /* vsync handled by the 60 Hz repaint timer */ }

  private final class GameKeyListener extends KeyAdapter {
    @Override
    public void keyPressed(KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_M) {
        apu.setEnabled(!apu.isEnabled());
        status.setText("Audio: " + (apu.isEnabled() ? "ON" : "OFF"));
        return;
      }
      Joypad.Button btn = map(e.getKeyCode());
      if (btn != null) joypad.set(btn, true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
      Joypad.Button btn = map(e.getKeyCode());
      if (btn != null) joypad.set(btn, false);
    }
  }

  private static Joypad.Button map(int keyCode) {
    switch (keyCode) {
      case KeyEvent.VK_LEFT:  return Joypad.Button.LEFT;
      case KeyEvent.VK_RIGHT: return Joypad.Button.RIGHT;
      case KeyEvent.VK_UP:    return Joypad.Button.UP;
      case KeyEvent.VK_DOWN:  return Joypad.Button.DOWN;
      case KeyEvent.VK_A:     return Joypad.Button.LEFT;
      case KeyEvent.VK_D:     return Joypad.Button.RIGHT;
      case KeyEvent.VK_W:     return Joypad.Button.UP;
      case KeyEvent.VK_S:     return Joypad.Button.DOWN;
      case KeyEvent.VK_Z:     return Joypad.Button.A;
      case KeyEvent.VK_J:     return Joypad.Button.A;
      case KeyEvent.VK_X:     return Joypad.Button.B;
      case KeyEvent.VK_K:     return Joypad.Button.B;
      case KeyEvent.VK_ENTER: return Joypad.Button.START;
      case KeyEvent.VK_SHIFT: return Joypad.Button.SELECT;
      default: return null;
    }
  }
}
