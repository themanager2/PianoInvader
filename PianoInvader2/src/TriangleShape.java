import java.awt.*;

/**
 * TriangleShape: triangular piano body drawn on screen.
 * Inherits from GameObject.
 */
public class TriangleShape extends GameObject {
    private Color bodyColor;
    private Color keyColor;
    private int keyCount;

    public TriangleShape(int x, int y, int width, int height) {
        super(x, y, width, height);
        this.bodyColor = Color.DARK_GRAY;
        this.keyColor = Color.WHITE;
        this.keyCount = 7;
    }

    @Override
    public void draw(Graphics g) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        Polygon tri = new Polygon();
        tri.addPoint(x + w / 2, y);       // top
        tri.addPoint(x, y + h);           // bottom-left
        tri.addPoint(x + w, y + h);       // bottom-right

        g.setColor(bodyColor);
        g.fillPolygon(tri);

        // Draw simple keys at the base
        int keyW = Math.max(4, w / keyCount);
        int ky = y + h - (h / 6);
        for (int i = 0; i < keyCount; i++) {
            int kx = x + i * keyW;
            g.setColor(keyColor);
            g.fillRect(kx + 2, ky, keyW - 4, h / 6 - 2);
            g.setColor(Color.BLACK);
            g.drawRect(kx + 2, ky, keyW - 4, h / 6 - 2);
        }
    }

    // Encapsulation: getters/setters
    public Color getBodyColor() { return bodyColor; }
    public void setBodyColor(Color bodyColor) { this.bodyColor = bodyColor; }

    public Color getKeyColor() { return keyColor; }
    public void setKeyColor(Color keyColor) { this.keyColor = keyColor; }

    public int getKeyCount() { return keyCount; }
    public void setKeyCount(int keyCount) { this.keyCount = keyCount; }
}
