package io.github.qiuspace.airplay.app;

import io.github.qiuspace.airplay.app.platform.WindowsIntegration;
import io.github.qiuspace.airplay.app.settings.AppSettings;
import io.github.qiuspace.airplay.app.settings.SettingsStore;
import io.github.qiuspace.airplay.app.theme.ThemeManager;
import io.github.qiuspace.airplay.player.gstreamer.GstPlayer;
import io.github.qiuspace.airplay.player.gstreamer.GstPlayerListener;
import io.github.qiuspace.airplay.player.gstreamer.PlaybackMetrics;
import io.github.qiuspace.airplay.server.AirPlayServer;
import io.github.qiuspace.airplay.server.AirPlayServerListener;
import io.github.qiuspace.airplay.server.ServerState;
import io.github.qiuspace.airplay.server.SessionInfo;
import io.github.qiuspace.airplay.server.SessionState;
import lombok.extern.slf4j.Slf4j;

import javax.swing.JComponent;
import java.awt.GraphicsEnvironment;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public final class ReceiverController implements AutoCloseable {

    private final SettingsStore settingsStore;
    private final GstPlayer player;
    private final ThemeManager themeManager;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "airplay-controller");
        thread.setDaemon(true);
        return thread;
    });

    private volatile AppSettings settings;
    private volatile ReceiverView view;
    private volatile AirPlayServer server;
    private final LatestRestartCoordinator<ReceiverBroadcastConfig> restartCoordinator;
    private volatile String displayedSessionId;

    public ReceiverController(SettingsStore settingsStore, AppSettings settings, ThemeManager themeManager) {
        AppSettings normalizedSettings = settings.normalized();
        this.settingsStore = settingsStore;
        this.settings = normalizedSettings;
        this.themeManager = themeManager;
        try {
            WindowsIntegration.setStartWithWindows(normalizedSettings.startWithWindows());
        } catch (RuntimeException error) {
            log.warn("Unable to synchronize the Windows startup setting", error);
        }
        player = new GstPlayer();
        player.setVolume(PerceptualVolume.toGain(normalizedSettings.volume()));
        player.setListener(new GstPlayerListener() {
            @Override
            public void onVideoFormatChanged(int width, int height) {
                onEdtAndWait(receiverView -> receiverView.onVideoFormat(width, height));
            }

            @Override
            public void onVideoFrameReady(int width, int height) {
                onEdt(receiverView -> receiverView.onVideoFrameReady(width, height));
            }

            @Override
            public void onPlaybackMetrics(PlaybackMetrics metrics) {
                onEdt(receiverView -> receiverView.onPlaybackMetrics(metrics));
            }

            @Override
            public void onPlaybackError(String message, Throwable error) {
                onEdt(receiverView -> receiverView.onError(message, error));
            }

            @Override
            public void onEndOfStream() {
                disconnectSession();
            }
        });
        ReceiverBroadcastConfig initialBroadcastConfig = toBroadcastConfig(normalizedSettings);
        server = createServer(initialBroadcastConfig);
        restartCoordinator = new LatestRestartCoordinator<>(
                worker,
                () -> closed.get() || server.activeSession().isPresent(),
                initialBroadcastConfig,
                config -> server.restart(config.toAirPlayConfig()),
                this::onRestartFailure);
    }

    public void attachView(ReceiverView view) {
        this.view = Objects.requireNonNull(view);
        view.onSettingsChanged(settings);
        view.onServerState(server.state());
    }

    public JComponent videoComponent() {
        return player.videoComponent();
    }

    public AppSettings settings() {
        return settings;
    }

    public void start() {
        startReceiver();
    }

    public void updateSettings(AppSettings updated) {
        AppSettings normalized = updated.normalized();
        ReceiverBroadcastConfig desiredBroadcastConfig = toBroadcastConfig(normalized);
        settings = normalized;
        settingsStore.save(normalized);
        player.setVolume(PerceptualVolume.toGain(normalized.volume()));
        themeManager.apply(normalized.theme());
        try {
            WindowsIntegration.setStartWithWindows(normalized.startWithWindows());
        } catch (RuntimeException error) {
            onEdt(receiverView -> receiverView.onError("Unable to update Windows startup setting", error));
        }
        onEdt(receiverView -> receiverView.onSettingsChanged(normalized));

        restartCoordinator.request(desiredBroadcastConfig);
    }

    public void setVolume(double volume) {
        double perceivedLevel = Math.max(0, Math.min(1, volume));
        player.setVolume(PerceptualVolume.toGain(perceivedLevel));
        AppSettings updated = settings.withVolume(perceivedLevel);
        settings = updated;
        settingsStore.save(updated);
    }

    public void setMuted(boolean muted) {
        player.setMuted(muted);
    }

    public boolean muted() {
        return player.muted();
    }

    public void disconnectSession() {
        worker.execute(() -> server.disconnectActiveSession());
    }

    public void startReceiver() {
        worker.execute(() -> {
            try {
                server.start();
            } catch (Exception error) {
                log.error("Unable to start AirPlay receiver", error);
                onEdt(receiverView -> receiverView.onError(error.getMessage(), error));
            }
        });
    }

    public void restartReceiver() {
        restartCoordinator.force(toBroadcastConfig(settings));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        worker.shutdownNow();
        try {
            server.close();
        } finally {
            try {
                player.close();
            } finally {
                themeManager.close();
            }
        }
    }

    private AirPlayServer createServer(ReceiverBroadcastConfig config) {
        return new AirPlayServer(config.toAirPlayConfig(), player, new AirPlayServerListener() {
            @Override
            public void onServerStateChanged(ServerState state) {
                onEdt(receiverView -> receiverView.onServerState(state));
            }

            @Override
            public void onSessionChanged(SessionInfo session, SessionState state) {
                if (state == SessionState.PLAYING) {
                    if (!session.id().equals(displayedSessionId)) {
                        displayedSessionId = session.id();
                        onEdt(receiverView -> receiverView.onSessionStarted(session));
                    }
                } else if (state == SessionState.STOPPED) {
                    if (session.id().equals(displayedSessionId)) {
                        displayedSessionId = null;
                        onEdt(ReceiverView::onSessionStopped);
                    }
                    restartCoordinator.resume();
                }
            }

            @Override
            public void onError(Throwable error) {
                onEdt(receiverView -> receiverView.onError(error.getMessage(), error));
            }
        });
    }

    private ReceiverBroadcastConfig toBroadcastConfig(AppSettings appSettings) {
        return ReceiverBroadcastConfig.from(appSettings,
                () -> GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDisplayMode());
    }

    private void onRestartFailure(Throwable error) {
        log.error("Unable to restart AirPlay receiver", error);
        onEdt(receiverView -> receiverView.onError(error.getMessage(), error));
    }

    private void onEdt(java.util.function.Consumer<ReceiverView> action) {
        ReceiverView currentView = view;
        if (currentView == null) {
            return;
        }
        SwingDispatcher.dispatch(() -> action.accept(currentView));
    }

    private void onEdtAndWait(java.util.function.Consumer<ReceiverView> action) {
        ReceiverView currentView = view;
        if (currentView == null) {
            return;
        }
        SwingDispatcher.dispatchAndWait(() -> action.accept(currentView));
    }
}
