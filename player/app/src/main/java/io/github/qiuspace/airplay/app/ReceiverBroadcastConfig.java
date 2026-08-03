package io.github.qiuspace.airplay.app;

import io.github.qiuspace.airplay.app.settings.AppSettings;
import io.github.qiuspace.airplay.server.AirPlayConfig;

import java.awt.DisplayMode;
import java.util.Objects;
import java.util.function.Supplier;

/** Immutable snapshot of every setting advertised by the AirPlay receiver. */
record ReceiverBroadcastConfig(String serverName,
                               int width,
                               int height,
                               int fps,
                               boolean mirrorOnly) {

    static ReceiverBroadcastConfig from(AppSettings settings,
                                        Supplier<DisplayMode> primaryDisplayMode) {
        Objects.requireNonNull(settings);
        Objects.requireNonNull(primaryDisplayMode);

        int width;
        int height;
        switch (settings.displayMode()) {
            case HD_720 -> {
                width = 1280;
                height = 720;
            }
            case FULL_HD_1080 -> {
                width = 1920;
                height = 1080;
            }
            case CUSTOM -> {
                width = settings.customWidth();
                height = settings.customHeight();
            }
            case PRIMARY_DISPLAY -> {
                DisplayMode displayMode = primaryDisplayMode.get();
                width = displayMode.getWidth();
                height = displayMode.getHeight();
            }
            default -> throw new IllegalStateException("Unsupported display mode: " + settings.displayMode());
        }

        // AppSettings is normalized before this snapshot is created. Reading maxFps
        // directly avoids resolving a possibly changing display refresh rate again.
        return new ReceiverBroadcastConfig(
                settings.receiverName(), width, height, settings.maxFps(), true);
    }

    AirPlayConfig toAirPlayConfig() {
        AirPlayConfig config = new AirPlayConfig();
        config.setServerName(serverName);
        config.setWidth(width);
        config.setHeight(height);
        config.setFps(fps);
        config.setMirrorOnly(mirrorOnly);
        return config;
    }
}
