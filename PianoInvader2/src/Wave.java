import java.awt.*;

/**
 * Wave: moving target near the top of the screen
 */
public class Wave {

    private int y;
    private int offset = 0;
    private int direction = 1;

    private static final int AMPLITUDE = 30;
    private static final int STEP = 40;
    private static final int SPEED = 2;

    public Wave(int y) {
        this.y = y;
    }

    public void update() {
        offset += direction * SPEED;
        if (offset > 40 || offset < -40) {
            direction *= -1;
        }
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(3));
        g2.setColor(Color.BLACK);

        int lastX = offset;
        int lastY = y;

        for (int i = 0; i < 900 / STEP + 4; i++) {
            int x = offset + i * STEP;
            int nextY = (i % 2 == 0) ? y - AMPLITUDE : y + AMPLITUDE;
            g2.drawLine(lastX, lastY, x, nextY);
            lastX = x;
            lastY = nextY;
        }
    }

    public boolean hit(int shotX, int shotY) {
        return Math.abs(shotY - y) < 10;
    }
}
