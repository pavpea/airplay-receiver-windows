package io.github.qiuspace.airplay.app;

import io.github.qiuspace.airplay.app.settings.AppSettings;
import io.github.qiuspace.airplay.server.AirPlayConfig;
import org.junit.jupiter.api.Test;

import java.awt.DisplayMode;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiverBroadcastConfigTest {

    @Test
    void snapshotsPrimaryDisplayAndResolvedFrameRateExactlyOnce() {
        AtomicInteger displayReads = new AtomicInteger();
        AppSettings settings = settings(AppSettings.DisplayMode.PRIMARY_DISPLAY, 120);

        ReceiverBroadcastConfig snapshot = ReceiverBroadcastConfig.from(settings, () -> {
            displayReads.incrementAndGet();
            return new DisplayMode(2560, 1440, 32, 165);
        });

        assertThat(snapshot).isEqualTo(new ReceiverBroadcastConfig(
                "Receiver", 2560, 1440, 120, true));
        assertThat(displayReads).hasValue(1);
    }

    @Test
    void customSnapshotDoesNotReadDynamicDisplayCapabilities() {
        AtomicInteger displayReads = new AtomicInteger();
        AppSettings settings = settings(AppSettings.DisplayMode.CUSTOM, 97);

        ReceiverBroadcastConfig snapshot = ReceiverBroadcastConfig.from(settings, () -> {
            displayReads.incrementAndGet();
            return new DisplayMode(3840, 2160, 32, 144);
        });

        assertThat(snapshot.width()).isEqualTo(1766);
        assertThat(snapshot.height()).isEqualTo(3840);
        assertThat(snapshot.fps()).isEqualTo(97);
        assertThat(displayReads).hasValue(0);
    }

    @Test
    void createsFreshMutableServerConfigWithoutChangingSnapshot() {
        ReceiverBroadcastConfig snapshot = new ReceiverBroadcastConfig(
                "Receiver", 1920, 1080, 60, true);

        AirPlayConfig first = snapshot.toAirPlayConfig();
        first.setServerName("mutated");
        first.setWidth(1);
        AirPlayConfig second = snapshot.toAirPlayConfig();

        assertThat(second.getServerName()).isEqualTo("Receiver");
        assertThat(second.getWidth()).isEqualTo(1920);
        assertThat(second.getHeight()).isEqualTo(1080);
        assertThat(second.getFps()).isEqualTo(60);
        assertThat(second.isMirrorOnly()).isTrue();
    }

    private static AppSettings settings(AppSettings.DisplayMode displayMode, int fps) {
        return new AppSettings(
                "Receiver",
                displayMode,
                1766,
                3840,
                fps,
                AppSettings.ThemeMode.DARK,
                AppSettings.LanguageMode.ZH_CN,
                false,
                true,
                true,
                true,
                0.5,
                AppSettings.FrameRateMode.CUSTOM,
                fps);
    }
}
