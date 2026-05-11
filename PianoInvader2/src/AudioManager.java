import java.io.File;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.*;

/**
 * AudioManager - Centralises all sound playback for Piano Invader.
 *
 * Two playback paths
 * ──────────────────
 * 1. WAV playback  – When a shot hits the wave there is a 20 % chance that a
 *    random WAV file from the sounds/ folder is played instead of a synthetic
 *    tone.  The four candidate files are: 1.wav, 2.wav, 3.wav, 4.wav.
 *    Exactly one WAV may play at a time; an AtomicBoolean flag prevents
 *    concurrent WAV triggers.  WAV audio is decoded to signed 16-bit PCM
 *    before being handed to a Clip so that any source format is supported.
 *
 * 2. Synthetic tone playback – For the remaining 80 % of shot events a rich,
 *    chaotic piano timbre is synthesised in real time with:
 *      • Frequency modulation (FM) for evolving timbre
 *      • Five harmonics with randomised amplitudes
 *      • Pitch drift, jitter, and random detune in cents
 *      • ADSR envelope with randomised attack/decay/sustain/release
 *      • Light soft-clip limiting to prevent integer overflow
 *    The resulting PCM buffer is written directly to a SourceDataLine.
 *
 * Rainbow / GIF notification
 * ──────────────────────────
 * When 1.wav begins playing, AudioManager fires onWavStart() on the registered
 * RainbowEffectListener so the visual effects layer can activate.  When the
 * clip finishes (or errors), onWavEnd() is fired to deactivate the effects.
 *
 * Threading model
 * ───────────────
 * All audio operations run on a dedicated fixed-size thread pool of 3 threads
 * so the Swing EDT (and therefore rendering) is never blocked by I/O or PCM
 * generation.  The pool is shut down gracefully via shutdown().
 */
public class AudioManager {

    /** Directory from which WAV files are loaded (typically "sounds/"). */
    private final File soundFolder;

    /**
     * The set of WAV filenames that may be chosen at random during gameplay.
     * All four files must exist inside soundFolder for reliable playback.
     */
    private final String[] specialSounds = {"1.wav", "2.wav", "3.wav", "4.wav"};

    /**
     * Fixed-size thread pool used for all audio tasks.
     * Keeping the pool small (3 threads) limits concurrent PCM generation
     * and prevents unbounded memory growth from stacked audio tasks.
     */
    private final ExecutorService soundExecutor;

    /** Shared Random instance used for probabilistic decisions and synthesis randomisation. */
    private final Random random;

    /**
     * Guards against overlapping WAV file playbacks.
     * Set to true when a WAV clip starts and reset to false when it ends.
     * compareAndSet() is used when deciding to start a new WAV so that only
     * one thread can "win" the transition from false → true.
     */
    private final AtomicBoolean wavPlaying = new AtomicBoolean(false);

    // ── Current WAV tracking ───────────────────────────────────────
    // These volatile fields are written on audio threads and read on the EDT
    // (for isRainbowActive() / isGifVisible() polling).  volatile is sufficient
    // here because each field is written atomically and no compound check-then-act
    // is performed on them from outside this class.

    /** Filename of the WAV currently being played (empty string when idle). */
    private volatile String currentWavName = "";

    /** True only while 1.wav is actively playing (used to gate rainbow/GIF). */
    private volatile boolean isPlayingSpecialWav = false;

    /** System.currentTimeMillis() timestamp captured the moment the clip starts. */
    private volatile long wavPlaybackStartTime = 0;

    /** Total duration of the current clip in milliseconds, from Clip.getMicrosecondLength(). */
    private volatile long wavDurationMs = 0;

    /**
     * Optional listener notified when 1.wav starts or any WAV ends.
     * Set by GamePanel via setRainbowListener().
     */
    private RainbowEffectListener rainbowListener;

    // ────────────────────────────────────────────────────────

    /**
     * Callback interface for visual effects that depend on WAV playback state.
     *
     * Implemented by RainbowEffect and registered via setRainbowListener().
     * Callbacks are fired from the audio thread pool, NOT the EDT, so
     * implementations must be thread-safe (RainbowEffect uses volatile flags).
     */
    public interface RainbowEffectListener {
        /**
         * Called on the audio thread immediately after a WAV clip starts.
         *
         * @param wavName    filename of the WAV that just started (e.g. "1.wav")
         * @param durationMs total clip length in milliseconds
         */
        void onWavStart(String wavName, long durationMs);

        /**
         * Called on the audio thread when a WAV clip finishes, is closed,
         * or fails with an error.  Always paired with a preceding onWavStart().
         */
        void onWavEnd();
    }
    
    /**
     * Constructs an AudioManager that loads sound files from the given folder.
     *
     * A fixed thread pool of 3 threads is created here.  Using a fixed pool
     * (rather than a cached pool) prevents a burst of shot events from
     * spawning an unbounded number of threads that each allocate a large PCM
     * buffer.
     *
     * @param soundFolder directory containing the WAV and GIF assets
     */
    public AudioManager(File soundFolder) {
        this.soundFolder = soundFolder;
        this.soundExecutor = Executors.newFixedThreadPool(3);
        this.random = new Random();
    }
    
    /**
     * Registers the listener that receives WAV start/end callbacks.
     *
     * Should be called once during setup (from GamePanel's constructor)
     * before any user interaction can occur.
     *
     * @param listener the RainbowEffect instance that handles visual callbacks
     */
    public void setRainbowListener(RainbowEffectListener listener) {
        this.rainbowListener = listener;
    }
    
    /**
     * Triggers a sound event in response to a shot hitting the wave.
     *
     * The method runs entirely on the soundExecutor thread pool (not the EDT)
     * and follows this decision tree:
     *
     *   1. If a WAV is already playing, skip entirely (return early).
     *      This keeps a playing WAV uninterrupted and avoids spawning extra
     *      threads while blocking synthesis for the duration of the WAV.
     *
     *   2. With 20 % probability, attempt to start a random WAV file.
     *      compareAndSet(false, true) on wavPlaying ensures only one call can
     *      win this race, preventing two WAVs from starting simultaneously.
     *
     *   3. Otherwise, synthesise a chaotic tone whose base pitch is derived
     *      from the shot's y-position relative to the wave's range, then
     *      shifted up (HIGHER) or down (LOWER) based on the wave's slope.
     *
     * Pitch derivation for the synthetic tone:
     *   normalizedY = clamp((waveCenterY + waveAmp - waveY) / (2 * waveAmp), 0, 1)
     *   baseFreq    = 220 Hz + normalizedY * 880 Hz   (range: 220– 1100 Hz)
     *   When the shot hits the top of the wave's range, normalizedY ≈ 1 and
     *   the tone is higher in pitch.  Bottom of the range gives a lower pitch.
     *
     * @param keyIndex   index of the piano key (0–based) that was fired;
     *                   passed through to AudioManager.playSound for logging
     * @param category   slope classification at the collision point
     * @param waveY      y-pixel of the wave at the shot's x-coordinate
     * @param waveCenterY vertical centre of the wave's oscillation in pixels
     * @param waveAmp    amplitude of the wave in pixels
     */
    public void playSound(int keyIndex, HitCategory category, double waveY, int waveCenterY, int waveAmp) {
        soundExecutor.submit(() -> {
            try {
                // Guard: skip if a WAV clip is already occupying the audio output.
                if (wavPlaying.get()) return;

                // 20 % chance: play a random WAV file instead of synthesising a tone.
                // compareAndSet atomically reserves the WAV slot so only one thread wins.
                if (random.nextDouble() < 0.20 && wavPlaying.compareAndSet(false, true)) {
                    String wav = specialSounds[random.nextInt(specialSounds.length)];
                    File f = new File(soundFolder, wav);
                    // Submit WAV playback as a separate task so this task can return
                    // immediately, freeing the thread pool slot for future events.
                    soundExecutor.submit(() -> playWavFile(f));
                    return;
                }

                // Normalise the shot's y-position to [0, 1] within the wave's amplitude range.
                // 0 = bottom of wave range (lower pitch), 1 = top (higher pitch).
                double normalizedY = Math.max(0.0, Math.min(1.0,
                        (waveCenterY + waveAmp - waveY) / (2.0 * waveAmp)));

                // Map normalised position to a frequency in the human-audible piano range.
                double baseFreq = 220.0 + normalizedY * 880.0;

                // Apply slope-based pitch shift.
                if (category == HitCategory.HIGHER)      baseFreq *= 1.45;  // ~major sixth up
                else if (category == HitCategory.LOWER)  baseFreq *= 0.65;  // ~minor third down

                playChaoticToneRich(baseFreq);

            } catch (Exception ignored) {}
        });
    }
    
    /**
     * Loads, decodes, and streams a WAV file using the javax.sound.sampled Clip API.
     *
     * Audio format handling
     * ─────────────────────
     * The file is first opened with AudioSystem.getAudioInputStream() which
     * returns the raw format (may be mu-law, ADPCM, or any other WAV codec).
     * A target PCM_SIGNED 16-bit format is defined at the same sample rate and
     * channel count, and a second AudioInputStream is obtained via format
     * conversion.  This guarantees the Clip receives raw PCM regardless of the
     * source codec.
     *
     * Clip lifecycle and LineListener
     * ───────────────────────────────
     * A LineListener is attached before opening the clip:
     *   START event – records playback start time, computes duration, sets
     *                 isPlayingSpecialWav, and calls rainbowListener.onWavStart()
     *                 if this is 1.wav.
     *   STOP event  – checks whether playback reached the natural end
     *                 (position + 1000µs ≥ length) before calling endWavPlayback()
     *                 to avoid false stops from pause/loop events.
     *   CLOSE event – safety net; always calls endWavPlayback() in case the
     *                 STOP event was missed.
     *
     * Volume
     * ──────
     * If the Clip's hardware line supports MASTER_GAIN, it is set to −6 dBFS
     * to match the level of the synthetic tones.
     *
     * Error handling
     * ─────────────
     * Any exception releases the wavPlaying lock via endWavPlayback() so the
     * game is not permanently silenced by a corrupt or missing file.
     *
     * @param f the WAV file to play; existence is verified before decoding
     */
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
                            // Capture timing info and notify the rainbow listener.
                            // getMicrosecondLength() / 1000 converts to milliseconds.
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
                            // Only treat this as end-of-playback if the position is
                            // within 1 ms of the clip's full length.  This filters out
                            // spurious STOP events from pause() or loop resets.
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
                            // Safety net: if STOP was not caught, CLOSE always resets state.
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
    
    /**
     * Resets all WAV-playback tracking state and releases the wavPlaying lock.
     *
     * Called from the LineListener on STOP/CLOSE events and from error
     * handlers.  After this method returns, playSound() will again accept
     * new WAV triggers.  The rainbowListener is notified so visual effects
     * deactivate in sync with the audio ending.
     */
    private void endWavPlayback() {
        isPlayingSpecialWav = false;
        currentWavName = "";
        wavPlaybackStartTime = 0;
        wavDurationMs = 0;
        wavPlaying.set(false);             // re-open the door for the next WAV trigger
        if (rainbowListener != null) {
            rainbowListener.onWavEnd();
        }
        System.out.println("[AUDIO] WAV playback ended.");
    }
    
    /**
     * Returns true if the rainbow overlay should currently be displayed.
     *
     * The rainbow is active while 1.wav is playing plus a 100 ms buffer
     * after the clip officially ends, to smooth the visual transition.
     *
     * Note: This method is polled from the EDT (GamePanel.paintComponent)
     * while writes come from audio threads; volatile is sufficient because
     * each field is written atomically.
     *
     * @return true if the rainbow effect is currently active
     */
    public boolean isRainbowActive() {
        if (!isPlayingSpecialWav) return false;
        
        long elapsed = System.currentTimeMillis() - wavPlaybackStartTime;
        return elapsed < wavDurationMs + 100;  // 100 ms grace period
    }
    
    /**
     * Returns true if the GIF overlay should currently be rendered.
     *
     * The GIF is shown only while 1.wav is playing (i.e. both rainbow is
     * active AND the current WAV is specifically 1.wav).
     *
     * @return true if the GIF should be drawn this frame
     */
    public boolean isGifVisible() {
        return isRainbowActive() && "1.wav".equalsIgnoreCase(currentWavName);
    }
    
    /**
     * Synthesises a rich, chaotic piano-like tone at the given base frequency.
     *
     * Synthesis chain
     * ───────────────
     * Each call produces a unique sound by randomising the parameters below
     * before sample generation begins:
     *
     *   Detuning  – A random cent offset (±100 cents) is applied via
     *               detuneFactor = 2^(cents/1200), simulating the natural
     *               inharmonicity of a real piano string.
     *
     *   Pitch drift – driftRate linearly shifts the frequency over time,
     *                 mimicking a slightly out-of-tune instrument.
     *
     *   FM synthesis – A low-frequency oscillator (fmRate, 0.5–8 Hz) modulates
     *                  the phase of the carrier at depth fmDepth (5–35 Hz),
     *                  creating subtle tremolo and timbral movement.
     *
     *   Harmonics – Five harmonics at integer multiples of the base frequency
     *               with randomised amplitudes produce a full, piano-like
     *               spectrum.  Each harmonic also has its own slow phase
     *               modulation for evolving overtones.
     *
     *   Square-wave bite – A small proportion (8 %) of a hard-clipped square
     *                      wave adds mid-range bite similar to a piano hammer
     *                      striking a string.
     *
     *   ADSR envelope
     *     Attack  – 5–35 ms  (fast transient, occasional random glitch)
     *     Decay   – 80–200 ms (fall from peak to sustain level)
     *     Sustain – 25–70 % of peak amplitude while held
     *     Release – 350–650 ms (gentle fade to silence)
     *
     *   Soft clipping – Samples exceeding ±0.95 are compressed rather than
     *                   hard-clipped, preventing harsh distortion while
     *                   still limiting amplitude.
     *
     * Output format
     * ─────────────
     * 16-bit signed PCM, mono, little-endian, 44100 Hz.  Total duration is
     * capped at 0.8 s to keep the per-tone buffer under ~70 KB.
     *
     * @param baseFreq the fundamental frequency in Hz before detuning/shift
     */
    private void playChaoticToneRich(double baseFreq) {
        try {
            double durationSec = 0.5 + random.nextDouble() * 0.3;  // 0.5 - 0.8 s
            int sampleRate = 44100;
            int totalSamples = (int) (durationSec * sampleRate);
            byte[] buffer = new byte[totalSamples * 2];  // 16-bit mono = 2 bytes/sample
            
            double masterGain = 0.55;  // scales the final mix to avoid clipping
            
            // Random detuning in cents (±100 cents = ±1 semitone) applied multiplicatively.
            double detuneCents = (random.nextDouble() - 0.5) * 200.0;
            double detuneFactor = Math.pow(2.0, detuneCents / 1200.0);

            // Slow linear frequency drift per second (positive or negative).
            double driftRate = (random.nextDouble() - 0.5) * 0.6;

            // Frequency modulation parameters: rate in Hz, depth in Hz.
            double fmDepth = 5.0 + random.nextDouble() * 30.0;
            double fmRate = 0.5 + random.nextDouble() * 8.0;
            
            // Relative amplitudes for harmonics 1–5 (fundamental through 5th partial).
            double[] harmonicAmps = new double[]{
                    1.0,
                    0.9 + random.nextDouble() * 0.8,
                    0.6 + random.nextDouble() * 0.6,
                    0.4 + random.nextDouble() * 0.6,
                    0.2 + random.nextDouble() * 0.4
            };
            
            // ADSR envelope parameter ranges (all in samples at 44100 Hz).
            int attackSamples  = (int) (0.005 * sampleRate) + random.nextInt((int) (0.03 * sampleRate));
            int decaySamples   = (int) (0.08  * sampleRate) + random.nextInt((int) (0.12 * sampleRate));
            double sustainLevel = 0.25 + random.nextDouble() * 0.45;
            int releaseSamples = (int) (0.35  * sampleRate) + random.nextInt((int) (0.3  * sampleRate));
            
            // ── Sample generation loop ───────────────────────────────────────
            for (int i = 0; i < totalSamples; i++) {
                double t = i / (double) sampleRate;  // time in seconds
                
                // Pitch drift: adds a tiny random walk on top of the linear drift.
                double drift = driftRate * t * (0.5 + random.nextDouble() * 1.5);
                double jitter = (random.nextDouble() - 0.5) * 0.5;  // sub-Hz noise
                double freq = baseFreq * detuneFactor + drift + jitter;
                
                // FM modulator signal (used to shift harmonic phases below).
                double fm = Math.sin(2.0 * Math.PI * fmRate * t) * fmDepth;
                
                // Sum all harmonics; each has a slow independent phase modulator.
                double sample = 0.0;
                for (int h = 0; h < harmonicAmps.length; h++) {
                    double harmonicFreq = freq * (h + 1) * (1.0 + (random.nextDouble() - 0.5) * 0.02);
                    double phaseMod = Math.sin(2.0 * Math.PI * (fmRate * (0.3 + h * 0.1)) * t) * (0.2 + random.nextDouble() * 0.8);
                    sample += harmonicAmps[h] * Math.sin(2.0 * Math.PI * harmonicFreq * t + phaseMod + fm * 0.01);
                }
                
                // Add a small amount of a square wave at triple the base frequency
                // for that characteristic piano "hammer attack" quality.
                sample += 0.08 * Math.signum(Math.sin(2.0 * Math.PI * freq * 3.0 * t + random.nextDouble()));
                
                // ── ADSR envelope ───────────────────────────────────────────────
                double env;
                if (i < attackSamples) {
                    // Attack: linear ramp from 0 to 1; occasional glitch (6 % chance)
                    // randomly dips the level to simulate a mechanical imperfection.
                    double glitch = (random.nextDouble() < 0.06) ? (random.nextDouble() * 0.6) : 1.0;
                    env = (i / (double) attackSamples) * glitch;
                } else if (i < attackSamples + decaySamples) {
                    // Decay: linear ramp from 1 down to sustainLevel.
                    double p = (i - attackSamples) / (double) decaySamples;
                    env = 1.0 + p * (sustainLevel - 1.0);
                } else if (i < totalSamples - releaseSamples) {
                    // Sustain: constant level until the release phase begins.
                    env = sustainLevel;
                } else {
                    // Release: linear ramp from sustainLevel down to 0.
                    double p = (i - (totalSamples - releaseSamples)) / (double) releaseSamples;
                    env = sustainLevel * (1.0 - p);
                }
                
                // Small per-sample amplitude jitter (±6 %) for a "breathing" quality.
                double ampMod = 0.95 + (random.nextDouble() - 0.5) * 0.12;
                double out = sample * env * ampMod * masterGain;
                
                // Soft clipping: compress peaks beyond ±0.95 instead of hard-clipping.
                if (out >  0.95) out =  0.95 + (out - 0.95) * 0.2;
                if (out < -0.95) out = -0.95 + (out + 0.95) * 0.2;
                
                // Convert double [-1, 1] to signed 16-bit integer, then store
                // in little-endian byte order (low byte first, then high byte).
                short sVal = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, (int) (out * Short.MAX_VALUE)));
                int idx = i * 2;
                buffer[idx]     = (byte) (sVal & 0xff);          // low byte
                buffer[idx + 1] = (byte) ((sVal >> 8) & 0xff);  // high byte
            }
            
            playPCM(buffer, sampleRate);
            
        } catch (Exception ex) {
            System.out.println("[AUDIO] Error generating tone: " + ex.getMessage());
        }
    }
    
    /**
     * Streams a raw 16-bit little-endian mono PCM buffer to the audio hardware.
     *
     * Uses a SourceDataLine rather than a Clip so that the buffer never has
     * to be fully loaded into memory before playback starts; data is written
     * in 4 KB chunks.  The line is opened with a buffer limited to 64 KB to
     * prevent over-buffering on hardware with large internal buffers.
     *
     * The MASTER_GAIN control is set to −6 dBFS if the line supports it, so
     * synthetic tones are roughly level-matched with the WAV files.
     *
     * drain() is called before the line is closed (via try-with-resources) to
     * ensure the last chunk of audio is heard before the line is released.
     *
     * @param pcm16      byte array of signed 16-bit little-endian mono PCM
     * @param sampleRate the sample rate used when generating the PCM data (Hz)
     */
    private void playPCM(byte[] pcm16, int sampleRate) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            // Limit internal hardware buffer to 64 KB to avoid latency build-up.
            line.open(format, Math.min(pcm16.length, 65536));
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                gain.setValue(-6.0f);  // −6 dBFS
            }
            line.start();
            int written = 0;
            // Write in 4 KB chunks to avoid blocking indefinitely on one large write.
            while (written < pcm16.length) {
                int toWrite = Math.min(4096, pcm16.length - written);
                line.write(pcm16, written, toWrite);
                written += toWrite;
            }
            line.drain();  // wait until all data has been sent to the hardware
        } catch (Exception e) {
            System.out.println("[AUDIO] PCM playback error: " + e.getMessage());
        }
    }
    
    /**
     * Immediately stops all queued and in-progress audio tasks.
     *
     * Uses shutdownNow() to interrupt any blocked I/O operations (such as
     * line.drain() inside playPCM) rather than waiting for them to complete
     * naturally.  Should be called from GamePanel.stop() when the game exits.
     */
    public void shutdown() {
        soundExecutor.shutdownNow();
    }
}
