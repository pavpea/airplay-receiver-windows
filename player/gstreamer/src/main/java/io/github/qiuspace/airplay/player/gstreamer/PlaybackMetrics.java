package io.github.qiuspace.airplay.player.gstreamer;

/** Periodic playback information for the desktop details tooltip. */
public record PlaybackMetrics(int width,
                              int height,
                              double framesPerSecond,
                              String codec,
                              DecoderPath decoderPath) {

    public enum DecoderPath {
        HARDWARE,
        SOFTWARE
    }
}
