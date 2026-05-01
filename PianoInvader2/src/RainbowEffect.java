import java.awt.*;
import java.util.Random;
import javax.swing.ImageIcon;

/**
 * RainbowEffect manages:
 * - Rainbow overlay on piano keys while 1.wav is playing
 * - GIF display while 1.wav is playing
 * - Effects persist for the full duration of the WAV
 */
public class RainbowEffect implements AudioManager.RainbowEffectListener {
    private volatile boolean rainbowActive = false;
    private volatile boolean gifVisible = false;
    private volatile ImageIcon gifIcon = null;
    private final Random random = new Random();
    
    public RainbowEffect(ImageIcon gifIcon) {
        this.gifIcon = gifIcon;
    }
    
    @Override
    public void onWavStart(String wavName, long durationMs) {
        if ("1.wav".equalsIgnoreCase(wavName)) {
            rainbowActive = true;
            gifVisible = (gifIcon != null);
            System.out.println("[EFFECT] Rainbow and GIF activated for " + wavName + 
                    " (duration: " + durationMs + "ms)");
        }
    }
    
    @Override
    public void onWavEnd() {
        rainbowActive = false;
        gifVisible = false;
        System.out.println("[EFFECT] Rainbow and GIF deactivated.");
    }
    
    public boolean isRainbowActive() {
        return rainbowActive;
    }
    
    public boolean isGifVisible() {
        return gifVisible && gifIcon != null;
    }
    
    public void drawRainbow(Graphics2D g, int pianoX, int pianoY, int pianoWidth, int pianoHeight) {
        if (!rainbowActive) return;
        
        int keyCount = 20;  // Default, should match piano key count
        int keyW = Math.max(1, pianoWidth / keyCount);
        int keyH = Math.max(1, pianoHeight / 3);
        int topY = pianoY + pianoHeight - keyH;
        
        Composite oldComp = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
        
        long time = System.currentTimeMillis();
        for (int i = 0; i < keyCount; i++) {
            float hue = (float) (((time % 6000) / 6000.0) + (i * 0.06));
            hue = hue - (float) Math.floor(hue);
            float jitter = (float) ((random.nextDouble() - 0.5) * 0.06);
            Color c = Color.getHSBColor(hue + jitter, 0.85f, 0.95f);
            g.setColor(c);
            int kx = pianoX + i * keyW;
            int kw = (i == keyCount - 1) ? (pianoX + pianoWidth - kx) : keyW;
            g.fillRect(kx, topY, kw, keyH);
            g.setColor(new Color(0, 0, 0, 60));
            g.setStroke(new BasicStroke(1f));
            g.drawRect(kx, topY, kw, keyH);
        }
        
        g.setComposite(oldComp);
    }
    
    public void drawGif(Graphics2D g, int panelWidth, int panelHeight, double waveY) {
        if (!gifVisible || gifIcon == null) return;
        
        int iconW = gifIcon.getIconWidth();
        int iconH = gifIcon.getIconHeight();
        int centerX = panelWidth / 2;
        int drawX = Math.max(0, (panelWidth - iconW) / 2);
        int drawY = Math.max(4, (int) waveY - iconH / 2);
        gifIcon.paintIcon(null, g, drawX, drawY);
    }
}
