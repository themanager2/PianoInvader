import javax.swing.*;

/**
 * Piano Invader - Main Application
 *
 * Object-Oriented Architecture:
 * - AudioManager: Handles WAV playback and synthetic tone generation
 * - PaywallManager: Manages paywall display, animation, and 30% triggering
 * - RainbowEffect: Manages rainbow overlay and GIF display for 1.wav
 * - GamePanel: Main game loop and rendering
 *
 * Key Features:
 * - 30% chance for paywall to appear on user interaction
 * - 50% chance for WAV files to play (40% synthetic tones)
 * - Rainbow and GIF effects persist for full duration of 1.wav
 * - Input NOT blocked during sound playback (only blocked during paywall)
 * - Memory optimized with cached images and fixed thread pool
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

}