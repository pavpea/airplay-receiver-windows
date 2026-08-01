package io.github.qiuspace.airplay.app;

/** Maps a linear UI level to a more natural, fine-grained GStreamer gain. */
final class PerceptualVolume {

    private PerceptualVolume() {
    }

    static double toGain(double perceivedLevel) {
        double normalized = Math.max(0, Math.min(1, perceivedLevel));
        return normalized * Math.sqrt(normalized);
    }
}
