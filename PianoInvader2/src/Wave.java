import java.awt.*;

/**
 * Wave - Represents and renders a horizontally scrolling sine wave.
 *
 * This class models an infinite sine wave that moves across the screen
 * by incrementing an x-phase offset (xOffset) each frame.  The wave is
 * evaluated analytically at any integer x-coordinate via getY(x), which
 * makes collision detection in Piano / GamePanel straightforward.
 *
 * Note: GamePanel computes its own wave inline for rendering and collision.
 * This class exists as a self-contained, reusable wave object that could
 * support multiple independent waves in future extensions.
 *
 * Wave equation
 * ───────────
 *   y(x) = centerY + amplitude × sin((x + xOffset) × frequency)
 *
 * Encapsulation
 * ─────────────
 * All wave parameters (amplitude, frequency, offset, colour) are private
 * and accessed through public getters/setters, allowing external code to
 * smoothly animate or modify the wave's appearance at runtime.
 *
 * Coordinate convention
 * ────────────────────
 * Standard Java 2D: y = 0 at the top, increasing downward.
 * A positive amplitude causes the sine wave to oscillate downward from
 * the centre (toward larger y values) on the first half-cycle.
 */
public class Wave {

    /** Peak displacement of the wave from its centre line, in pixels. */
    private double amplitude;

    /**
     * Spatial frequency of the wave in radians per pixel.
     * Higher values produce more oscillations across the same screen width.
     * Typical value: 0.01 rad/px (≈ 1.4 full cycles across 900 px).
     */
    private double frequency;

    /**
     * Cumulative horizontal phase offset in pixels.
     * Increased by dx each frame via update(); produces leftward scrolling
     * because a larger xOffset shifts apparent peaks to the left.
     */
    private double xOffset;

    /**
     * Vertical midpoint of the wave's oscillation, in pixels from the top
     * of the panel.  The wave oscillates symmetrically about this line.
     */
    private int centerY;

    /** Colour used when drawing the wave line. Default: Color.CYAN. */
    private Color color;

    /**
     * Width of the drawable area in pixels.
     * draw() renders the wave from x = 0 to x = width − 1.
     */
    private int width;

    /**
     * Constructs a Wave with the given geometry parameters.
     *
     * The initial xOffset is 0 (no horizontal scroll applied yet) and
     * the colour defaults to cyan.
     *
     * @param width     drawable width in pixels
     * @param centerY   y-coordinate of the wave's rest position (pixels)
     * @param amplitude peak displacement from centerY (pixels)
     * @param frequency spatial frequency (radians per pixel)
     */
    public Wave(int width, int centerY, double amplitude, double frequency) {
        this.width = width;
        this.centerY = centerY;
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.xOffset = 0;
        this.color = Color.CYAN;
    }

    /**
     * Advances the wave's scroll position by dx pixels per frame.
     *
     * Increasing xOffset shifts the apparent waveform leftward, creating
     * the illusion of a wave moving from right to left.  Call this once
     * per frame from the game loop before drawing.
     *
     * @param dx number of pixels to advance the phase each frame
     */
    public void update(double dx) {
        xOffset += dx;
    }

    /**
     * Returns the y-pixel of the wave at a given x-coordinate.
     *
     * Uses the wave equation:
     *   y = round(centerY + amplitude × sin((x + xOffset) × frequency))
     *
     * The result is rounded to the nearest integer for pixel-accurate rendering.
     *
     * @param x x-coordinate in panel pixels
     * @return  y-coordinate of the wave surface at x
     */
    public int getY(int x) {
        double angle = (x + xOffset) * frequency;
        return (int) Math.round(centerY + amplitude * Math.sin(angle));
    }

    /**
     * Renders the wave as a continuous cyan polyline and draws helper guide lines.
     *
     * The wave is drawn pixel-by-pixel as connected line segments from x = 0
     * to x = width − 1.  Three additional reference lines are drawn:
     *
     *   Centre line  — semi-transparent white (alpha 80/255) at y = centerY.
     *                  Helps visualise the wave's rest position.
     *   ±5 % markers — very faint white (alpha 40/255) at ± (amplitude × 0.05)
     *                  from the centre.  Indicate a narrow "flat zone" that
     *                  maps to HitCategory.SAME in slope classification.
     *
     * @param g the Graphics context from Swing's repaint pipeline
     */
    public void draw(Graphics g) {
        // Draw the wave as a sequence of 1-pixel line segments.
        g.setColor(color);
        int prevX = 0;
        int prevY = getY(0);
        for (int x = 1; x < width; x++) {
            int y = getY(x);
            g.drawLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }

        // Centre line: shows where the wave rests when displacement = 0.
        g.setColor(new Color(255, 255, 255, 80));
        g.drawLine(0, centerY, width, centerY);

        // ±5 % amplitude markers: indicate the slope threshold for HitCategory.SAME.
        int offset = (int) Math.round(amplitude * 0.05);
        g.setColor(new Color(255, 255, 255, 40));
        g.drawLine(0, centerY - offset, width, centerY - offset);  // upper marker
        g.drawLine(0, centerY + offset, width, centerY + offset);  // lower marker
    }

    // ── Getters and setters ──────────────────────────────────────────────

    /** @return peak displacement from centerY in pixels */
    public double getAmplitude() { return amplitude; }

    /** @param amplitude new peak displacement in pixels */
    public void setAmplitude(double amplitude) { this.amplitude = amplitude; }

    /** @return spatial frequency in radians per pixel */
    public double getFrequency() { return frequency; }

    /** @param frequency new spatial frequency in radians per pixel */
    public void setFrequency(double frequency) { this.frequency = frequency; }

    /** @return current cumulative x-phase offset in pixels */
    public double getXOffset() { return xOffset; }

    /** @param xOffset override the current phase offset */
    public void setXOffset(double xOffset) { this.xOffset = xOffset; }

    /** @return y-coordinate of the wave's rest position */
    public int getCenterY() { return centerY; }

    /** @param centerY new y-coordinate of the wave's rest position */
    public void setCenterY(int centerY) { this.centerY = centerY; }

    /** @return the colour used to draw the wave */
    public Color getColor() { return color; }

    /** @param color new wave colour */
    public void setColor(Color color) { this.color = color; }

    /** @return the drawable width in pixels */
    public int getWidth() { return width; }

    /** @param width new drawable width in pixels */
    public void setWidth(int width) { this.width = width; }
}
