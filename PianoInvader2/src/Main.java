import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Main entry point
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Piano Triangle Instrument");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            GamePanel panel = new GamePanel(900, 600);
            frame.setContentPane(panel);
            frame.pack();
            frame.setVisible(true);
        });
    }
}

/**
 * GamePanel: handles loop, input, drawing
 */
class GamePanel extends JPanel implements ActionListener, MouseListener {

    private final Piano piano;
    private final Wave wave;

    private final javax.swing.Timer timer;

    public GamePanel(int width, int height) {
        setPreferredSize(new Dimension(width, height));
        setBackground(new Color(245, 242, 235));

        // Big stationary piano at bottom
        piano = new Piano(width, height);

        // Wave near top
        wave = new Wave(120);

        addMouseListener(this);

        timer = new javax.swing.Timer(16, this);
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        wave.update();
        piano.updateShots();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        wave.draw(g);
        piano.draw(g);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getY() >= piano.getY()) {
            piano.shoot(e.getX());
        }
    }

    public void mouseReleased(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
}