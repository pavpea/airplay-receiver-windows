package io.github.qiuspace.airplay.server;

public interface AirPlayServerListener {

    default void onServerStateChanged(ServerState state) {
    }

    default void onSessionChanged(SessionInfo session, SessionState state) {
    }

    default void onError(Throwable error) {
    }
}
