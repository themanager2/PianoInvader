import java.awt.*;

/**
 * Wave: represents a horizontally moving sine wave.
 * Encapsulation: private fields with getters/setters.
 */
public class Wave {
    private double amplitude;   // pixels
    private double frequency;   // radians per pixel
    private double xOffset;     // horizontal phase offset
    private int centerY;
    private Color color;
    private int width;

    public Wave(int width, int centerY, double amplitude, double frequency) {
        this.width = width;
        this.centerY = centerY;
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.xOffset = 0;
        this.color = Color.CYAN;
    }

    public void update(double dx) {
        xOffset += dx;
    }

    /**
     * Get the y-position of the wave at a given x.
     */
    public int getY(int x) {
        double angle = (x + xOffset) * frequency;
        return (int) Math.round(centerY + amplitude * Math.sin(angle));
    }

    public void draw(Graphics g) {
        g.setColor(color);
        int prevX = 0;
        int prevY = getY(0);
        for (int x = 1; x < width; x++) {
            int y = getY(x);
            g.drawLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }

        // Draw center line and ±5% markers for visual debugging
        g.setColor(new Color(255, 255, 255, 80));
        g.drawLine(0, centerY, width, centerY);
        int offset = (int) Math.round(amplitude * 0.05);
        g.setColor(new Color(255, 255, 255, 40));
        g.drawLine(0, centerY - offset, width, centerY - offset);
        g.drawLine(0, centerY + offset, width, centerY + offset);
    }

    // Getters / Setters
    public double getAmplitude() { return amplitude; }
    public void setAmplitude(double amplitude) { this.amplitude = amplitude; }

    public double getFrequency() { return frequency; }
    public void setFrequency(double frequency) { this.frequency = frequency; }

    public double getXOffset() { return xOffset; }
    public void setXOffset(double xOffset) { this.xOffset = xOffset; }

    public int getCenterY() { return centerY; }
    public void setCenterY(int centerY) { this.centerY = centerY; }

    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
}
