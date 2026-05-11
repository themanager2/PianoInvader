import javax.swing.*;

/**
 * Main - Application entry point for Piano Invader.
 *
 * Piano Invader is a music-arcade hybrid game where the player controls a
 * triangular piano that fires shots upward at a scrolling sine wave.
 * When a shot collides with the wave, a sound is triggered whose pitch
 * depends on the wave's slope at the point of impact.
 *
 * Overall Object-Oriented Architecture
 * ─────────────────────────────────────
 * Main         – Bootstraps the Swing window and hands off to GamePanel.
 * GamePanel    – Owns the game loop (javax.swing.Timer at ~60 fps), handles
 *                all user input, drives updates, and delegates rendering.
 * Piano        – Extends TriangleShape; draws the player's ship with
 *                trapezoid keys and manages the list of in-flight shots.
 * TriangleShape– Extends GameObject; draws the triangular body and keys.
 * GameObject   – Abstract base class supplying x/y/width/height state and
 *                an abstract draw() contract.
 * Wave         – Draws and evaluates the scrolling sine wave.
 * AudioManager – Plays WAV files from disk and synthesises chaotic piano
 *                tones programmatically; notifies RainbowEffect via a
 *                listener callback.
 * PaywallManager–Loads paywall.jpg, runs a cubic-ease-in slide animation,
 *                and hides the image after a fixed stay duration.
 * RainbowEffect– Draws a cycling hue overlay on the piano keys and renders
 *                an animated GIF while 1.wav is playing.
 * HitCategory  – Enum that classifies a shot's impact point as HIGHER,
 *                LOWER, or SAME relative to the wave slope.
 *
 * Key Game Mechanics
 * ──────────────────
 * - The player clicks piano keys to fire shots that travel straight up.
 * - Each shot is removed when its y-position crosses the wave's y value at
 *   that x coordinate; at that moment a sound is triggered.
 * - Arrow keys move the piano left/right; mouse clicks fire individual keys.
 * - 7 % of all user interactions randomly trigger the paywall overlay.
 * - 20 % of sound events play a random WAV instead of a synthetic tone.
 * - 1.wav specifically activates the rainbow key overlay and GIF display for
 *   the full duration of the clip.
 * - The paywall blocks further input while it is visible.
 *
 * Memory / Performance Notes
 * ──────────────────────────
 * - Paywall image is scaled once and cached in a BufferedImage.
 * - Audio tasks run on a fixed-size thread pool (max 3 concurrent threads).
 * - AtomicBoolean guards against concurrent WAV triggers.
 */
public class Main {

    /**
     * Application entry point.
     *
     * Schedules window construction on the Swing Event Dispatch Thread (EDT)
     * using SwingUtilities.invokeLater, which is required for all Swing
     * component creation and manipulation.
     *
     * Steps performed:
     *  1. Create a JFrame titled "Piano Invader".
     *  2. Instantiate GamePanel at 900 × 600 pixels and set it as the
     *     content pane (GamePanel extends JPanel).
     *  3. Pack the frame so it sizes itself to the panel's preferred size.
     *  4. Centre the window on screen with setLocationRelativeTo(null).
     *  5. Make the window visible.
     *  6. Start the game loop timer inside GamePanel.
     *  7. Request keyboard focus so arrow-key events reach the panel.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        // Swing is not thread-safe; all UI work must happen on the EDT.
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Piano Invader");
            GamePanel gp = new GamePanel(900, 600);

            // Terminate the JVM when the window is closed.
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            f.setContentPane(gp);
            f.pack();                         // size frame to panel's preferred size
            f.setLocationRelativeTo(null);    // centre on screen
            f.setVisible(true);

            gp.start();                       // begin the ~60 fps game loop
            gp.requestFocusInWindow();        // capture keyboard input immediately
        });
    }

}