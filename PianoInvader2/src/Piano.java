import java.awt.Color;
import java.awt.Graphics;
import java.awt.geom.Path2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Piano: handles trapezoid key detection and firing shots.
 * Sound is handled elsewhere (on collision).
 */
public class Piano extends TriangleShape implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<KeyShot> shots;
    private final int panelWidth;
    private int speed;

    public Piano(int x, int y, int width, int height, int panelWidth) {
        super(x, y, width, height);
        this.panelWidth = panelWidth;
        this.speed = 6;
        this.shots = new ArrayList<>();
        setKeyCount(20);
    }

    @Override
    public void draw(Graphics g) {
        super.draw(g);

        g.setColor(Color.RED);
        for (KeyShot s : shots) {
            g.fillRect(s.getX(), s.getY(), s.getWidth(), s.getHeight());
        }
    }

    public void moveLeft() {
        setX(Math.max(0, getX() - speed));
    }

    public void moveRight() {
        setX(Math.min(panelWidth - getWidth(), getX() + speed));
    }

    /**
     * Handle mouse click on trapezoid keys.
     * Returns the key index clicked or -1 if none.
     * Does NOT play sound; only fires a shot.
     */
    public int handleMousePress(int mx, int my) {
        int keyIndex = detectKeyIndex(mx, my);
        if (keyIndex == -1) return -1;

        shootKey(keyIndex);
        return keyIndex;
    }

    /**
     * Detect which trapezoid key was clicked.
     */
    public int detectKeyIndex(int mx, int my) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int keyCount = getKeyCount();

        int keyW = w / keyCount;
        int keyH = h / 3;
        int topKeyY = y + h - keyH;
        int bottomY = y + h;

        int topX = x + w / 2;
        int topY = y;
        int bottomLeftX = x;
        int bottomRightX = x + w;

        double leftSlope = (double)(topX - bottomLeftX) / (topY - bottomY);
        double rightSlope = (double)(topX - bottomRightX) / (topY - bottomY);

        int topLeftX = (int)(topX + (topKeyY - topY) * leftSlope);
        int topRightX = (int)(topX + (topKeyY - topY) * rightSlope);

        for (int i = 0; i < keyCount; i++) {
            int leftX = x + i * keyW;
            int rightX = leftX + keyW;

            double t1 = (double)(leftX - bottomLeftX) / (bottomRightX - bottomLeftX);
            double t2 = (double)(rightX - bottomLeftX) / (bottomRightX - bottomLeftX);

            int kTopLeftX = (int)(topLeftX + t1 * (topRightX - topLeftX));
            int kTopRightX = (int)(topLeftX + t2 * (topRightX - topLeftX));

            Path2D poly = new Path2D.Double();
            poly.moveTo(kTopLeftX, topKeyY);
            poly.lineTo(kTopRightX, topKeyY);
            poly.lineTo(rightX, bottomY);
            poly.lineTo(leftX, bottomY);
            poly.closePath();

            if (poly.contains(mx, my)) return i;
        }

        return -1;
    }

    /**
     * Fire a shot from a specific key.
     */
    public void shootKey(int keyIndex) {
        int keyW = getWidth() / getKeyCount();
        int shotW = 6;
        int shotH = 12;

        int sx = getX() + keyIndex * keyW + keyW / 2 - shotW / 2;
        int sy = getY() - shotH;

        shots.add(new KeyShot(sx, sy, shotW, shotH, 8, keyIndex));
    }

    public void updateShots() {
        Iterator<KeyShot> it = shots.iterator();
        while (it.hasNext()) {
            KeyShot s = it.next();
            s.update();
            if (s.getY() + s.getHeight() < 0) it.remove();
        }
    }

    public List<KeyShot> getShots() {
        return shots;
    }

    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

    // Simple inner class representing a shot fired from a key
    public static class KeyShot {
        private int x, y, width, height, speed;
        private final int keyIndex;

        public KeyShot(int x, int y, int width, int height, int speed, int keyIndex) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.speed = speed;
            this.keyIndex = keyIndex;
        }

        public void update() { y -= speed; }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getKeyIndex() { return keyIndex; }
    }
}
