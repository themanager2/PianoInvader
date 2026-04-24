import javax.swing.*;
import javax.sound.midi.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class HorribleWaveInstrument extends JPanel
        implements ActionListener, MouseListener {

    // ---------------- Window ----------------
    private static final int WIDTH = 900;
    private static final int HEIGHT = 600;

    // ---------------- Wave ----------------
    private static final int WAVE_Y = 120;
    private static final int WAVE_AMPLITUDE = 40;
    private static final int WAVE_STEP = 30;
    private static final int WAVE_SPEED = 4;

    // ---------------- Keyboard ----------------
    private static final int KEYBOARD_Y = 450;
    private static final int KEY_HEIGHT = 150;
    private static final int NUM_KEYS = 14;

    // ---------------- Projectile ----------------
    private static final int PROJECTILE_SPEED = 7;

    // ---------------- State ----------------
    private final List<Projectile> projectiles = new ArrayList<>();
    private final Random random = new Random();

    private Wave wave;
    private javax.swing.Timer timer;

    // ---------------- MIDI ----------------
    private Synthesizer synth;
    private MidiChannel channel;

    // ---------------- Sound Effects ----------------
    private Clip fart;
    private Clip horn;
    private Clip buzzer;

    // ---------------- Main ----------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Horrible Wave Instrument");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new HorribleWaveInstrument());
            frame.pack();
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }

    // ---------------- Constructor ----------------
    public HorribleWaveInstrument() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(240, 230, 210));
        addMouseListener(this);

        wave = new Wave();
        initMidi();

        // Load horrible sounds
        fart   = loadClip("sounds/fart.wav");
        horn   = loadClip("sounds/horn.wav");
        buzzer = loadClip("sounds/buzzer.wav");

        timer = new javax.swing.Timer(16, this);
        timer.start();
    }

    // ---------------- File-based Clip Loader ----------------
    private Clip loadClip(String path) {
        try {
            AudioInputStream in = AudioSystem.getAudioInputStream(new File(path));
            Clip clip = AudioSystem.getClip();
            clip.open(in);
            return clip;
        } catch (Exception e) {
            System.err.println("Failed to load: " + path);
            return null;
        }
    }

    private void playClip(Clip clip) {
        if (clip == null) return;
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    // ---------------- MIDI ----------------
    private void initMidi() {
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            channel = synth.getChannels()[0];
            channel.programChange(0);
        } catch (Exception e) {
            channel = null;
        }
    }

    // ---------------- Update Loop ----------------
    @Override
    public void actionPerformed(ActionEvent e) {
        wave.update();

        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            p.update();

            if (wave.hit(p.x, p.y)) {
                playSomethingHorrible(p.baseNote);
                it.remove();
            } else if (p.y < 0) {
                playClip(buzzer); // punishment
                it.remove();
            }
        }

        repaint();
    }

    // ---------------- Horrible Sound Logic ----------------
    private void playSomethingHorrible(int baseNote) {

        double chaos = random.nextDouble();

        // 40% sound effects
        if (chaos < 0.15) {
            playClip(fart);
            return;
        }
        if (chaos < 0.30) {
            playClip(horn);
            return;
        }
        if (chaos < 0.40) {
            playClip(buzzer);
            return;
        }

        // Otherwise: cursed musical note
        if (channel == null) return;

        int note = (chaos < 0.7)
                ? baseNote + random.nextInt(8) - 4
                : 40 + random.nextInt(40);

        channel.noteOn(note, 100);
        new javax.swing.Timer(180, e -> channel.noteOff(note)).start();
    }

    // ---------------- Drawing ----------------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        drawWave(g2);
        drawTriangleKeyboard(g2);
        drawProjectiles(g2);
    }

    private void drawWave(Graphics2D g2) {
        g2.setStroke(new BasicStroke(4));
        g2.setColor(Color.BLACK);

        int startX = wave.offset;

        int lastX = startX;
        int lastY = WAVE_Y;

        for (int i = 0; i < WIDTH / WAVE_STEP + 4; i++) {
            int x = startX + i * WAVE_STEP;
            int y = WAVE_Y +
                    (int)(Math.sin(i + wave.phase) * WAVE_AMPLITUDE);

            g2.drawLine(lastX, lastY, x, y);
            lastX = x;
            lastY = y;
        }
    }

    // ---------------- Triangular Keyboard ----------------
    private void drawTriangleKeyboard(Graphics2D g2) {
        int keyWidth = WIDTH / NUM_KEYS;
        int center = NUM_KEYS / 2;

        for (int i = 0; i < NUM_KEYS; i++) {
            int x1 = i * keyWidth;
            int x2 = x1 + keyWidth;

            double t = 1.0 - (Math.abs(i - center) / (double) center);
            int topY = KEYBOARD_Y + (int)((1 - t) * KEY_HEIGHT);

            Polygon key = new Polygon(
                    new int[]{ x1, x2, x2, x1 },
                    new int[]{ KEYBOARD_Y + KEY_HEIGHT, KEYBOARD_Y + KEY_HEIGHT, topY, topY },
                    4
            );

            g2.setColor(i % 2 == 0 ? Color.WHITE : new Color(200,200,200));
            g2.fillPolygon(key);
            g2.setColor(Color.BLACK);
            g2.drawPolygon(key);
        }
    }

    private void drawProjectiles(Graphics2D g2) {
        for (Projectile p : projectiles) {
            int jitter = random.nextInt(3) - 1;
            g2.drawLine(p.x + jitter, p.y, p.x - jitter, p.y + 12);
        }
    }

    // ---------------- Mouse ----------------
    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getY() >= KEYBOARD_Y) {
            int keyWidth = WIDTH / NUM_KEYS;
            int key = Math.min(e.getX() / keyWidth, NUM_KEYS - 1);
            projectiles.add(new Projectile(e.getX(), KEYBOARD_Y, 60 + key));
        }
    }

    public void mouseReleased(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}

    // ---------------- Inner Classes ----------------
    private static class Projectile {
        int x, y, baseNote;
        Projectile(int x, int y, int baseNote) {
            this.x = x;
            this.y = y;
            this.baseNote = baseNote;
        }
        void update() { y -= PROJECTILE_SPEED; }
    }

    private static class Wave {
        int offset = 0;
        int phase = 0;
        void update() {
            offset -= WAVE_SPEED;
            phase++;
        }
        boolean hit(int x, int y) {
            return Math.abs(y - WAVE_Y) < 12;
        }
    }
}