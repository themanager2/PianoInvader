import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Piano: extends TriangleShape, manages movement and shooting key shots.
 * Encapsulation: private fields with getters/setters.
 */
public class Piano extends TriangleShape {
    private int speed;
    private final List<KeyShot> shots;
    private final int panelWidth;

    public Piano(int x, int y, int width, int height, int panelWidth) {
        super(x, y, width, height);
        this.speed = 6;
        this.shots = new ArrayList<>();
        this.panelWidth = panelWidth;
    }

    @Override
    public void draw(Graphics g) {
        super.draw(g);
        // draw shots
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

    public void shoot() {
        int shotW = 6;
        int shotH = 12;
        int sx = getX() + getWidth() / 2 - shotW / 2;
        int sy = getY() - shotH;
        shots.add(new KeyShot(sx, sy, shotW, shotH, 8));
    }

    public void updateShots() {
        Iterator<KeyShot> it = shots.iterator();
        while (it.hasNext()) {
            KeyShot s = it.next();
            s.update();
            if (s.getY() + s.getHeight() < 0) it.remove();
        }
    }

    // Encapsulation: getters/setters
    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

    public List<KeyShot> getShots() { return shots; }

    // Inner class representing a shot
    public static class KeyShot {
        private int x;
        private int y;
        private int width;
        private int height;
        private int speed;

        public KeyShot(int x, int y, int width, int height, int speed) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.speed = speed;
        }

        public void update() { y -= speed; }

        // Getters / Setters
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }

        public int getY() { return y; }
        public void setY(int y) { this.y = y; }

        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }

        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }

        public int getSpeed() { return speed; }
        public void setSpeed(int speed) { this.speed = speed; }
    }
}
