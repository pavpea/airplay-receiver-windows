package io.github.qiuspace.airplay.app;

import io.github.qiuspace.airplay.app.settings.AppSettings;
import io.github.qiuspace.airplay.player.gstreamer.PlaybackMetrics;
import io.github.qiuspace.airplay.server.ServerState;
import io.github.qiuspace.airplay.server.SessionInfo;

public interface ReceiverView {

    void onServerState(ServerState state);

    void onSessionStarted(SessionInfo session);

    void onSessionStopped();

    void onVideoFormat(int width, int height);

    default void onVideoFrameReady(int width, int height) {
    }

    default void onPlaybackMetrics(PlaybackMetrics metrics) {
    }

    void onError(String message, Throwable error);

    void onSettingsChanged(AppSettings settings);
}
