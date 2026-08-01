package io.github.qiuspace.airplay.player.gstreamer;

public interface GstPlayerListener {

    default void onVideoFormatChanged(int width, int height) {
    }

    default void onVideoFrameReady(int width, int height) {
    }

    default void onPlaybackError(String message, Throwable error) {
    }

    default void onEndOfStream() {
    }
}
