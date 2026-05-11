import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.*;

/**
 * GamePanel - The central class that owns and coordinates all game subsystems.
 *
 * Responsibilities
 * ───────────────
 * 1. Game loop  – A javax.swing.Timer fires every 16 ms (~62.5 fps), calling
 *    updateGame() followed by repaint().  The Timer runs on the EDT, so all
 *    game state reads/writes and rendering happen on the same thread, avoiding
 *    the need for explicit synchronisation of game objects.
 *
 * 2. Wave animation – A sine wave is computed analytically each frame using a
 *    monotonically increasing phase value; no Wave object is used here for the
 *    wave rendering (the Wave class exists but GamePanel computes the wave
 *    inline for simplicity).
 *
 * 3. Collision detection – Each active KeyShot is tested against the wave
 *    y-position at the shot's x-coordinate.  When the shot reaches or passes
 *    the wave, a HitCategory is determined from the local slope and the hit is
 *    dispatched to AudioManager.playSound().
 *
 * 4. Input handling – Mouse clicks fire piano keys; arrow keys move the piano.
 *    All input is suppressed (returns early) while the paywall is visible.
 *    Every valid interaction first checks for a 7 % paywall trigger.
 *
 * 5. Rendering  – paintComponent draws (in order):
 *      a. The sine wave
 *      b. The piano (body + keys + in-flight shots)
 *      c. The rainbow key overlay (only while 1.wav plays)
 *      d. The animated GIF (only while 1.wav plays)
 *      e. The paywall image (if triggered)
 *
 * Wave equation
 * ───────────
 *   y(x) = waveCenterY + waveAmp × sin(waveFreq × x + phase)
 *
 * where phase increases by 0.06 radians each frame, making the wave scroll
 * smoothly from right to left as seen from the player.
 */
public class GamePanel extends JPanel {

    /** Panel width in pixels; set from the constructor argument and used for
     *  piano boundary clamping and wave rendering. */
    private final int width;

    /** The player-controlled piano entity. Handles drawing, movement, shot
     *  management, and key-click detection. */
    private final Piano piano;

    // ── Sine wave parameters ──────────────────────────────────────────
    // The wave is defined purely by these constants plus the mutable `phase`.

    /**
     * Current phase offset of the sine wave in radians.
     * Incremented by 0.06 each frame to scroll the wave horizontally.
     * Because the wave equation uses (waveFreq * x + phase), increasing
     * phase shifts the apparent wave leftward.
     */
    private double phase = 0;

    /**
     * Spatial frequency of the wave: 0.01 radians per pixel.
     * Controls how many full oscillation cycles appear across the panel.
     * At 900 px wide: ~900 × 0.01 / (2π) ≈ 1.4 full cycles visible.
     */
    private final double waveFreq = 0.01;

    /**
     * Half-amplitude of the sine wave in pixels.
     * The wave oscillates between waveCenterY − waveAmp and
     * waveCenterY + waveAmp (i.e. a total peak-to-peak of 120 px).
     */
    private final int waveAmp = 60;

    /**
     * Vertical centre of the wave's oscillation in pixels from the top of
     * the panel.  Set to h/2 so the wave is centred vertically.
     */
    private final int waveCenterY;

    // ── Timer ───────────────────────────────────────────────────────

    /**
     * Swing timer that drives the game loop at approximately 60 fps (16 ms
     * interval).  Fires on the EDT, eliminating the need for synchronisation
     * between update and paint.
     */
    private final Timer timer;

    // ── Asset path ─────────────────────────────────────────────────

    /**
     * Relative path to the directory containing all runtime assets:
     * 1.wav, 2.wav, 3.wav, 4.wav, 1.gif, and paywall.jpg.
     * Resolved relative to the JVM's working directory (project root when
     * launched from an IDE or the directory containing the JAR).
     */
    private final File soundFolder = new File("sounds");

    // ── Subsystem managers ────────────────────────────────────────

    /** Handles WAV and synthetic-tone playback on a background thread pool. */
    private final AudioManager audioManager;

    /** Manages the paywall image: loading, slide animation, and stay timer. */
    private final PaywallManager paywallManager;

    /** Draws the rainbow key overlay and animated GIF during 1.wav playback. */
    private final RainbowEffect rainbowEffect;

    /**
     * Constructs the game panel and all subsystems.
     *
     * Initialisation order matters:
     *  1. Piano is placed at the bottom-centre of the panel.
     *  2. AudioManager is created (opens the thread pool).
     *  3. PaywallManager is created (loads paywall.jpg from disk).
     *  4. RainbowEffect is created with the GIF loaded from disk (or null if absent).
     *  5. The rainbowListener is wired from AudioManager → RainbowEffect.
     *  6. Mouse and keyboard listeners are attached.
     *  7. The Timer is created (but not started until start() is called).
     *
     * @param w panel width in pixels
     * @param h panel height in pixels
     */
    public GamePanel(int w, int h) {
        this.width = w;
        setPreferredSize(new Dimension(w, h));
        setBackground(Color.BLACK);

        // Place the piano near the bottom centre of the panel.
        // Cap width at 700 px so it doesn't span the entire panel on wide screens.
        int pianoW = Math.min(700, w - 80);
        int pianoH = 160;
        int pianoX = (w - pianoW) / 2;     // horizontally centred
        int pianoY = h - pianoH - 20;      // 20 px gap from the bottom edge
        this.piano = new Piano(pianoX, pianoY, pianoW, pianoH, w);

        this.waveCenterY = h / 2;  // wave oscillates about the vertical midpoint

        // ── Subsystem initialisation ──────────────────────────────
        this.audioManager = new AudioManager(soundFolder);
        this.paywallManager = new PaywallManager(soundFolder, w, h);
        
        // Attempt to load the animated GIF played during 1.wav.
        // ImageIcon handles GIF animation natively; if the file is missing, the
        // rainbow effect still activates but without the GIF overlay.
        ImageIcon gifIcon = null;
        try {
            File gifFile = new File(soundFolder, "1.gif");
            if (gifFile.exists() && gifFile.isFile()) {
                gifIcon = new ImageIcon(gifFile.getAbsolutePath());
                System.out.println("[ASSET] Loaded 1.gif for display: " + gifFile.getName());
            }
        } catch (Exception ex) {
            System.out.println("[ASSET] Failed to load 1.gif: " + ex.getMessage());
        }
        
        this.rainbowEffect = new RainbowEffect(gifIcon);
        
        // Wire the audio → visual callback so RainbowEffect receives WAV events.
        audioManager.setRainbowListener(rainbowEffect);

        // ── Mouse listener ────────────────────────────────────────────
        // Clicking a piano key fires a shot upward from that key's position.
        // Input is suppressed while the paywall is visible.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (paywallManager.isVisible()) {
                    // The paywall demands the user's attention; ignore game input.
                    System.out.println("[INPUT] Mouse click ignored while paywall visible.");
                    return;
                }
                
                // Evaluate the 7 % paywall chance at the wave's current centre x.
                // If the paywall is triggered, suppress the actual game action this frame.
                int centerX = width / 2;
                int waveY = (int) computeWaveY(centerX);
                if (paywallManager.triggerPaywallChance(waveY)) {
                    return;
                }
                
                // Delegate click to the Piano, which detects which trapezoid key was
                // hit and adds a KeyShot to its shot list.
                piano.handleMousePress(e.getX(), e.getY());
            }
        });

        // ── Keyboard listener ────────────────────────────────────────
        // LEFT / RIGHT arrow keys translate the piano within panel bounds.
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (paywallManager.isVisible()) {
                    System.out.println("[INPUT] Key press ignored while paywall visible.");
                    return;
                }
                
                // Evaluate the 7 % paywall chance on keyboard input too.
                int centerX = width / 2;
                int waveY = (int) computeWaveY(centerX);
                if (paywallManager.triggerPaywallChance(waveY)) {
                    return;
                }
                
                // Piano.moveLeft/moveRight clamp position to panel bounds internally.
                if (e.getKeyCode() == KeyEvent.VK_LEFT)  piano.moveLeft();
                if (e.getKeyCode() == KeyEvent.VK_RIGHT) piano.moveRight();
            }
        });

        // Panel must be focusable to receive KeyEvents.
        setFocusable(true);

        // ── Game loop timer ─────────────────────────────────────────
        // 16 ms interval ≈ 62.5 fps.  The listener executes on the EDT, so
        // all state mutations here are single-threaded with the rendering code.
        timer = new Timer(16, ev -> {
            updateGame();           // advance physics and detect collisions
            paywallManager.update(); // run slide animation and stay timer
            repaint();              // schedule a paintComponent call
        });
    }

    /**
     * Starts the game loop timer and requests keyboard focus.
     *
     * Called once from Main after the JFrame is made visible.  Using
     * invokeLater inside start() ensures the focus request is queued after
     * the window's native peer has finished becoming visible.
     */
    public void start() {
        timer.start();
        SwingUtilities.invokeLater(() -> {
            if (!requestFocusInWindow()) requestFocus();
        });
    }

    /**
     * Stops the game loop and releases audio resources.
     *
     * Should be called when the window is closing (or in tests) to shut down
     * the AudioManager's thread pool cleanly before the JVM exits.
     */
    public void stop() {
        timer.stop();
        audioManager.shutdown();
    }

    /**
     * Advances game state by one frame (~16 ms).
     *
     * Steps performed each frame:
     *  1. Increment phase by 0.06 rad to scroll the wave.
     *  2. Call piano.updateShots() to move all in-flight shots upward and
     *     remove any that have exited the top of the panel.
     *  3. For each active shot, evaluate collision with the wave:
     *       a. Compute the wave's y-position at the shot's centre x.
     *       b. Compute the local slope using a two-point finite difference.
     *       c. Classify the slope into a HitCategory.
     *       d. If the shot has reached or crossed the wave (sy <= waveY),
     *          trigger AudioManager.playSound() and remove the shot.
     *
     * Collision snapshot
     * ─────────────────
     * A copy of the shot list (new ArrayList<>(...)) is iterated to avoid a
     * ConcurrentModificationException when removing shots mid-loop.
     */
    private void updateGame() {
        // Advance the wave phase: 0.06 rad/frame × 62.5 frames/s ≈ 3.75 rad/s.
        phase += 0.06;

        piano.updateShots();  // move shots up; prune off-screen shots

        // Snapshot the shot list so removals during iteration are safe.
        List<Piano.KeyShot> shots = new ArrayList<>(piano.getShots());
        for (Piano.KeyShot s : shots) {
            // Use the horizontal centre of the shot for accurate wave sampling.
            int sx = s.getX() + s.getWidth() / 2;
            int sy = s.getY();

            double waveY = computeWaveY(sx);
            double slope = computeWaveSlope(sx);

            // A shot "hits" the wave when its top edge is at or above the wave.
            if (sy <= waveY) {
                HitCategory category;
                if (Math.abs(slope) < 0.02)  category = HitCategory.SAME;   // nearly flat
                else if (slope > 0)           category = HitCategory.HIGHER; // wave rising (y increasing = going down in screen space)
                else                          category = HitCategory.LOWER;  // wave falling

                // Fire sound asynchronously; the shot is removed immediately.
                audioManager.playSound(s.getKeyIndex(), category, waveY, waveCenterY, waveAmp);
                removeShotFromPiano(s);
            }
        }
    }

    /**
     * Returns the y-pixel of the sine wave at horizontal position x.
     *
     * The wave equation is:
     *   y(x) = waveCenterY + waveAmp × sin(waveFreq × x + phase)
     *
     * @param x x-coordinate in panel pixels
     * @return  y-coordinate of the wave surface at x (panel pixels, top = 0)
     */
    private double computeWaveY(int x) {
        return waveCenterY + waveAmp * Math.sin(waveFreq * x + phase);
    }

    /**
     * Returns the approximate slope of the wave at horizontal position x.
     *
     * Uses a central finite-difference approximation (two samples, ±1 px):
     *   slope = (y(x+1) − y(x−1)) / 2
     *
     * Because y increases downward, a positive slope means the wave surface
     * is moving downward to the right (classified as HIGHER in screen space).
     *
     * @param x x-coordinate in panel pixels
     * @return  slope in pixels-per-pixel (dimensionless)
     */
    private double computeWaveSlope(int x) {
        double y1 = computeWaveY(x - 1);
        double y2 = computeWaveY(x + 1);
        return (y2 - y1) / 2.0;
    }

    /**
     * Removes a specific KeyShot from the piano's shot list.
     *
     * Identifies the exact shot by matching keyIndex, x, and y to handle the
     * (unlikely) case where the same key fires multiple shots simultaneously.
     * Uses an iterator to avoid a ConcurrentModificationException.
     *
     * @param target the shot to remove
     */
    private void removeShotFromPiano(Piano.KeyShot target) {
        Iterator<Piano.KeyShot> it = piano.getShots().iterator();
        while (it.hasNext()) {
            Piano.KeyShot s = it.next();
            // Match on all three fields to uniquely identify this shot instance.
            if (s.getKeyIndex() == target.getKeyIndex() &&
                    s.getX() == target.getX() &&
                    s.getY() == target.getY()) {
                it.remove();
                return;  // remove only the first match
            }
        }
    }

    /**
     * Renders the entire scene for the current frame.
     *
     * Called by Swing's repaint/paint pipeline on the EDT after updateGame()
     * has advanced the state.  Antialiasing is enabled for smooth curves.
     *
     * Draw order (painter's algorithm – later draws on top of earlier):
     *  1. Background: super.paintComponent() fills with the black background.
     *  2. Wave: drawn pixel-by-pixel as a series of 1-px line segments with
     *     a 2 px cyan stroke.
     *  3. Piano: delegates to Piano.draw() which draws body, keys, and shots.
     *  4. Rainbow overlay: covers only the piano's key row with cycling hues.
     *     Only rendered when RainbowEffect.isRainbowActive() is true.
     *  5. GIF overlay: centred horizontally, vertically aligned with the wave.
     *     Only rendered when RainbowEffect.isGifVisible() is true.
     *  6. Paywall: drawn last so it appears on top of everything.
     *
     * @param g0 the Graphics context provided by Swing's paint system
     */
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);  // fills background with Color.BLACK
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // ── Draw the scrolling sine wave ──────────────────────────────
        // Connected line segments give a smooth curve without needing GeneralPath.
        g.setColor(Color.CYAN);
        g.setStroke(new BasicStroke(2f));  // 2 px wide line

        int prevX = 0;
        int prevY = (int) computeWaveY(prevX);

        for (int x = 1; x < getWidth(); x++) {
            int y = (int) computeWaveY(x);
            g.drawLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }

        // ── Draw the piano (body + keys + in-flight shots) ──────────────
        piano.draw(g);

        // ── Rainbow key overlay (active only while 1.wav is playing) ───
        if (rainbowEffect.isRainbowActive()) {
            rainbowEffect.drawRainbow(g, piano.getX(), piano.getY(), piano.getWidth(), piano.getHeight());
        }

        // ── Animated GIF overlay (active only while 1.wav is playing) ──
        if (rainbowEffect.isGifVisible()) {
            rainbowEffect.drawGif(g, getWidth(), getHeight(), computeWaveY(getWidth() / 2));
        }

        // ── Paywall image (drawn last; appears above all other content) ──
        paywallManager.draw(g, getWidth());
    }
}
