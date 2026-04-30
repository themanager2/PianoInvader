import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main.java
 *
 * - WAV logging (attempt / missing / success / decode/play errors)
 * - Restored rich chaotic synthesis (playChaoticToneRich)
 * - Blocks keyboard and mouse input while any sound is playing
 * - While 1.wav is playing, all piano keys turn into continuously changing rainbow colors
 *   for the full duration of the WAV (rainbow stays active until playback truly finishes).
 * - If a GIF named 1.gif exists in the sounds folder, it will be displayed at the top center
 *   on top of the sine wave while 1.wav is playing.
 * - If a JPG named paywall.jpg exists in the sounds folder, it has a 40% chance to appear
 *   when the user interacts (mouse click or key press). When shown it slides from the
 *   bottom up, stays for 5 seconds, then hides. While the paywall is visible, keyboard input is blocked.
 * - The paywall image is scaled to fit the window (80% of panel width) while preserving aspect ratio.
 *
 * MEMORY FIXES:
 * - Paywall image is cached as a BufferedImage and only recomputed when dimensions change,
 *   instead of calling getScaledInstance() on every paintComponent call (was causing ~10GB RAM usage).
 * - Sound threads are managed by a fixed thread pool (max 3 concurrent) instead of unbounded new Thread().
 * - PCM buffer duration capped at 0.8s to reduce per-tone memory.
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Piano Invader");
            GamePanel gp = new GamePanel(900, 600);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(gp);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            gp.start();
            gp.requestFocusInWindow();
        });
    }

    public static class GamePanel extends JPanel {

        private final int width;
        private final int height;
        private final Piano piano;
        private final Random random = new Random();

        // Wave parameters
        private double phase = 0;
        private final double waveFreq = 0.01;
        private final int waveAmp = 60;
        private final int waveCenterY;

        private final Timer timer;

        // FIX: Fixed thread pool instead of unbounded new Thread() per shot
        private final ExecutorService soundExecutor = Executors.newFixedThreadPool(3);

        // Sound folder relative to project root
        private final File soundFolder = new File("sounds");
        private final String[] specialSounds = {"1.wav","2.wav","3.wav","4.wav","5.wav","6.wav"};

        // Flag to indicate a sound is currently playing
        private final AtomicBoolean soundPlaying = new AtomicBoolean(false);

        // Track which WAV is currently playing (filename) and whether rainbow mode is active
        private volatile String currentWavName = "";
        private volatile boolean rainbowActive = false;

        // GIF handling: specifically load 1.gif from sounds folder (if present)
        private volatile ImageIcon gifIcon = null;
        private volatile boolean gifVisible = false;

        // Paywall image handling
        // store original Image for scaling
        private volatile Image paywallOriginalImage = null;
        private volatile boolean paywallVisible = false;
        private volatile boolean paywallSliding = false;
        private volatile int paywallY = 0;
        private volatile int paywallTargetY = 0;
        private volatile long paywallSlideStart = 0L;
        private volatile long paywallSlideDuration = 800L; // ms to slide up
        private volatile long paywallStayStart = 0L;
        private volatile long paywallStayDuration = 5000L; // stay 5 seconds
        private volatile int paywallStartY = 0;

        // FIX: Cached scaled paywall image — recomputed only when dimensions change
        private volatile BufferedImage paywallCachedImage = null;
        private volatile int paywallCachedWidth = -1;
        private volatile int paywallCachedHeight = -1;

        public GamePanel(int w, int h) {
            this.width = w;
            this.height = h;
            setPreferredSize(new Dimension(w, h));
            setBackground(Color.BLACK);

            int pianoW = Math.min(700, w - 80);
            int pianoH = 160;
            int pianoX = (w - pianoW) / 2;
            int pianoY = h - pianoH - 20;
            this.piano = new Piano(pianoX, pianoY, pianoW, pianoH, w);

            this.waveCenterY = h / 2;

            // Attempt to load specifically "1.gif" from the sounds folder
            try {
                File gifFile = new File(soundFolder, "1.gif");
                if (gifFile.exists() && gifFile.isFile()) {
                    gifIcon = new ImageIcon(gifFile.getAbsolutePath());
                    System.out.println("[ASSET] Loaded 1.gif for display: " + gifFile.getName());
                } else {
                    gifIcon = null;
                }
            } catch (Exception ex) {
                System.out.println("[ASSET] Failed to load 1.gif: " + ex.getMessage());
                gifIcon = null;
            }

            // Attempt to load paywall.jpg from the sounds folder (store original Image)
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

            // Mouse listener: block clicks while a sound is playing or paywall visible.
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (paywallVisible) {
                        System.out.println("[INPUT] Mouse click ignored while paywall visible.");
                        return;
                    }
                    if (soundPlaying.get()) {
                        System.out.println("[INPUT] Mouse click ignored while sound is playing.");
                        return;
                    }
                    if (triggerPaywallChance()) return;
                    piano.handleMousePress(e.getX(), e.getY());
                }
            });

            // Key listener: block key presses while a sound is playing OR while paywall is visible
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (paywallVisible) {
                        System.out.println("[INPUT] Key press ignored while paywall visible.");
                        return;
                    }
                    if (soundPlaying.get()) {
                        System.out.println("[INPUT] Key press ignored while sound is playing.");
                        return;
                    }
                    if (triggerPaywallChance()) return;
                    if (e.getKeyCode() == KeyEvent.VK_LEFT) piano.moveLeft();
                    if (e.getKeyCode() == KeyEvent.VK_RIGHT) piano.moveRight();
                }
            });

            setFocusable(true);

            timer = new Timer(16, ev -> {
                updateGame();
                updatePaywallAnimation();
                repaint();
            });
        }

        /**
         * Rolls the independent 40% chance to show the paywall.
         * Returns true if the paywall was scheduled/shown.
         */
        private boolean triggerPaywallChance() {
            if (paywallOriginalImage == null) return false;
            if (paywallVisible) return false;

            boolean show = random.nextDouble() < 0.40;
            if (!show) return false;

            paywallVisible = true;
            paywallSliding = true;
            paywallStartY = height + 8;

            int[] scaled = computePaywallScaledSize();
            int iconH = scaled[1];

            int centerX = width / 2;
            int waveY = (int) computeWaveY(centerX);
            int desiredY = Math.max(4, waveY - iconH / 2 - 20);
            paywallTargetY = Math.max(4, desiredY);
            paywallY = paywallStartY;
            paywallSlideStart = System.currentTimeMillis();
            paywallStayStart = 0L;

            System.out.println("[ASSET] Paywall triggered by user input; sliding in (targetY=" + paywallTargetY + ").");
            return true;
        }

        /**
         * Compute scaled width/height for the paywall image based on current panel width.
         * Returns int[]{scaledWidth, scaledHeight}.
         */
        private int[] computePaywallScaledSize() {
            if (paywallOriginalImage == null) return new int[]{0, 0};
            int panelW = getWidth() > 0 ? getWidth() : width;
            int targetW = (int) (panelW * 0.80);
            int origW = paywallOriginalImage.getWidth(null);
            int origH = paywallOriginalImage.getHeight(null);
            if (origW <= 0 || origH <= 0) return new int[]{targetW, targetW / 2};
            int targetH = (int) ((long) targetW * origH / origW);
            return new int[]{targetW, targetH};
        }

        /**
         * FIX: Build and cache a BufferedImage of the paywall at the required size.
         * Only re-renders when the target dimensions change. This replaces the old
         * getScaledInstance() call that was running every paintComponent() frame.
         */
        private BufferedImage getCachedPaywallImage(int targetW, int targetH) {
            if (paywallCachedImage == null
                    || paywallCachedWidth != targetW
                    || paywallCachedHeight != targetH) {

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

        public void start() {
            timer.start();
            SwingUtilities.invokeLater(() -> {
                if (!requestFocusInWindow()) requestFocus();
            });
        }

        /** Call this when the window is closed to cleanly shut down the sound thread pool. */
        public void stop() {
            timer.stop();
            soundExecutor.shutdownNow();
        }

        private void updateGame() {
            phase += 0.06;

            piano.updateShots();

            List<Piano.KeyShot> shots = new ArrayList<>(piano.getShots());
            Iterator<Piano.KeyShot> it = shots.iterator();

            while (it.hasNext()) {
                Piano.KeyShot s = it.next();
                int sx = s.getX() + s.getWidth() / 2;
                int sy = s.getY();

                double waveY = computeWaveY(sx);
                double slope = computeWaveSlope(sx);

                if (sy <= waveY) {
                    HitCategory category;
                    if (Math.abs(slope) < 0.02) category = HitCategory.SAME;
                    else if (slope > 0) category = HitCategory.HIGHER;
                    else category = HitCategory.LOWER;

                    playSoundForHit(s.getKeyIndex(), category, waveY);
                    removeShotFromPiano(s);
                }
            }
        }

        private void updatePaywallAnimation() {
            if (!paywallVisible) return;

            long now = System.currentTimeMillis();

            if (paywallSliding) {
                long elapsed = now - paywallSlideStart;
                double t = Math.min(1.0, (double) elapsed / (double) paywallSlideDuration);
                double ease = 1 - Math.pow(1 - t, 3);
                int newY = (int) (paywallStartY + (paywallTargetY - paywallStartY) * ease);
                paywallY = newY;
                if (t >= 1.0) {
                    paywallSliding = false;
                    paywallStayStart = now;
                }
            } else {
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

        private void removeShotFromPiano(Piano.KeyShot target) {
            Iterator<Piano.KeyShot> it = piano.getShots().iterator();
            while (it.hasNext()) {
                Piano.KeyShot s = it.next();
                if (s.getKeyIndex() == target.getKeyIndex() &&
                        s.getX() == target.getX() &&
                        s.getY() == target.getY()) {
                    it.remove();
                    return;
                }
            }
        }

        private double computeWaveY(int x) {
            return waveCenterY + waveAmp * Math.sin(waveFreq * x + phase);
        }

        private double computeWaveSlope(int x) {
            double y1 = computeWaveY(x - 1);
            double y2 = computeWaveY(x + 1);
            return (y2 - y1) / 2.0;
        }

        private enum HitCategory { HIGHER, LOWER, SAME }

        // FIX: Use soundExecutor (fixed thread pool of 3) instead of new Thread() each time
        private void playSoundForHit(int keyIndex, HitCategory category, double waveY) {
            soundExecutor.submit(() -> {
                try {
                    if (random.nextDouble() < 0.30) {
                        String wav = specialSounds[random.nextInt(specialSounds.length)];
                        File f = new File(soundFolder, wav);
                        playWavFile(f);
                        return;
                    }

                    double normalizedY = Math.max(0.0, Math.min(1.0,
                            (waveCenterY + waveAmp - waveY) / (2.0 * waveAmp)));

                    double baseFreq = 220.0 + normalizedY * 880.0;

                    switch (category) {
                        case HIGHER -> baseFreq *= 1.45;
                        case LOWER  -> baseFreq *= 0.65;
                        case SAME   -> baseFreq *= 1.0;
                    }

                    playChaoticToneRich(baseFreq);

                } catch (Exception ignored) {}
            });
        }

        /**
         * WAV loader with logging.
         */
        private void playWavFile(File f) {
            System.out.println("[AUDIO] Attempting to load WAV: " + f.getAbsolutePath());

            if (!f.exists()) {
                System.out.println("[AUDIO] ERROR: WAV file does NOT exist: " + f.getAbsolutePath());
                return;
            }

            currentWavName = f.getName();

            Clip clip = null;
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(f)) {

                AudioFormat baseFormat = ais.getFormat();
                AudioFormat decoded = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false
                );

                try (AudioInputStream dais = AudioSystem.getAudioInputStream(decoded, ais)) {
                    clip = AudioSystem.getClip();
                    final Clip finalClip = clip;

                    clip.addLineListener(event -> {
                        LineEvent.Type type = event.getType();
                        try {
                            if (type == LineEvent.Type.START) {
                                soundPlaying.set(true);
                                rainbowActive = "1.wav".equalsIgnoreCase(currentWavName);
                                gifVisible = rainbowActive && gifIcon != null;
                                System.out.println("[AUDIO] Playback START for: " + currentWavName + " | rainbowActive=" + rainbowActive + " gifVisible=" + gifVisible);
                            } else if (type == LineEvent.Type.STOP) {
                                long pos = finalClip.getMicrosecondPosition();
                                long len = finalClip.getMicrosecondLength();
                                if (len > 0 && pos + 1000 >= len) {
                                    try {
                                        if (finalClip.isOpen()) finalClip.close();
                                    } catch (Exception ex) {
                                        // ignore
                                    } finally {
                                        soundPlaying.set(false);
                                        rainbowActive = false;
                                        gifVisible = false;
                                        currentWavName = "";
                                        System.out.println("[AUDIO] Playback END for WAV (position >= length). Rainbow and GIF off; paywall state preserved if active.");
                                    }
                                } else {
                                    System.out.println("[AUDIO] STOP event received early (pos=" + pos + " len=" + len + "), waiting for real end.");
                                }
                            } else if (type == LineEvent.Type.CLOSE) {
                                soundPlaying.set(false);
                                rainbowActive = false;
                                gifVisible = false;
                                currentWavName = "";
                                System.out.println("[AUDIO] Clip CLOSED. Rainbow and GIF off.");
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            soundPlaying.set(false);
                            rainbowActive = false;
                            gifVisible = false;
                            paywallVisible = false;
                            paywallSliding = false;
                            currentWavName = "";
                        }
                    });

                    clip.open(dais);

                    System.out.println("[AUDIO] Successfully loaded WAV: " + f.getAbsolutePath());

                    if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                        gain.setValue(-6.0f);
                    }

                    clip.start();
                }

            } catch (Exception e) {
                System.out.println("[AUDIO] ERROR: Failed to decode or play WAV: " + f.getAbsolutePath());
                e.printStackTrace();
                soundPlaying.set(false);
                rainbowActive = false;
                gifVisible = false;
                paywallVisible = false;
                paywallSliding = false;
                currentWavName = "";
                if (clip != null && clip.isOpen()) {
                    try { clip.close(); } catch (Exception ex) { /* ignore */ }
                }
            }
        }

        /**
         * Rich chaotic piano synthesis.
         * FIX: Duration capped at 0.8s (was up to 1.5s) to reduce per-tone PCM buffer memory.
         */
        private void playChaoticToneRich(double baseFreq) {
            try {
                // FIX: capped at 0.8s — was 0.7-1.2s, each tone was up to ~132KB; now max ~70KB
                double durationSec = 0.5 + random.nextDouble() * 0.3; // 0.5 - 0.8s
                int sampleRate = 44100;
                int totalSamples = (int) (durationSec * sampleRate);
                byte[] buffer = new byte[totalSamples * 2]; // 16-bit mono

                double masterGain = 0.55;

                double detuneCents = (random.nextDouble() - 0.5) * 200.0;
                double detuneFactor = Math.pow(2.0, detuneCents / 1200.0);

                double driftRate = (random.nextDouble() - 0.5) * 0.6;
                double fmDepth = 5.0 + random.nextDouble() * 30.0;
                double fmRate = 0.5 + random.nextDouble() * 8.0;

                double[] harmonicAmps = new double[]{
                        1.0,
                        0.9 + random.nextDouble() * 0.8,
                        0.6 + random.nextDouble() * 0.6,
                        0.4 + random.nextDouble() * 0.6,
                        0.2 + random.nextDouble() * 0.4
                };

                int attackSamples  = (int) (0.005 * sampleRate) + random.nextInt((int) (0.03 * sampleRate));
                int decaySamples   = (int) (0.08  * sampleRate) + random.nextInt((int) (0.12 * sampleRate));
                double sustainLevel = 0.25 + random.nextDouble() * 0.45;
                int releaseSamples = (int) (0.35  * sampleRate) + random.nextInt((int) (0.3  * sampleRate)); // reduced max release

                for (int i = 0; i < totalSamples; i++) {
                    double t = i / (double) sampleRate;

                    double drift = driftRate * t * (0.5 + random.nextDouble() * 1.5);
                    double jitter = (random.nextDouble() - 0.5) * 0.5;
                    double freq = baseFreq * detuneFactor + drift + jitter;

                    double fm = Math.sin(2.0 * Math.PI * fmRate * t) * fmDepth;

                    double sample = 0.0;
                    for (int h = 0; h < harmonicAmps.length; h++) {
                        double harmonicFreq = freq * (h + 1) * (1.0 + (random.nextDouble() - 0.5) * 0.02);
                        double phaseMod = Math.sin(2.0 * Math.PI * (fmRate * (0.3 + h * 0.1)) * t) * (0.2 + random.nextDouble() * 0.8);
                        sample += harmonicAmps[h] * Math.sin(2.0 * Math.PI * harmonicFreq * t + phaseMod + fm * 0.01);
                    }

                    sample += 0.08 * Math.signum(Math.sin(2.0 * Math.PI * freq * 3.0 * t + random.nextDouble()));

                    double env;
                    if (i < attackSamples) {
                        double glitch = (random.nextDouble() < 0.06) ? (random.nextDouble() * 0.6) : 1.0;
                        env = (i / (double) attackSamples) * glitch;
                    } else if (i < attackSamples + decaySamples) {
                        double p = (i - attackSamples) / (double) decaySamples;
                        env = 1.0 + p * (sustainLevel - 1.0);
                    } else if (i < totalSamples - releaseSamples) {
                        env = sustainLevel;
                    } else {
                        double p = (i - (totalSamples - releaseSamples)) / (double) releaseSamples;
                        env = sustainLevel * (1.0 - p);
                    }

                    double ampMod = 0.95 + (random.nextDouble() - 0.5) * 0.12;
                    double out = sample * env * ampMod * masterGain;

                    if (out >  0.95) out =  0.95 + (out - 0.95) * 0.2;
                    if (out < -0.95) out = -0.95 + (out + 0.95) * 0.2;

                    short sVal = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, (int) (out * Short.MAX_VALUE)));
                    int idx = i * 2;
                    buffer[idx]     = (byte) (sVal & 0xff);
                    buffer[idx + 1] = (byte) ((sVal >> 8) & 0xff);
                }

                playPCM(buffer, sampleRate);

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        /**
         * Play raw PCM 16-bit little-endian mono buffer.
         */
        private void playPCM(byte[] pcm16, int sampleRate) {
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            rainbowActive = false;
            gifVisible = false;
            currentWavName = "";
            soundPlaying.set(true);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format, Math.min(pcm16.length, 65536));
                if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue(-6.0f);
                }
                line.start();
                int written = 0;
                while (written < pcm16.length) {
                    int toWrite = Math.min(4096, pcm16.length - written);
                    line.write(pcm16, written, toWrite);
                    written += toWrite;
                }
                line.drain();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                soundPlaying.set(false);
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // draw wave
            g.setColor(Color.CYAN);
            g.setStroke(new BasicStroke(2f));

            int prevX = 0;
            int prevY = (int) computeWaveY(prevX);

            for (int x = 1; x < getWidth(); x++) {
                int y = (int) computeWaveY(x);
                g.drawLine(prevX, prevY, x, y);
                prevX = x;
                prevY = y;
            }

            // draw piano and shots
            piano.draw(g);

            // Rainbow overlay while 1.wav is playing
            if (rainbowActive) {
                int px = piano.getX();
                int py = piano.getY();
                int pw = piano.getWidth();
                int ph = piano.getHeight();
                int keyCount = 20;
                try { keyCount = piano.getKeyCount(); } catch (Exception ignored) {}

                int keyW = Math.max(1, pw / keyCount);
                int keyH = Math.max(1, ph / 3);
                int topY  = py + ph - keyH;

                Composite oldComp = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));

                long time = System.currentTimeMillis();
                for (int i = 0; i < keyCount; i++) {
                    float hue = (float) (((time % 6000) / 6000.0) + (i * 0.06));
                    hue = hue - (float) Math.floor(hue);
                    float jitter = (float) ((random.nextDouble() - 0.5) * 0.06);
                    Color c = Color.getHSBColor(hue + jitter, 0.85f, 0.95f);
                    g.setColor(c);
                    int kx = px + i * keyW;
                    int kw = (i == keyCount - 1) ? (px + pw - kx) : keyW;
                    g.fillRect(kx, topY, kw, keyH);
                    g.setColor(new Color(0, 0, 0, 60));
                    g.setStroke(new BasicStroke(1f));
                    g.drawRect(kx, topY, kw, keyH);
                }

                g.setComposite(oldComp);
            }

            // GIF overlay while 1.wav is playing
            if (gifVisible && gifIcon != null) {
                int iconW  = gifIcon.getIconWidth();
                int iconH  = gifIcon.getIconHeight();
                int centerX = getWidth() / 2;
                int waveY  = (int) computeWaveY(centerX);
                int drawX  = Math.max(0, (getWidth() - iconW) / 2);
                int drawY  = Math.max(4, waveY - iconH / 2);
                gifIcon.paintIcon(this, g, drawX, drawY);
            }

            // FIX: Paywall drawn using cached BufferedImage — no allocation per frame
            if (paywallVisible && paywallOriginalImage != null) {
                int[] scaled  = computePaywallScaledSize();
                int iconW     = scaled[0];
                int iconH     = scaled[1];

                if (iconW > 0 && iconH > 0) {
                    BufferedImage img = getCachedPaywallImage(iconW, iconH);
                    int drawX = Math.max(0, (getWidth() - iconW) / 2);
                    int drawY = Math.max(-iconH, Math.min(getHeight(), paywallY));
                    g.drawImage(img, drawX, drawY, this);
                }
            }
        }
    }
}