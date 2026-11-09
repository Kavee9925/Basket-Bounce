package com.basketbounce.game;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/** Small synthesized, cute collection chime; no external sound files are required. */ public final class AudioManager implements AutoCloseable {
    private static final float SAMPLE_RATE = 22050f;
    private final ExecutorService audioQueue = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "basket-bounce-audio");
        thread.setDaemon(true);
        return thread;
    });
    public void playCollectible() {
        audioQueue.submit(() -> {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            SourceDataLine line = null;
            try {
                line = AudioSystem.getSourceDataLine(format);
                line.open(format, 4096);
                line.start();
                playTone(line, 659.25, 0.075);
                playTone(line, 783.99, 0.075);
                playTone(line, 987.77, 0.12);
                line.drain();
            } catch (Exception ignored) {
                // Audio devices may be unavailable; sound must never interrupt gameplay.
            } finally {
                if (line != null) {
                    line.stop();
                    line.close();
                }
            }
        });
    }

    private void playTone(SourceDataLine line, double frequency, double seconds) {
        int samples = (int)(SAMPLE_RATE * seconds);
        byte[] buffer = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double fadeIn = Math.min(1.0, i / 180.0);
            double fadeOut = Math.min(1.0, (samples - i) / 900.0);
            double envelope = fadeIn * fadeOut;
            double wave = Math.sin(2.0 * Math.PI * frequency * i / SAMPLE_RATE);
            short sample = (short)(wave * envelope * 5200);
            buffer[i * 2] = (byte)(sample & 0xff);
            buffer[i * 2 + 1] = (byte)((sample >>> 8) & 0xff);
        }
        line.write(buffer, 0, buffer.length);
    }
    @Override
    public void close() {
        audioQueue.shutdownNow();
    }
}
