import java.awt.*;
import java.util.Random;
import javax.swing.ImageIcon;

/**
 * RainbowEffect - Manages the visual effects that play during 1.wav playback.
 *
 * Two effects are provided:
 *
 * 1. Rainbow key overlay
 *    A row of 20 brightly coloured rectangles is drawn over the piano's key
 *    row.  Each key is assigned a hue derived from:
 *      hue = ((currentTimeMs % 6000) / 6000.0) + (keyIndex × 0.06)
 *    The time term rotates all keys through the full hue wheel every 6 seconds.
 *    The index term spreads adjacent keys 0.06 hue units apart, producing a
 *    smooth rainbow gradient across the keyboard.  A small random jitter
 *    (±0.03 hue units) is added per key per frame to create a shimmering effect.
 *    The overlay is drawn at 90 % opacity (AlphaComposite.SRC_OVER, 0.9f).
 *
 * 2. Animated GIF overlay
 *    A pre-loaded ImageIcon (containing 1.gif) is painted centred horizontally
 *    and vertically aligned with the wave.  ImageIcon handles GIF animation
 *    automatically through Swing's image observer mechanism.
 *
 * Lifecycle
 * ─────────
 * RainbowEffect implements AudioManager.RainbowEffectListener so that
 * AudioManager can activate/deactivate the effects by calling:
 *   onWavStart("1.wav", durationMs) → sets rainbowActive and gifVisible to true
 *   onWavEnd()                       → sets both to false
 *
 * These callbacks are fired from audio threads; the volatile keyword ensures
 * the EDT reads the updated values immediately on the next repaint.
 *
 * Thread safety
 * ────────────
 * rainbowActive and gifVisible are volatile; each is written atomically by
 * audio threads and read atomically by the EDT.  No compound operation
 * requires both fields to be consistent simultaneously, so volatile alone
 * is sufficient (no synchronised block needed).
 */
public class RainbowEffect implements AudioManager.RainbowEffectListener {

    /**
     * True while the rainbow key overlay should be rendered.
     * Set to true by onWavStart() when 1.wav begins; false by onWavEnd().
     */
    private volatile boolean rainbowActive = false;

    /**
     * True while the animated GIF should be rendered.
     * Always false when rainbowActive is false; may also be false if
     * gifIcon was not loaded (file missing at startup).
     */
    private volatile boolean gifVisible = false;

    /**
     * The animated GIF loaded from sounds/1.gif.
     * Null if the file was not found or failed to load.
     * ImageIcon handles the GIF animation loop internally.
     */
    private volatile ImageIcon gifIcon = null;

    /**
     * Random instance used to generate per-key hue jitter each frame.
     * Creates a shimmering/sparkling appearance on the rainbow keys.
     */
    private final Random random = new Random();
    
    /**
     * Constructs a RainbowEffect with the given animated GIF.
     *
     * @param gifIcon the ImageIcon loaded from 1.gif, or null if unavailable.
     *                If null, the GIF overlay is permanently disabled but
     *                the rainbow key overlay still functions.
     */
    public RainbowEffect(ImageIcon gifIcon) {
        this.gifIcon = gifIcon;
    }
    
    /**
     * Called by AudioManager when a WAV file begins playing.
     *
     * Only activates the visual effects if the WAV is specifically 1.wav;
     * other WAV files (2.wav, 3.wav, 4.wav) produce no visual change.
     *
     * @param wavName    filename of the WAV that started (e.g. "1.wav")
     * @param durationMs total clip length in milliseconds (for logging)
     */
    @Override
    public void onWavStart(String wavName, long durationMs) {
        if ("1.wav".equalsIgnoreCase(wavName)) {
            rainbowActive = true;
            gifVisible = (gifIcon != null);  // only show GIF if the file was loaded
            System.out.println("[EFFECT] Rainbow and GIF activated for " + wavName + 
                    " (duration: " + durationMs + "ms)");
        }
    }
    
    /**
     * Called by AudioManager when any WAV file finishes or is aborted.
     *
     * Deactivates both effects regardless of which WAV was playing, acting
     * as a reliable clean-up path.
     */
    @Override
    public void onWavEnd() {
        rainbowActive = false;
        gifVisible = false;
        System.out.println("[EFFECT] Rainbow and GIF deactivated.");
    }
    
    /**
     * Returns whether the rainbow key overlay is currently active.
     *
     * Polled each frame by GamePanel.paintComponent().
     *
     * @return true if the rainbow overlay should be drawn
     */
    public boolean isRainbowActive() {
        return rainbowActive;
    }
    
    /**
     * Returns whether the animated GIF overlay should be drawn.
     *
     * Both gifVisible (set by onWavStart) and gifIcon (non-null) must be
     * true/present; this guards against a null dereference if the file was
     * missing at startup.
     *
     * @return true if the GIF should be rendered this frame
     */
    public boolean isGifVisible() {
        return gifVisible && gifIcon != null;
    }
    
    /**
     * Draws the rainbow key overlay on top of the piano's key row.
     *
     * Rendering details
     * ─────────────────
     * - 20 rectangles are drawn, one per key.
     * - Each rectangle spans the full height of the key row
     *   (pianoHeight / 3) from the top of the key area.
     * - The last rectangle is widened to fill any remaining space caused
     *   by integer division (pianoWidth % keyCount leftover pixels).
     * - A semi-transparent black border (60/255 alpha) is drawn around each
     *   key to visually separate them.
     * - The original AlphaComposite is saved and restored so subsequent
     *   rendering by GamePanel is not affected.
     *
     * @param g          Graphics2D context from GamePanel.paintComponent
     * @param pianoX     x position of the piano's left edge (pixels)
     * @param pianoY     y position of the piano's top edge (pixels)
     * @param pianoWidth total width of the piano (pixels)
     * @param pianoHeight total height of the piano (pixels)
     */
    public void drawRainbow(Graphics2D g, int pianoX, int pianoY, int pianoWidth, int pianoHeight) {
        if (!rainbowActive) return;
        
        int keyCount = 20;  // must match Piano.getKeyCount()
        int keyW = Math.max(1, pianoWidth / keyCount);   // width of each key at base
        int keyH = Math.max(1, pianoHeight / 3);         // key row height = bottom third
        int topY = pianoY + pianoHeight - keyH;          // y of the top of the key row
        
        // Save composite to restore after drawing the semi-transparent overlay.
        Composite oldComp = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
        
        long time = System.currentTimeMillis();
        for (int i = 0; i < keyCount; i++) {
            // Base hue cycles over 6 seconds; each key is offset by 0.06 hue units.
            float hue = (float) (((time % 6000) / 6000.0) + (i * 0.06));
            hue = hue - (float) Math.floor(hue);  // wrap to [0, 1]

            // Per-key jitter creates a sparkling, non-uniform shimmer.
            float jitter = (float) ((random.nextDouble() - 0.5) * 0.06);
            Color c = Color.getHSBColor(hue + jitter, 0.85f, 0.95f);
            g.setColor(c);

            int kx = pianoX + i * keyW;
            // Last key fills remaining space to avoid a 1–2 px gap from rounding.
            int kw = (i == keyCount - 1) ? (pianoX + pianoWidth - kx) : keyW;
            g.fillRect(kx, topY, kw, keyH);

            // Semi-transparent black separator border.
            g.setColor(new Color(0, 0, 0, 60));
            g.setStroke(new BasicStroke(1f));
            g.drawRect(kx, topY, kw, keyH);
        }
        
        // Restore the original composite so other rendering is unaffected.
        g.setComposite(oldComp);
    }
    
    /**
     * Draws the animated GIF centred horizontally and aligned with the wave.
     *
     * The GIF is positioned so its vertical centre aligns with the wave's
     * y-position at the panel's horizontal centre.  If the computed y would
     * place the GIF above the top of the panel, it is clamped to y = 4.
     *
     * ImageIcon.paintIcon() drives GIF animation by registering an
     * ImageObserver with the component passed as the first argument.  Passing
     * null here means Swing relies on its own timer to advance GIF frames,
     * which works correctly in practice.
     *
     * @param g          Graphics2D context from GamePanel.paintComponent
     * @param panelWidth width of the panel in pixels (for horizontal centring)
     * @param panelHeight height of the panel in pixels (unused; kept for API
     *                   symmetry with potential future vertical clamping)
     * @param waveY      y-pixel of the wave at the panel's horizontal centre
     */
    public void drawGif(Graphics2D g, int panelWidth, int panelHeight, double waveY) {
        if (!gifVisible || gifIcon == null) return;
        
        int iconW = gifIcon.getIconWidth();
        int iconH = gifIcon.getIconHeight();
        int drawX = Math.max(0, (panelWidth - iconW) / 2);        // horizontally centred
        int drawY = Math.max(4, (int) waveY - iconH / 2);         // vertically near the wave
        gifIcon.paintIcon(null, g, drawX, drawY);
    }
}
