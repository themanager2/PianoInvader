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
        this.keyCount = 20; // now 20 keys
    }

    @Override
    public void draw(Graphics g) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // TRUE TRIANGLE (restored)
        int topX = x + w / 2;
        int topY = y;
        int bottomLeftX = x;
        int bottomLeftY = y + h;
        int bottomRightX = x + w;
        int bottomRightY = y + h;

        Polygon tri = new Polygon();
        tri.addPoint(topX, topY);
        tri.addPoint(bottomLeftX, bottomLeftY);
        tri.addPoint(bottomRightX, bottomRightY);

        g.setColor(bodyColor);
        g.fillPolygon(tri);

        // --- KEY DRAWING ---
        // Keys must fit inside the triangle edges.
        // We compute the left and right slanted edges as lines.

        double leftSlope = (double)(topX - bottomLeftX) / (topY - bottomLeftY);
        double rightSlope = (double)(topX - bottomRightX) / (topY - bottomRightY);

        int keyH = h / 3; // key height
        int keyW = w / keyCount;

        for (int i = 0; i < keyCount; i++) {
            int leftX = x + i * keyW;
            int rightX = leftX + keyW;

            // Compute top Y of keys
            int topKeyY = y + h - keyH;

            // Compute slanted top-left X based on triangle edge
            int topLeftX = (int)(topX + (topKeyY - topY) * leftSlope);
            int topRightX = (int)(topX + (topKeyY - topY) * rightSlope);

            // Interpolate key top positions between the two slanted edges
            double t1 = (double)(leftX - bottomLeftX) / (bottomRightX - bottomLeftX);
            double t2 = (double)(rightX - bottomLeftX) / (bottomRightX - bottomLeftX);

            int keyTopLeftX = (int)(topLeftX + t1 * (topRightX - topLeftX));
            int keyTopRightX = (int)(topLeftX + t2 * (topRightX - topLeftX));

            int bottomY = y + h;

            Polygon key = new Polygon();
            key.addPoint(keyTopLeftX, topKeyY);
            key.addPoint(keyTopRightX, topKeyY);
            key.addPoint(rightX, bottomY);
            key.addPoint(leftX, bottomY);

            g.setColor(keyColor);
            g.fillPolygon(key);

            g.setColor(Color.BLACK);
            g.drawPolygon(key);
        }
    }

    public Color getBodyColor() { return bodyColor; }
    public void setBodyColor(Color bodyColor) { this.bodyColor = bodyColor; }

    public Color getKeyColor() { return keyColor; }
    public void setKeyColor(Color keyColor) { this.keyColor = keyColor; }

    public int getKeyCount() { return keyCount; }
    public void setKeyCount(int keyCount) { this.keyCount = keyCount; }
}
