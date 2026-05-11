import java.awt.*;

/**
 * TriangleShape - Draws the triangular piano body with its fan of trapezoid keys.
 *
 * Visual layout
 * ─────────────
 * The shape is a true isosceles triangle with:
 *   apex     at (x + width/2, y)          — top-centre
 *   base-left at (x, y + height)          — bottom-left
 *   base-right at (x + width, y + height) — bottom-right
 *
 * The bottom third of the triangle's height is subdivided into {@code keyCount}
 * trapezoid "keys".  Each key has:
 *   top edge  — on the horizontal line y = (y + height − keyHeight)
 *                but only spanning between the two slanted triangle sides
 *   bottom edge — on the triangle's base, y = y + height
 *
 * This geometry creates the appearance of a triangular piano keyboard that
 * widens toward the base, matching the host triangle's silhouette.
 *
 * Key coordinate derivation
 * ─────────────────────────
 * For each key i (0-based, left to right):
 *   1. Divide the base into equal slots: leftX = x + i × keyW, rightX = leftX + keyW.
 *   2. Compute the x-extents of the triangle's left and right edges at the key-row's
 *      top-y using the pre-calculated left and right slopes (dx/dy along each edge).
 *   3. Linearly interpolate the top-left and top-right x-coordinates for this specific
 *      key between those extents, using the key's base fraction
 *      t = (keySlotX − bottomLeftX) / (bottomRightX − bottomLeftX).
 *   4. Build a Polygon with four corners and fill/outline it.
 *
 * Inheritance hierarchy
 * ─────────────────────
 *   GameObject  (abstract base: x, y, width, height + draw())
 *     └─ TriangleShape  (this class: triangle body + keys)
 *          └─ Piano       (adds movement, shot list, mouse detection)
 */
public class TriangleShape extends GameObject {

    /** Fill colour of the triangular body (default: dark grey). */
    private Color bodyColor;

    /**
     * Fill colour of the trapezoid keys (default: white).
     * A black outline (Color.BLACK) is always drawn over the fill.
     */
    private Color keyColor;

    /**
     * Number of keys drawn in the bottom third of the triangle.
     * Default is 20; can be changed via setKeyCount().
     */
    private int keyCount;

    /**
     * Constructs a TriangleShape with default colours and 20 keys.
     *
     * @param x      left edge of the bounding box (pixels)
     * @param y      top edge of the bounding box (pixels)
     * @param width  total width of the bounding box (pixels)
     * @param height total height of the bounding box (pixels)
     */
    public TriangleShape(int x, int y, int width, int height) {
        super(x, y, width, height);
        this.bodyColor = Color.DARK_GRAY;
        this.keyColor = Color.WHITE;
        this.keyCount = 20;
    }

    /**
     * Renders the triangle body and the trapezoid key row.
     *
     * Drawing order
     * ─────────────
     *  1. Filled triangle polygon (bodyColor) covering the full bounding box.
     *  2. For each key: a filled trapezoid (keyColor) followed by its black outline.
     *     Keys are drawn front-to-back so outline strokes are crisp.
     *
     * @param g the Graphics context from Swing's repaint pipeline
     */
    @Override
    public void draw(Graphics g) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // ── Step 1: Draw the triangular body ────────────────────────────
        // Apex at top-centre; base spans the full width at the bottom.
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

        // ── Step 2: Draw the trapezoid key row ───────────────────────────
        // Keys fit within the triangle: their top edges lie on the slanted sides.

        // Slopes of the triangle edges expressed as dx per dy (moving from base to apex).
        double leftSlope  = (double)(topX - bottomLeftX)  / (topY - bottomLeftY);
        double rightSlope = (double)(topX - bottomRightX) / (topY - bottomRightY);

        int keyH = h / 3;        // key row height = bottom third of the triangle
        int keyW = w / keyCount; // equal-width slots along the base

        for (int i = 0; i < keyCount; i++) {
            // Base-edge slot extents for this key.
            int leftX  = x + i * keyW;
            int rightX = leftX + keyW;

            // y-coordinate of the key row's top edge.
            int topKeyY = y + h - keyH;

            // x-coordinates of the triangle edges at topKeyY.
            // Moving from bottomY (base) upward by keyH pixels, the edge moves
            // leftSlope * keyH pixels inward on the left and rightSlope * keyH on the right.
            int topLeftX  = (int)(topX + (topKeyY - topY) * leftSlope);
            int topRightX = (int)(topX + (topKeyY - topY) * rightSlope);

            // Interpolation fractions: where does this key slot sit along the base?
            double t1 = (double)(leftX  - bottomLeftX) / (bottomRightX - bottomLeftX);
            double t2 = (double)(rightX - bottomLeftX) / (bottomRightX - bottomLeftX);

            // Interpolate top-edge x positions between the two slanted edge x values.
            int keyTopLeftX  = (int)(topLeftX + t1 * (topRightX - topLeftX));
            int keyTopRightX = (int)(topLeftX + t2 * (topRightX - topLeftX));

            int bottomY = y + h;

            // Construct the trapezoid: top edge (narrower) + bottom edge (wider).
            Polygon key = new Polygon();
            key.addPoint(keyTopLeftX, topKeyY);  // top-left
            key.addPoint(keyTopRightX, topKeyY); // top-right
            key.addPoint(rightX, bottomY);        // bottom-right
            key.addPoint(leftX, bottomY);         // bottom-left

            g.setColor(keyColor);
            g.fillPolygon(key);

            // Draw a thin black border to visually separate adjacent keys.
            g.setColor(Color.BLACK);
            g.drawPolygon(key);
        }
    }

    // ── Getters and setters ──────────────────────────────────────────────

    /** @return the fill colour of the triangular body */
    public Color getBodyColor() { return bodyColor; }

    /** @param bodyColor new fill colour for the triangle body */
    public void setBodyColor(Color bodyColor) { this.bodyColor = bodyColor; }

    /** @return the fill colour of the trapezoid keys */
    public Color getKeyColor() { return keyColor; }

    /** @param keyColor new fill colour for the keys */
    public void setKeyColor(Color keyColor) { this.keyColor = keyColor; }

    /** @return number of trapezoid keys drawn in the key row */
    public int getKeyCount() { return keyCount; }

    /** @param keyCount new number of keys; must be ≥ 1 to avoid division by zero */
    public void setKeyCount(int keyCount) { this.keyCount = keyCount; }
}
