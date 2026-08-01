package io.github.qiuspace.airplay.player.gstreamer;

/**
 * Serializes video-format changes so the UI can finish its layout before the
 * corresponding GStreamer buffer reaches the Swing renderer.
 */
final class VideoFormatNotifier {

    private int width;
    private int height;

    synchronized void beforeBuffer(int nextWidth, int nextHeight, GstPlayerListener listener) {
        if (nextWidth <= 0 || nextHeight <= 0
                || (nextWidth == width && nextHeight == height)) {
            return;
        }
        listener.onVideoFormatChanged(nextWidth, nextHeight);
        width = nextWidth;
        height = nextHeight;
    }

    void frameReady(int frameWidth, int frameHeight, GstPlayerListener listener) {
        listener.onVideoFrameReady(frameWidth, frameHeight);
    }

    synchronized void reset() {
        width = 0;
        height = 0;
    }
}
