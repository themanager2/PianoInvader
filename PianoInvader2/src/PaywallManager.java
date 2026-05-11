import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.swing.ImageIcon;

/**
 * PaywallManager - Loads, animates, and renders a "paywall" overlay image.
 *
 * Purpose
 * ───────
 * On 7 % of player interactions the paywall image (paywall.jpg from the
 * sounds/ directory) slides up from the bottom of the screen, stays visible
 * for 5 seconds, then disappears.  While visible, all game input is blocked
 * in GamePanel (this class simply reports visibility; it does not block input
 * itself).
 *
 * Animation
 * ─────────
 * The slide-in uses a cubic ease-out function:
 *   eased = 1 − (1 − t)³    where t ∈ [0, 1]
 * This produces a fast initial velocity that decelerates smoothly to rest.
 * The animation duration is 800 ms; after completion the image stays for
 * 5 000 ms before being hidden.
 *
 * Image scaling and caching
 * ───────────────────────
 * The paywall image is scaled to 80 % of the panel width while preserving
 * the original aspect ratio.  Scaling is performed once into a BufferedImage
 * (with bilinear interpolation) and the result is cached.  Per-frame drawing
 * simply blits the cached BufferedImage, so there is no per-frame scaling
 * cost.
 *
 * Thread safety
 * ────────────
 * All fields are volatile because triggerPaywallChance() is called from the
 * EDT (inside input listeners) and update()/draw() are also on the EDT via
 * the Timer.  In practice all accesses happen on the EDT, but volatile
 * ensures visibility if the class is ever called from another thread.
 */
public class PaywallManager {

    /** Directory from which paywall.jpg is loaded. */
    private final File soundFolder;

    /** Used for the 7 % probability roll in triggerPaywallChance(). */
    private final Random random;
    
    /**
     * The original unscaled image loaded from paywall.jpg.
     * Null if the file was not found or failed to load.
     * Access is via ImageIcon so that Java's image loading pipeline handles
     * format decoding transparently.
     */
    private volatile Image paywallOriginalImage = null;

    /** Whether the paywall is currently visible (sliding in, held, or fading). */
    private volatile boolean paywallVisible = false;

    /** True only during the 800 ms ease-in animation; false once the image has arrived. */
    private volatile boolean paywallSliding = false;

    /** Current y-coordinate of the top-left corner of the paywall image (panel pixels). */
    private volatile int paywallY = 0;

    /** Target y-coordinate that the image slides toward (panel pixels). */
    private volatile int paywallTargetY = 0;

    /** System.currentTimeMillis() when the slide animation started. */
    private volatile long paywallSlideStart = 0L;

    /** Duration of the slide animation in milliseconds (800 ms). */
    private volatile long paywallSlideDuration = 800L;

    /** System.currentTimeMillis() when the image reached its target (slide ended). */
    private volatile long paywallStayStart = 0L;

    /** How long the paywall stays fully visible before being hidden (5 000 ms). */
    private volatile long paywallStayDuration = 5000L;

    /** y-coordinate at the start of the slide (always below the panel bottom). */
    private volatile int paywallStartY = 0;
    
    // ── Image cache ──────────────────────────────────────────────────
    // Cached scaled BufferedImage avoids re-scaling on every frame.
    // The cache is invalidated when the target dimensions change.

    /** The cached scaled version of paywallOriginalImage. */
    private volatile BufferedImage paywallCachedImage = null;

    /** Width of the currently cached image; used to detect when re-scaling is needed. */
    private volatile int paywallCachedWidth = -1;

    /** Height of the currently cached image; used to detect when re-scaling is needed. */
    private volatile int paywallCachedHeight = -1;
    
    /** Panel width in pixels, used for horizontal centring and scaled image sizing. */
    private final int panelWidth;

    /** Panel height in pixels, used to position the starting y below the screen. */
    private final int panelHeight;
    
    /**
     * Constructs the PaywallManager and immediately attempts to load paywall.jpg.
     *
     * @param soundFolder directory containing paywall.jpg
     * @param panelWidth  width of the game panel in pixels
     * @param panelHeight height of the game panel in pixels
     */
    public PaywallManager(File soundFolder, int panelWidth, int panelHeight) {
        this.soundFolder = soundFolder;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.random = new Random();
        
        loadPaywall();
    }
    
    /**
     * Loads paywall.jpg from the sounds folder into paywallOriginalImage.
     *
     * ImageIcon is used for its built-in format support.  If the file is
     * absent or an error occurs, paywallOriginalImage is left null and the
     * paywall is silently disabled for the session.
     */
    private void loadPaywall() {
        try {
            File paywallFile = new File(soundFolder, "paywall.jpg");
            if (paywallFile.exists() && paywallFile.isFile()) {
                ImageIcon tmp = new ImageIcon(paywallFile.getAbsolutePath());
                paywallOriginalImage = tmp.getImage();
                System.out.println("[ASSET] Loaded paywall.jpg for display: " + paywallFile.getName());
            } else {
                paywallOriginalImage = null;
            }
        } catch (Exception ex) {
            System.out.println("[ASSET] Failed to load paywall.jpg: " + ex.getMessage());
            paywallOriginalImage = null;
        }
    }
    
    /**
     * Rolls a 7 % chance to trigger the paywall slide-in animation.
     *
     * Called by GamePanel every time a valid user interaction occurs (mouse
     * click or key press).  The paywall will not trigger if:
     *   - paywallOriginalImage is null (file was not found)
     *   - The paywall is already visible (prevents stacking triggers)
     *
     * When triggered, the initial y-position is set 8 px below the panel
     * bottom so the image slides smoothly up from off-screen.  The target
     * y is positioned so the image is centred vertically around the wave.
     *
     * @param waveY the current y-pixel of the wave at the panel's centre x;
     *              used to vertically position the paywall near the wave
     * @return true if the paywall was triggered; false otherwise
     */
    public boolean triggerPaywallChance(int waveY) {
        if (paywallOriginalImage == null) return false;
        if (paywallVisible) return false;

        boolean show = random.nextDouble() < 0.07;  // 7 % probability
        if (!show) return false;
        
        paywallVisible = true;
        paywallSliding = true;
        paywallStartY = panelHeight + 8;  // start just below the visible area
        
        int[] scaled = computePaywallScaledSize();
        int iconH = scaled[1];
        
        // Target: vertically centred on the wave minus a small upward offset.
        int desiredY = Math.max(4, waveY - iconH / 2 - 20);
        paywallTargetY = Math.max(4, desiredY);
        paywallY = paywallStartY;
        paywallSlideStart = System.currentTimeMillis();
        paywallStayStart = 0L;
        
        System.out.println("[ASSET] Paywall triggered by user input; sliding in (targetY=" + paywallTargetY + ").");
        return true;
    }
    
    /**
     * Advances the paywall animation state by one frame.
     *
     * Called every 16 ms by the Timer in GamePanel.  Two phases:
     *
     * Sliding phase (paywallSliding == true)
     *   - Normalises elapsed time to t ∈ [0, 1].
     *   - Applies cubic ease-out: eased = 1 − (1 − t)³.
     *   - Interpolates paywallY from paywallStartY toward paywallTargetY.
     *   - When t ≥ 1.0, clamps to target, marks sliding finished, and records
     *     paywallStayStart for the stay timer.
     *
     * Stay phase (paywallSliding == false)
     *   - Once 5 000 ms have elapsed since paywallStayStart, all state is
     *     reset and paywallVisible is set to false.
     */
    public void update() {
        if (!paywallVisible) return;
        
        long now = System.currentTimeMillis();
        
        if (paywallSliding) {
            long elapsed = now - paywallSlideStart;
            // Normalise elapsed time to [0, 1] over the slide duration.
            double t = Math.min(1.0, (double) elapsed / (double) paywallSlideDuration);
            // Cubic ease-out: fast start, decelerates to rest.
            double ease = 1 - Math.pow(1 - t, 3);
            int newY = (int) (paywallStartY + (paywallTargetY - paywallStartY) * ease);
            paywallY = newY;
            if (t >= 1.0) {
                paywallSliding = false;
                paywallStayStart = now;  // begin the stay timer
            }
        } else {
            // Stay phase: hide the paywall after 5 seconds.
            if (paywallStayStart > 0 && now - paywallStayStart >= paywallStayDuration) {
                paywallVisible = false;
                paywallSliding = false;
                paywallStartY = 0;
                paywallTargetY = 0;
                paywallSlideStart = 0L;
                paywallStayStart = 0L;
                System.out.println("[ASSET] Paywall stay finished; hiding paywall.");
            }
        }
    }
    
    /**
     * Computes the scaled dimensions of the paywall image.
     *
     * The image is scaled to 80 % of the panel width while preserving the
     * original aspect ratio (height is derived proportionally).
     *
     * @return int[2] where [0] = target width, [1] = target height in pixels.
     *         Returns {0, 0} if paywallOriginalImage is null.
     */
    private int[] computePaywallScaledSize() {
        if (paywallOriginalImage == null) return new int[]{0, 0};
        int targetW = (int) (panelWidth * 0.80);
        int origW = paywallOriginalImage.getWidth(null);
        int origH = paywallOriginalImage.getHeight(null);
        if (origW <= 0 || origH <= 0) return new int[]{targetW, targetW / 2};
        // Preserve aspect ratio: targetH / targetW = origH / origW
        int targetH = (int) ((long) targetW * origH / origW);
        return new int[]{targetW, targetH};
    }
    
    /**
     * Returns the cached scaled BufferedImage, re-creating it only when the
     * target dimensions have changed.
     *
     * Bilinear interpolation is used for smooth downscaling.  The cache is
     * stored in a BufferedImage (TYPE_INT_ARGB) to allow fast hardware blits.
     *
     * @param targetW desired width of the cached image in pixels
     * @param targetH desired height of the cached image in pixels
     * @return the cached BufferedImage at exactly targetW × targetH
     */
    private BufferedImage getCachedPaywallImage(int targetW, int targetH) {
        if (paywallCachedImage == null
                || paywallCachedWidth != targetW
                || paywallCachedHeight != targetH) {
            // Allocate a new buffer and scale the original image into it.
            BufferedImage buf = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = buf.createGraphics();
            bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            bg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            bg.drawImage(paywallOriginalImage, 0, 0, targetW, targetH, null);
            bg.dispose();
            
            paywallCachedImage = buf;
            paywallCachedWidth = targetW;
            paywallCachedHeight = targetH;
            System.out.println("[ASSET] Paywall image cached at " + targetW + "x" + targetH);
        }
        return paywallCachedImage;
    }
    
    /**
     * Draws the paywall image at its current animated position.
     *
     * Horizontally centres the image within the panel.  The y-position
     * (paywallY) is clamped so the image never renders above y = −iconH
     * (fully off-screen above) or below y = panelHeight (fully off-screen
     * below), preventing layout artefacts at extreme animation states.
     *
     * Does nothing if the paywall is not visible or the image failed to load.
     *
     * @param g          Graphics2D context from paintComponent
     * @param panelWidth current width of the panel (pixels); used for centring
     */
    public void draw(Graphics2D g, int panelWidth) {
        if (!paywallVisible || paywallOriginalImage == null) return;
        
        int[] scaled = computePaywallScaledSize();
        int iconW = scaled[0];
        int iconH = scaled[1];
        
        if (iconW > 0 && iconH > 0) {
            BufferedImage img = getCachedPaywallImage(iconW, iconH);
            int drawX = Math.max(0, (panelWidth - iconW) / 2);              // horizontally centred
            int drawY = Math.max(-iconH, Math.min(panelHeight, paywallY));  // clamped to panel bounds
            g.drawImage(img, drawX, drawY, null);
        }
    }
    
    /**
     * Returns whether the paywall is currently on-screen.
     *
     * Used by GamePanel to decide whether to suppress user input.
     *
     * @return true if the paywall is visible (sliding in or staying)
     */
    public boolean isVisible() {
        return paywallVisible;
    }
}
