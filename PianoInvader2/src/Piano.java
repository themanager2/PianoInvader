import java.awt.Color;
import java.awt.Graphics;
import java.awt.geom.Path2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Piano - The player-controlled entity: a triangular piano ship that fires
 * vertical shots at the scrolling sine wave.
 *
 * Inheritance
 * ───────────
 * Piano extends TriangleShape (which extends GameObject), gaining the
 * triangular body rendering and the 20-key visual layout.  Piano adds:
 *   • Horizontal movement (moveLeft / moveRight), clamped to panel bounds.
 *   • A list of active KeyShot projectiles.
 *   • Mouse-click key detection via trapezoid hit-testing.
 *   • Shot lifecycle management (creation, upward movement, off-screen pruning).
 *
 * Key geometry
 * ────────────
 * The piano is a triangle (apex at top-centre, base along the bottom).
 * The bottom third of the triangle's height is divided into 20 trapezoid
 * "keys".  Each key's top edge lies on the slanted triangle sides; its
 * bottom edge lies on the triangle's base.  This produces a fan of keys
 * that widens toward the base.
 *
 * Trapezoid hit-testing
 * ────────────────────
 * detectKeyIndex() builds a Path2D.Double for each key and calls
 * Path2D.contains(x, y), which correctly handles non-rectangular polygons.
 * This is more accurate than a simple rectangular bounding-box test.
 *
 * Serializable
 * ────────────
 * Implements Serializable (serialVersionUID = 1L) to support potential future
 * save/load functionality without breaking existing serialised states.
 */
public class Piano extends TriangleShape implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The list of in-flight shots fired from the piano's keys.
     * Shots are added in handleMousePress() / shootKey() and removed in
     * updateShots() once they leave the top of the panel, or removed by
     * GamePanel when they collide with the wave.
     */
    private final List<KeyShot> shots;

    /** Panel width used to clamp the piano's horizontal position. */
    private final int panelWidth;

    /**
     * Pixels moved per arrow-key press.
     * A value of 6 feels responsive without skipping over narrow targets.
     */
    private int speed;

    /**
     * Constructs a Piano at the given position and size.
     *
     * @param x          left edge of the piano's bounding box (pixels)
     * @param y          top edge of the piano's bounding box (pixels)
     * @param width      total width of the piano (pixels)
     * @param height     total height of the piano (pixels)
     * @param panelWidth panel width used to clamp rightward movement
     */
    public Piano(int x, int y, int width, int height, int panelWidth) {
        super(x, y, width, height);
        this.panelWidth = panelWidth;
        this.speed = 6;
        this.shots = new ArrayList<>();
        setKeyCount(20);  // override TriangleShape default if different
    }

    /**
     * Renders the piano body, keys, and all in-flight shots.
     *
     * Delegates body and key rendering to TriangleShape.draw(), then
     * iterates the shot list and draws each shot as a small red rectangle.
     * Shots are drawn after the piano body so they appear on top.
     *
     * @param g the Graphics context from the Swing paint pipeline
     */
    @Override
    public void draw(Graphics g) {
        super.draw(g);  // draw triangle body and trapezoid keys

        // Draw each active shot as a small red rectangle.
        g.setColor(Color.RED);
        for (KeyShot s : shots) {
            g.fillRect(s.getX(), s.getY(), s.getWidth(), s.getHeight());
        }
    }

    /**
     * Moves the piano left by {@code speed} pixels, clamping at the left edge.
     *
     * The minimum x is 0 (left edge of the panel).
     */
    public void moveLeft() {
        setX(Math.max(0, getX() - speed));
    }

    /**
     * Moves the piano right by {@code speed} pixels, clamping at the right edge.
     *
     * The maximum x ensures the piano's right edge never exceeds panelWidth.
     */
    public void moveRight() {
        setX(Math.min(panelWidth - getWidth(), getX() + speed));
    }

    /**
     * Responds to a mouse press by detecting the clicked key and firing a shot.
     *
     * Sound playback is intentionally NOT triggered here; it is triggered
     * later by GamePanel when the shot collides with the wave.
     *
     * @param mx mouse x-coordinate in panel pixels
     * @param my mouse y-coordinate in panel pixels
     * @return the 0-based index of the key that was clicked, or -1 if the
     *         click did not land on any key
     */
    public int handleMousePress(int mx, int my) {
        int keyIndex = detectKeyIndex(mx, my);
        if (keyIndex == -1) return -1;  // click was outside all keys

        shootKey(keyIndex);
        return keyIndex;
    }

    /**
     * Identifies which trapezoid key (if any) was clicked.
     *
     * Algorithm
     * ─────────
     * The key row occupies the bottom third of the piano's bounding box.
     * For each of the 20 keys:
     *  1. Divide the bottom edge into 20 equal slots.
     *  2. Project the top-left and top-right corners of the slot onto the
     *     two slanted triangle edges (left slope, right slope) to find the
     *     key's top vertices.
     *  3. Build a Path2D.Double quadrilateral (trapezoid) from the four
     *     computed vertices.
     *  4. Use Path2D.contains(mx, my) for precise polygon hit-testing.
     *
     * The triangle's left and right slopes are computed as:
     *   leftSlope  = (topX − bottomLeftX)  / (topY − bottomY)   [dx/dy]
     *   rightSlope = (topX − bottomRightX) / (topY − bottomY)
     *
     * Because Swing's y-axis points downward, topY < bottomY, so the
     * denominators are negative and the slopes produce correct leftward /
     * rightward offsets.
     *
     * @param mx mouse x-coordinate in panel pixels
     * @param my mouse y-coordinate in panel pixels
     * @return 0-based key index (0 = leftmost) or -1 if no key was hit
     */
    public int detectKeyIndex(int mx, int my) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int keyCount = getKeyCount();

        int keyW = w / keyCount;          // width of each key slot at the base
        int keyH = h / 3;                 // key row height = bottom third of triangle
        int topKeyY = y + h - keyH;       // y of the top edge of the key row
        int bottomY = y + h;              // y of the triangle's base

        // Triangle apex and base corners.
        int topX = x + w / 2;
        int topY = y;
        int bottomLeftX = x;
        int bottomRightX = x + w;

        // Slopes of the left and right triangle edges (dx per dy, going upward).
        double leftSlope  = (double)(topX - bottomLeftX)  / (topY - bottomY);
        double rightSlope = (double)(topX - bottomRightX) / (topY - bottomY);

        // x-coordinates of the left and right triangle edges at topKeyY.
        int topLeftX  = (int)(topX + (topKeyY - topY) * leftSlope);
        int topRightX = (int)(topX + (topKeyY - topY) * rightSlope);

        for (int i = 0; i < keyCount; i++) {
            // Base-edge slot for this key.
            int leftX  = x + i * keyW;
            int rightX = leftX + keyW;

            // Interpolation parameters map this slot to the top-edge coordinate.
            double t1 = (double)(leftX  - bottomLeftX) / (bottomRightX - bottomLeftX);
            double t2 = (double)(rightX - bottomLeftX) / (bottomRightX - bottomLeftX);

            // Top-edge x-coordinates for this key (interpolated between topLeftX and topRightX).
            int kTopLeftX  = (int)(topLeftX + t1 * (topRightX - topLeftX));
            int kTopRightX = (int)(topLeftX + t2 * (topRightX - topLeftX));

            // Build the trapezoid path and test the click point.
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
     * Creates and adds a new KeyShot for the specified key.
     *
     * The shot spawns horizontally centred on the key and just above the
     * piano's top edge (sy = y − shotHeight), so it immediately appears to
     * emerge from the key.
     *
     * Shot dimensions: 6 × 12 pixels; speed: 8 pixels/frame upward.
     *
     * @param keyIndex the 0-based index of the key to shoot from
     */
    public void shootKey(int keyIndex) {
        int keyW = getWidth() / getKeyCount();
        int shotW = 6;
        int shotH = 12;

        // Horizontally centre the shot within the key's bottom-edge slot.
        int sx = getX() + keyIndex * keyW + keyW / 2 - shotW / 2;
        int sy = getY() - shotH;  // spawn just above the piano's top edge

        shots.add(new KeyShot(sx, sy, shotW, shotH, 8, keyIndex));
    }

    /**
     * Advances all active shots upward and removes those that have exited
     * the top of the panel.
     *
     * Each shot moves by its own speed (8 px/frame).  A shot is considered
     * off-screen when its bottom edge (y + height) is above the panel top
     * (y < 0).
     *
     * Uses an Iterator to safely remove elements while iterating.
     */
    public void updateShots() {
        Iterator<KeyShot> it = shots.iterator();
        while (it.hasNext()) {
            KeyShot s = it.next();
            s.update();                            // move shot upward by its speed
            if (s.getY() + s.getHeight() < 0) {   // completely above the panel
                it.remove();
            }
        }
    }

    /**
     * Returns the live list of active shots.
     *
     * GamePanel iterates this list each frame to detect wave collisions.  The
     * caller must not modify the list directly; use shootKey() to add and
     * updateShots() or GamePanel's removeShotFromPiano() to remove.
     *
     * @return the mutable list of KeyShot instances currently in flight
     */
    public List<KeyShot> getShots() {
        return shots;
    }

    /** @return horizontal speed in pixels per key-press */
    public int getSpeed() { return speed; }

    /** @param speed new horizontal speed in pixels per key-press */
    public void setSpeed(int speed) { this.speed = speed; }

    /**
     * KeyShot - A projectile fired from a single piano key.
     *
     * Each shot travels straight upward at a fixed speed until it either
     * collides with the wave (removed by GamePanel) or exits the top of the
     * panel (removed by Piano.updateShots()).
     *
     * Fields are package-private via accessors; the inner class is public
     * so GamePanel can read shot positions for collision detection without
     * needing reflection or additional accessors on Piano.
     */
    public static class KeyShot {
        /** Current horizontal position of the shot's left edge (pixels). */
        private int x;

        /** Current vertical position of the shot's top edge (pixels). */
        private int y;

        /** Width of the shot rectangle (pixels). */
        private int width;

        /** Height of the shot rectangle (pixels). */
        private int height;

        /** Pixels moved upward per frame (positive = upward in screen space). */
        private int speed;

        /**
         * Index of the piano key that fired this shot (0-based).
         * Used by GamePanel to identify the shot for removal after a hit.
         */
        private final int keyIndex;

        /**
         * Constructs a KeyShot at the given position, size, and speed.
         *
         * @param x        initial left-edge x position (pixels)
         * @param y        initial top-edge y position (pixels)
         * @param width    shot width (pixels)
         * @param height   shot height (pixels)
         * @param speed    upward movement per frame (pixels)
         * @param keyIndex index of the firing key (0-based)
         */
        public KeyShot(int x, int y, int width, int height, int speed, int keyIndex) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.speed = speed;
            this.keyIndex = keyIndex;
        }

        /**
         * Moves the shot upward by {@code speed} pixels.
         * Called once per frame from Piano.updateShots().
         */
        public void update() { y -= speed; }

        /** @return current x position of the shot's left edge */
        public int getX() { return x; }

        /** @return current y position of the shot's top edge */
        public int getY() { return y; }

        /** @return width of the shot in pixels */
        public int getWidth() { return width; }

        /** @return height of the shot in pixels */
        public int getHeight() { return height; }

        /** @return the 0-based index of the key that fired this shot */
        public int getKeyIndex() { return keyIndex; }
    }
}
