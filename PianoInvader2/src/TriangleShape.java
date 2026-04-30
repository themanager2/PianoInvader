import java.awt.*;

/**
 * TriangleShape: stationary triangular piano body
 */
public class TriangleShape extends GameObject {

    private Color bodyColor = new Color(60, 60, 60);
    private Color keyColor = Color.WHITE;
    private int keyCount = 14;

    public TriangleShape(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // ---- Draw triangle body ----
        Polygon triangle = new Polygon(
                new int[]{ x, x + w, x + w / 2 },
                new int[]{ y + h, y + h, y },
                3
        );

        g2.setColor(bodyColor);
        g2.fillPolygon(triangle);
        g2.setColor(Color.BLACK);
        g2.drawPolygon(triangle);

        // ---- Draw piano keys inside triangle ----
        drawKeys(g2);
    }

    private void drawKeys(Graphics2D g2) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        int baseY = y + h;
        int keyHeight = h / 3;
        int keyWidth = w / keyCount;

        int center = keyCount / 2;

        for (int i = 0; i < keyCount; i++) {
            int left = x + i * keyWidth;
            int right = left + keyWidth;

            double factor = 1.0 - (Math.abs(i - center) / (double) center);
            int topY = baseY - (int) (keyHeight * factor);

            Polygon key = new Polygon(
                    new int[]{ left, right, right, left },
                    new int[]{ baseY, baseY, topY, topY },
                    4
            );

            g2.setColor(i % 2 == 0 ? keyColor : new Color(200, 200, 200));
            g2.fillPolygon(key);
            g2.setColor(Color.BLACK);
            g2.drawPolygon(key);
        }
    }

    public int getKeyCount() {
        return keyCount;
    }
}