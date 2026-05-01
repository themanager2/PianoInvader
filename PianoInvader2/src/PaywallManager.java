import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.swing.ImageIcon;

/**
 * PaywallManager handles the paywall image display, animation, and triggering.
 * Manages:
 * - Loading paywall.jpg
 * - 7% chance to display on user interaction
 * - Sliding animation (800ms)
 * - 5 second display duration
 */
public class PaywallManager {
    private final File soundFolder;
    private final Random random;
    
    private volatile Image paywallOriginalImage = null;
    private volatile boolean paywallVisible = false;
    private volatile boolean paywallSliding = false;
    private volatile int paywallY = 0;
    private volatile int paywallTargetY = 0;
    private volatile long paywallSlideStart = 0L;
    private volatile long paywallSlideDuration = 800L;
    private volatile long paywallStayStart = 0L;
    private volatile long paywallStayDuration = 5000L;
    private volatile int paywallStartY = 0;
    
    // Cached scaled image to avoid per-frame allocation
    private volatile BufferedImage paywallCachedImage = null;
    private volatile int paywallCachedWidth = -1;
    private volatile int paywallCachedHeight = -1;
    
    private final int panelWidth;
    private final int panelHeight;
    
    public PaywallManager(File soundFolder, int panelWidth, int panelHeight) {
        this.soundFolder = soundFolder;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.random = new Random();
        
        loadPaywall();
    }
    
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
     * 20% chance to show the paywall when user interacts.
     */
    public boolean triggerPaywallChance(int waveY) {
        if (paywallOriginalImage == null) return false;
        if (paywallVisible) return false;

        boolean show = random.nextDouble() < 0.07;  // 7% chance
        if (!show) return false;
        
        paywallVisible = true;
        paywallSliding = true;
        paywallStartY = panelHeight + 8;
        
        int[] scaled = computePaywallScaledSize();
        int iconH = scaled[1];
        
        int desiredY = Math.max(4, waveY - iconH / 2 - 20);
        paywallTargetY = Math.max(4, desiredY);
        paywallY = paywallStartY;
        paywallSlideStart = System.currentTimeMillis();
        paywallStayStart = 0L;
        
        System.out.println("[ASSET] Paywall triggered by user input; sliding in (targetY=" + paywallTargetY + ").");
        return true;
    }
    
    public void update() {
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
    
    private int[] computePaywallScaledSize() {
        if (paywallOriginalImage == null) return new int[]{0, 0};
        int targetW = (int) (panelWidth * 0.80);
        int origW = paywallOriginalImage.getWidth(null);
        int origH = paywallOriginalImage.getHeight(null);
        if (origW <= 0 || origH <= 0) return new int[]{targetW, targetW / 2};
        int targetH = (int) ((long) targetW * origH / origW);
        return new int[]{targetW, targetH};
    }
    
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
    
    public void draw(Graphics2D g, int panelWidth) {
        if (!paywallVisible || paywallOriginalImage == null) return;
        
        int[] scaled = computePaywallScaledSize();
        int iconW = scaled[0];
        int iconH = scaled[1];
        
        if (iconW > 0 && iconH > 0) {
            BufferedImage img = getCachedPaywallImage(iconW, iconH);
            int drawX = Math.max(0, (panelWidth - iconW) / 2);
            int drawY = Math.max(-iconH, Math.min(panelHeight, paywallY));
            g.drawImage(img, drawX, drawY, null);
        }
    }
    
    public boolean isVisible() {
        return paywallVisible;
    }
}
