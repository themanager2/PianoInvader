import java.io.File;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.*;

/**
 * AudioManager handles all sound playback:
 * - Loads and plays WAV files
 * - Generates synthetic tones
 * - Manages audio playback state
 * - Tracks when 1.wav is playing for rainbow/gif effects
 */
public class AudioManager {
    private final File soundFolder;
    private final String[] specialSounds = {"1.wav", "2.wav", "3.wav", "4.wav", "5.wav", "6.wav"};
    private final ExecutorService soundExecutor;
    private final Random random;
    // Prevent new sound triggers while a WAV is actively playing
    private final AtomicBoolean wavPlaying = new AtomicBoolean(false);
    
    // Track current WAV state
    private volatile String currentWavName = "";
    private volatile boolean isPlayingSpecialWav = false;
    private volatile long wavPlaybackStartTime = 0;
    private volatile long wavDurationMs = 0;
    
    // Callback for rainbow/gif effects
    private RainbowEffectListener rainbowListener;
    
    public interface RainbowEffectListener {
        void onWavStart(String wavName, long durationMs);
        void onWavEnd();
    }
    
    public AudioManager(File soundFolder) {
        this.soundFolder = soundFolder;
        this.soundExecutor = Executors.newFixedThreadPool(3);
        this.random = new Random();
    }
    
    public void setRainbowListener(RainbowEffectListener listener) {
        this.rainbowListener = listener;
    }
    
    public void playSound(int keyIndex, HitCategory category, double waveY, int waveCenterY, int waveAmp) {
        soundExecutor.submit(() -> {
            try {
                // If a WAV is playing, skip the synthetic tone entirely
                if (wavPlaying.get()) return;

                // 20% chance to trigger a random WAV instead of the synthetic tone
                if (random.nextDouble() < 0.20 && wavPlaying.compareAndSet(false, true)) {
                    String wav = specialSounds[random.nextInt(specialSounds.length)];
                    File f = new File(soundFolder, wav);
                    soundExecutor.submit(() -> playWavFile(f));
                    return;
                }

                // Otherwise play the synthetic tone
                double normalizedY = Math.max(0.0, Math.min(1.0,
                        (waveCenterY + waveAmp - waveY) / (2.0 * waveAmp)));

                double baseFreq = 220.0 + normalizedY * 880.0;

                if (category == HitCategory.HIGHER)      baseFreq *= 1.45;
                else if (category == HitCategory.LOWER)  baseFreq *= 0.65;

                playChaoticToneRich(baseFreq);

            } catch (Exception ignored) {}
        });
    }
    
    private void playWavFile(File f) {
        System.out.println("[AUDIO] Attempting to load WAV: " + f.getAbsolutePath());
        
        if (!f.exists()) {
            System.out.println("[AUDIO] ERROR: WAV file does NOT exist: " + f.getAbsolutePath());
            // Ensure we release the wav-playing lock so subsequent triggers are allowed
            endWavPlayback();
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
                            // Calculate duration and notify listener
                            long durationMs = finalClip.getMicrosecondLength() / 1000;
                            wavPlaybackStartTime = System.currentTimeMillis();
                            wavDurationMs = durationMs;
                            isPlayingSpecialWav = "1.wav".equalsIgnoreCase(currentWavName);
                            
                            if (isPlayingSpecialWav && rainbowListener != null) {
                                rainbowListener.onWavStart(currentWavName, durationMs);
                            }
                            System.out.println("[AUDIO] Playback START: " + currentWavName + 
                                    " | duration=" + durationMs + "ms | rainbow=" + isPlayingSpecialWav);
                        } else if (type == LineEvent.Type.STOP) {
                            long pos = finalClip.getMicrosecondPosition();
                            long len = finalClip.getMicrosecondLength();
                            if (len > 0 && pos + 1000 >= len) {
                                try {
                                    if (finalClip.isOpen()) finalClip.close();
                                } catch (Exception ignored) {}
                                finally {
                                    endWavPlayback();
                                }
                            }
                        } else if (type == LineEvent.Type.CLOSE) {
                            endWavPlayback();
                        }
                    } catch (Exception ex) {
                        System.out.println("[AUDIO] LineListener exception: " + ex.getMessage());
                        endWavPlayback();
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
            System.out.println("[AUDIO] ERROR: Failed to decode or play WAV: " + f.getAbsolutePath() + " -> " + e.getMessage());
            endWavPlayback();
            if (clip != null && clip.isOpen()) {
                try { clip.close(); } catch (Exception ex) { /* ignore */ }
            }
        }
    }
    
    private void endWavPlayback() {
        isPlayingSpecialWav = false;
        currentWavName = "";
        wavPlaybackStartTime = 0;
        wavDurationMs = 0;
        wavPlaying.set(false);
        if (rainbowListener != null) {
            rainbowListener.onWavEnd();
        }
        System.out.println("[AUDIO] WAV playback ended.");
    }
    
    public boolean isRainbowActive() {
        if (!isPlayingSpecialWav) return false;
        
        long elapsed = System.currentTimeMillis() - wavPlaybackStartTime;
        return elapsed < wavDurationMs + 100;  // Small buffer
    }
    
    public boolean isGifVisible() {
        return isRainbowActive() && "1.wav".equalsIgnoreCase(currentWavName);
    }
    
    /**
     * Rich chaotic piano synthesis.
     * Duration capped at 0.8s to reduce per-tone PCM buffer memory.
     */
    private void playChaoticToneRich(double baseFreq) {
        try {
            double durationSec = 0.5 + random.nextDouble() * 0.3;  // 0.5 - 0.8s
            int sampleRate = 44100;
            int totalSamples = (int) (durationSec * sampleRate);
            byte[] buffer = new byte[totalSamples * 2];  // 16-bit mono
            
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
            int releaseSamples = (int) (0.35  * sampleRate) + random.nextInt((int) (0.3  * sampleRate));
            
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
            System.out.println("[AUDIO] Error generating tone: " + ex.getMessage());
        }
    }
    
    /**
     * Play raw PCM 16-bit little-endian mono buffer.
     */
    private void playPCM(byte[] pcm16, int sampleRate) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
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
            System.out.println("[AUDIO] PCM playback error: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        soundExecutor.shutdownNow();
    }
}
