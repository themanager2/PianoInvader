import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Piano: stationary triangle that shoots notes upward
 */
public class Piano extends TriangleShape {

    private final List<KeyShot> shots = new ArrayList<>();

    public Piano(int panelWidth, int panelHeight) {
        // BIG triangle at bottom
        super(
                0,
                panelHeight - 250,   // bottom
                panelWidth,         // full width
                250                 // tall
        );
    }

    @Override
    public void draw(Graphics g) {
        super.draw(g);

        // Draw shots
        g.setColor(Color.RED);
        for (KeyShot s : shots) {
            g.fillRect(s.x, s.y, s.width, s.height);
        }
    }

    // ---- Shooting (no movement) ----
    public void shoot(int mouseX) {
        int keyWidth = getWidth() / getKeyCount();
        int keyIndex = mouseX / keyWidth;

        int shotW = 6;
        int shotH = 14;

        int sx = mouseX - shotW / 2;
        int sy = getY();

        shots.add(new KeyShot(sx, sy, shotW, shotH, 8));
    }

    public void updateShots() {
        Iterator<KeyShot> it = shots.iterator();
        while (it.hasNext()) {
            KeyShot s = it.next();
            s.y -= s.speed;
            if (s.y + s.height < 0) {
                it.remove();
            }
        }
    }

    public List<KeyShot> getShots() {
        return shots;
    }

    // ---- Shot class ----
    public static class KeyShot {
        int x, y, width, height, speed;

        KeyShot(int x, int y, int width, int height, int speed) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.speed = speed;
        }
    }
}