import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.*;

public class GamePanel extends JPanel {

    private final int width;
    private final Piano piano;

    // Wave parameters
    private double phase = 0;
    private final double waveFreq = 0.01;
    private final int waveAmp = 60;
    private final int waveCenterY;

    private final Timer timer;

    // Sound folder
    private final File soundFolder = new File("sounds");

    // Manager instances
    private final AudioManager audioManager;
    private final PaywallManager paywallManager;
    private final RainbowEffect rainbowEffect;

    public GamePanel(int w, int h) {
        this.width = w;
        setPreferredSize(new Dimension(w, h));
        setBackground(Color.BLACK);

        int pianoW = Math.min(700, w - 80);
        int pianoH = 160;
        int pianoX = (w - pianoW) / 2;
        int pianoY = h - pianoH - 20;
        this.piano = new Piano(pianoX, pianoY, pianoW, pianoH, w);

        this.waveCenterY = h / 2;

        // Initialize managers
        this.audioManager = new AudioManager(soundFolder);
        this.paywallManager = new PaywallManager(soundFolder, w, h);
        
        // Load GIF for rainbow effect
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
        
        // Connect audio manager to rainbow effect
        audioManager.setRainbowListener(rainbowEffect);

        // Mouse listener: only block clicks while paywall visible
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (paywallManager.isVisible()) {
                    System.out.println("[INPUT] Mouse click ignored while paywall visible.");
                    return;
                }
                
                // Trigger paywall chance
                int centerX = width / 2;
                int waveY = (int) computeWaveY(centerX);
                if (paywallManager.triggerPaywallChance(waveY)) {
                    return;
                }
                
                // Handle piano key press
                piano.handleMousePress(e.getX(), e.getY());
            }
        });

        // Key listener: only block keys while paywall visible
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (paywallManager.isVisible()) {
                    System.out.println("[INPUT] Key press ignored while paywall visible.");
                    return;
                }
                
                // Trigger paywall chance
                int centerX = width / 2;
                int waveY = (int) computeWaveY(centerX);
                if (paywallManager.triggerPaywallChance(waveY)) {
                    return;
                }
                
                // Handle arrow keys for piano movement
                if (e.getKeyCode() == KeyEvent.VK_LEFT) piano.moveLeft();
                if (e.getKeyCode() == KeyEvent.VK_RIGHT) piano.moveRight();
            }
        });

        setFocusable(true);

        timer = new Timer(16, ev -> {
            updateGame();
            paywallManager.update();
            repaint();
        });
    }

    public void start() {
        timer.start();
        SwingUtilities.invokeLater(() -> {
            if (!requestFocusInWindow()) requestFocus();
        });
    }

    public void stop() {
        timer.stop();
        audioManager.shutdown();
    }

    private void updateGame() {
        phase += 0.06;

        piano.updateShots();

        List<Piano.KeyShot> shots = new ArrayList<>(piano.getShots());
        for (Piano.KeyShot s : shots) {
            int sx = s.getX() + s.getWidth() / 2;
            int sy = s.getY();

            double waveY = computeWaveY(sx);
            double slope = computeWaveSlope(sx);

            if (sy <= waveY) {
                HitCategory category;
                if (Math.abs(slope) < 0.02) category = HitCategory.SAME;
                else if (slope > 0) category = HitCategory.HIGHER;
                else category = HitCategory.LOWER;

                // Play sound via AudioManager
                audioManager.playSound(s.getKeyIndex(), category, waveY, waveCenterY, waveAmp);
                removeShotFromPiano(s);
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

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw wave
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

        // Draw piano and shots
        piano.draw(g);

        // Draw rainbow effect for 1.wav
        if (rainbowEffect.isRainbowActive()) {
            rainbowEffect.drawRainbow(g, piano.getX(), piano.getY(), piano.getWidth(), piano.getHeight());
        }

        // Draw GIF for 1.wav
        if (rainbowEffect.isGifVisible()) {
            rainbowEffect.drawGif(g, getWidth(), getHeight(), computeWaveY(getWidth() / 2));
        }

        // Draw paywall
        paywallManager.draw(g, getWidth());
    }
}
