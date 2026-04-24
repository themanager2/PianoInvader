import javax.swing.*;
import javax.sound.midi.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class BadWaveInstrument extends JPanel
        implements ActionListener, MouseListener {

    // ---------------- Window ----------------
    private static final int WIDTH = 900;
    private static final int HEIGHT = 600;

    // ---------------- Wave ----------------
    private static final int WAVE_Y = 120;
    private static final int WAVE_AMPLITUDE = 30;
    private static final int WAVE_STEP = 40;
    private static final int WAVE_SPEED = 2;

    // ---------------- Keyboard ----------------
    private static final int KEYBOARD_Y = 460;
    private static final int KEY_HEIGHT = 120;
    private static final int NUM_KEYS = 14;

    // ---------------- Projectile ----------------
    private static final int PROJECTILE_SPEED = 6;

    // ---------------- Sound ----------------
    private static final double RANDOM_NOTE_CHANCE = 0.3;

    // ---------------- State ----------------
    private final List<Projectile> projectiles = new ArrayList<>();
    private final Random random = new Random();

    private Wave wave;
    private javax.swing.Timer timer;

    private Synthesizer synth;
    private MidiChannel channel;

    // ---------------- Main ----------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Bad Wave Instrument");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new BadWaveInstrument());
            frame.pack();
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }

    // ---------------- Constructor ----------------
    public BadWaveInstrument() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(245, 242, 235));
        addMouseListener(this);

        // IMPORTANT: initialize everything BEFORE repainting
        wave = new Wave();
        initMidi();

        timer = new javax.swing.Timer(16, this);
        timer.start();
    }

    // ---------------- MIDI ----------------
    private void initMidi() {
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            channel = synth.getChannels()[0];
            channel.programChange(0); // piano
        } catch (Exception e) {
            System.err.println("MIDI unavailable");
        }
    }

    // ---------------- Update loop ----------------
    @Override
    public void actionPerformed(ActionEvent e) {
        wave.update();

        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            p.update();

            if (wave.hit(p.x, p.y)) {
                playNote(p.baseNote);
                it.remove();
            } else if (p.y < 0) {
                it.remove();
            }
        }

        repaint();
    }

    // ---------------- Sound ----------------
    private void playNote(int baseNote) {
        if (channel == null) return;

        int note;
        if (random.nextDouble() < RANDOM_NOTE_CHANCE) {
            note = 48 + random.nextInt(36);
        } else {
            note = baseNote;
        }

        channel.noteOn(note, 90);

        new javax.swing.Timer(120, e -> channel.noteOff(note)).start();
    }

    // ---------------- Drawing ----------------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        drawWave(g2);
        drawKeyboard(g2);
        drawProjectiles(g2);
    }

    private void drawWave(Graphics2D g2) {
        g2.setStroke(new BasicStroke(3));
        g2.setColor(Color.BLACK);

        int startX = wave.offset - WAVE_STEP * 2;

        int lastX = startX;
        int lastY = WAVE_Y;

        for (int i = 0; i < WIDTH / WAVE_STEP + 6; i++) {
            int x = startX + i * WAVE_STEP;
            int y = (i % 2 == 0)
                    ? WAVE_Y - WAVE_AMPLITUDE
                    : WAVE_Y + WAVE_AMPLITUDE;

            g2.drawLine(lastX, lastY, x, y);
            lastX = x;
            lastY = y;
        }
    }

    private void drawKeyboard(Graphics2D g2) {
        int keyWidth = WIDTH / NUM_KEYS;
        int centerKey = NUM_KEYS / 2;

        for (int i = 0; i < NUM_KEYS; i++) {
            int xLeft = i * keyWidth;
            int xRight = xLeft + keyWidth;

            // Distance from center controls height
            int distanceFromCenter = Math.abs(i - centerKey);

            double heightFactor = 1.0 - (distanceFromCenter / (double) centerKey);
            int topY = KEYBOARD_Y + (int)(KEY_HEIGHT * (1.0 - heightFactor));

            Polygon keyShape = new Polygon(
                    new int[] { xLeft, xRight, xRight, xLeft },
                    new int[] { KEYBOARD_Y + KEY_HEIGHT, KEYBOARD_Y + KEY_HEIGHT, topY, topY },
                    4
            );

            g2.setColor(i % 2 == 0 ? Color.WHITE : new Color(220, 220, 220));
            g2.fillPolygon(keyShape);

            g2.setColor(Color.BLACK);
            g2.drawPolygon(keyShape);
        }
    }


    private void drawProjectiles(Graphics2D g2) {
        g2.setColor(Color.BLACK);
        for (Projectile p : projectiles) {
            g2.drawLine(p.x, p.y, p.x, p.y + 12);
        }
    }

    // ---------------- Mouse ----------------
    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getY() >= KEYBOARD_Y) {
            int keyWidth = WIDTH / NUM_KEYS;
            int keyIndex = e.getX() / keyWidth;
            int midiNote = 60 + keyIndex;

            projectiles.add(new Projectile(
                    e.getX(),
                    KEYBOARD_Y,
                    midiNote
            ));
        }
    }

    public void mouseReleased(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}

    // ---------------- Inner Classes ----------------
    private static class Projectile {
        int x, y;
        int baseNote;

        Projectile(int x, int y, int baseNote) {
            this.x = x;
            this.y = y;
            this.baseNote = baseNote;
        }

        void update() {
            y -= PROJECTILE_SPEED;
        }
    }

    private static class Wave {
        int offset = 0;
        int direction = 1;

        void update() {
            offset += direction * WAVE_SPEED;
            if (offset > 40 || offset < -40) {
                direction *= -1;
            }
        }

        boolean hit(int x, int y) {
            return Math.abs(y - WAVE_Y) < 10;
        }
    }
}
