/**
 * HitCategory - Classifies the slope of the sine wave at the precise pixel
 * where a piano shot collides with it.
 *
 * The slope is computed in GamePanel.updateGame() by sampling the wave's
 * y-position one pixel to the left and one pixel to the right of the
 * shot's x coordinate (a simple finite-difference approximation):
 *
 *     slope = (waveY(x + 1) − waveY(x − 1)) / 2
 *
 * Because y increases downward in Java 2D, a positive slope means the
 * wave surface is descending (moving toward the bottom of the screen),
 * and a negative slope means it is ascending.
 *
 * AudioManager.playSound() uses the category to shift the base frequency
 * of the synthesised piano tone:
 *   • HIGHER  → baseFreq × 1.45  (raises the pitch by roughly a major sixth)
 *   • LOWER   → baseFreq × 0.65  (lowers the pitch by about a minor third)
 *   • SAME    → baseFreq unchanged  (flat portion of the wave)
 *
 * The threshold for SAME is |slope| < 0.02 (nearly flat).
 */
public enum HitCategory {
    /** Wave is rising at the hit point (slope > +0.02). Pitch shifted up. */
    HIGHER,

    /** Wave is falling at the hit point (slope < −0.02). Pitch shifted down. */
    LOWER,

    /** Wave is nearly flat at the hit point (|slope| < 0.02). Pitch unchanged. */
    SAME
}
