import javax.swing.*;
import java.awt.*;

/**
 * Main: creates the window and contains GamePanel as a package-private class.
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Piano Invaders - Prototype");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            GamePanel panel = new GamePanel(800, 600);
            frame.setContentPane(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            panel.requestFocusInWindow();
            panel.start();
        });
    }
}

/* GamePanel: kept in Main.java so the project stays to five files.
   Handles input, game loop, painting, updates, and hit detection. */
class GamePanel extends JPanel {
    private final int width;
    private final int height;
    private final Piano piano;
    private final Wave wave;
    private final Timer timer;

    public GamePanel(int width, int height) {
        this.width = width;
        this.height = height;
        setPreferredSize(new Dimension(width, height));
        setFocusable(true);

        // Create game objects
        piano = new Piano(width / 2 - 40, height - 120, 80, 80, width);
        wave = new Wave(width, height / 3, 60.0, 0.02);

        // Keyboard movement only (left/right)
        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                int kc = e.getKeyCode();
                if (kc == java.awt.event.KeyEvent.VK_LEFT) piano.moveLeft();
                if (kc == java.awt.event.KeyEvent.VK_RIGHT) piano.moveRight();
            }
        });

        // Mouse clicks for piano keys
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                piano.handleMousePress(e.getX(), e.getY());
            }
        });

        // Game loop ~60 FPS
        timer = new Timer(16, ev -> gameLoop());
    }

    public void start() {
        timer.start();
        requestFocusInWindow();
    }

    private void gameLoop() {
        piano.updateShots();
        wave.update(1.5);

        // Collision detection
        java.util.Iterator<Piano.KeyShot> it = piano.getShots().iterator();
        while (it.hasNext()) {
            Piano.KeyShot s = it.next();
            int shotCenterX = s.getX() + s.getWidth() / 2;
            int waveY = wave.getY(shotCenterX);
            int shotY = s.getY();

            if (shotY <= waveY) {
                double threshold = Math.abs(wave.getAmplitude()) * 0.05;
                double diff = shotY - waveY;

                if (Math.abs(diff) <= threshold) {
                    System.out.println("HIT: SAME TONE at x=" + shotCenterX + " y=" + waveY);
                } else if (diff < -threshold) {
                    System.out.println("HIT: HIGHER TONE at x=" + shotCenterX + " y=" + waveY);
                } else {
                    System.out.println("HIT: LOWER TONE at x=" + shotCenterX + " y=" + waveY);
                }
                it.remove();
            }
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        wave.draw(g);
        piano.draw(g);
    }
}
