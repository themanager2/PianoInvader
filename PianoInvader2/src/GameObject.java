import java.awt.Graphics;

/**
 * GameObject - Abstract base class for every visible, positionable entity
 * in the Piano Invader game.
 *
 * Design rationale
 * ────────────────
 * Using an abstract base class enforces a consistent interface for all
 * on-screen objects (Piano, TriangleShape, etc.) while letting each
 * subclass define its own rendering logic through the abstract draw()
 * method.  Storing position and size here avoids duplicating these
 * fundamental fields in every subclass.
 *
 * Coordinate system
 * ─────────────────
 * Follows the standard Java 2D / Swing convention:
 *   • (0, 0) is the top-left corner of the panel.
 *   • x increases rightward; y increases downward.
 *   • The bounding box of this object spans from (x, y) to
 *     (x + width, y + height).
 *
 * Inheritance hierarchy
 * ─────────────────────
 *   GameObject  (this class)
 *     └─ TriangleShape  – draws the triangular body with trapezoid keys
 *          └─ Piano      – adds shot management and movement logic
 */
public abstract class GameObject {

    /** Horizontal position of the object's top-left corner, in pixels. */
    private int x;

    /** Vertical position of the object's top-left corner, in pixels. */
    private int y;

    /** Total width of the object's bounding box, in pixels. */
    private int width;

    /** Total height of the object's bounding box, in pixels. */
    private int height;

    /**
     * Constructs a GameObject with the given position and dimensions.
     *
     * @param x      horizontal position of the top-left corner (pixels)
     * @param y      vertical position of the top-left corner (pixels)
     * @param width  bounding-box width (pixels)
     * @param height bounding-box height (pixels)
     */
    public GameObject(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Renders this object onto the provided Graphics context.
     *
     * Subclasses must implement this method to define their visual
     * appearance.  It is called every frame from GamePanel.paintComponent().
     *
     * @param g the Graphics context to draw on (provided by Swing's
     *          repaint/paint cycle)
     */
    public abstract void draw(Graphics g);

    // ── Getters and setters ──────────────────────────────────────────────
    // Private fields are exposed through accessors so that subclasses and
    // collaborators can update position without directly manipulating state.

    /** @return the x coordinate of the top-left corner */
    public int getX() { return x; }

    /** @param x new x coordinate of the top-left corner */
    public void setX(int x) { this.x = x; }

    /** @return the y coordinate of the top-left corner */
    public int getY() { return y; }

    /** @param y new y coordinate of the top-left corner */
    public void setY(int y) { this.y = y; }

    /** @return the width of the bounding box in pixels */
    public int getWidth() { return width; }

    /** @param width new bounding-box width in pixels */
    public void setWidth(int width) { this.width = width; }

    /** @return the height of the bounding box in pixels */
    public int getHeight() { return height; }

    /** @param height new bounding-box height in pixels */
    public void setHeight(int height) { this.height = height; }
}