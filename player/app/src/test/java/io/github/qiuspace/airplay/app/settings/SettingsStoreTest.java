package io.github.qiuspace.airplay.app.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndLoadsSettings() {
        SettingsStore store = new SettingsStore(temporaryDirectory.resolve("settings.json"));
        AppSettings settings = new AppSettings("Living Room", AppSettings.DisplayMode.FULL_HD_1080,
                1600, 900, 60, AppSettings.ThemeMode.DARK, AppSettings.LanguageMode.EN,
                true, false, true, true, 0.42);

        store.save(settings);

        assertThat(store.load()).isEqualTo(settings);
    }

    @Test
    void keepsExistingVolumeValueWithoutMigrationOrRewrite() throws Exception {
        Path settingsFile = temporaryDirectory.resolve("settings.json");
        Files.writeString(settingsFile, """
                {
                  "receiverName": "Office",
                  "displayMode": "PRIMARY_DISPLAY",
                  "customWidth": 1920,
                  "customHeight": 1080,
                  "maxFps": 60,
                  "theme": "SYSTEM",
                  "language": "SYSTEM",
                  "startWithWindows": false,
                  "bringToFront": true,
                  "closeToTray": true,
                  "receiverEnabled": true,
                  "volume": 0.73
                }
                """);
        String original = Files.readString(settingsFile);

        AppSettings loaded = new SettingsStore(settingsFile).load();

        assertThat(loaded.volume()).isEqualTo(0.73);
        assertThat(Files.readString(settingsFile)).isEqualTo(original);
    }

    @Test
    void missingVolumeUsesNewDefaultWithoutRewritingExistingFile() throws Exception {
        Path settingsFile = temporaryDirectory.resolve("settings.json");
        Files.writeString(settingsFile, """
                {
                  "receiverName": "Office",
                  "displayMode": "PRIMARY_DISPLAY",
                  "customWidth": 1920,
                  "customHeight": 1080,
                  "maxFps": 60,
                  "theme": "SYSTEM",
                  "language": "SYSTEM",
                  "startWithWindows": false,
                  "bringToFront": true,
                  "closeToTray": true,
                  "receiverEnabled": true
                }
                """);
        String original = Files.readString(settingsFile);

        AppSettings loaded = new SettingsStore(settingsFile).load();

        assertThat(loaded.volume()).isEqualTo(0.5);
        assertThat(Files.readString(settingsFile)).isEqualTo(original);
    }

    @Test
    void backsUpDamagedSettingsAndReturnsDefaults() throws Exception {
        Path settingsFile = temporaryDirectory.resolve("settings.json");
        Files.writeString(settingsFile, "{broken-json");
        SettingsStore store = new SettingsStore(settingsFile);

        AppSettings loaded = store.load();

        assertThat(loaded).isEqualTo(AppSettings.defaults());
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            assertThat(files.anyMatch(path -> path.getFileName().toString().startsWith("settings.corrupt-"))).isTrue();
        }
        assertThat(Files.isRegularFile(settingsFile)).isTrue();
    }

    @Test
    void migratesLegacyApplicationProperties() throws Exception {
        Path legacy = temporaryDirectory.resolve("application.properties");
        Files.writeString(legacy, "airplay.serverName=Office TV\n"
                + "airplay.width=1280\n"
                + "airplay.height=720\n"
                + "airplay.fps=30\n");
        SettingsStore store = new SettingsStore(temporaryDirectory.resolve("settings.json"), legacy);

        AppSettings migrated = store.load();

        assertThat(migrated.receiverName()).isEqualTo("Office TV");
        assertThat(migrated.displayMode()).isEqualTo(AppSettings.DisplayMode.CUSTOM);
        assertThat(migrated.customWidth()).isEqualTo(1280);
        assertThat(migrated.customHeight()).isEqualTo(720);
        assertThat(migrated.maxFps()).isEqualTo(30);
        assertThat(migrated.frameRateMode()).isEqualTo(AppSettings.FrameRateMode.CUSTOM);
        assertThat(migrated.customFrameRate()).isEqualTo(30);
        assertThat(Files.isRegularFile(store.settingsFile())).isTrue();
    }

    @Test
    void normalizesUnsafeValues() {
        AppSettings settings = new AppSettings(" ", null, 1, 10000, 120,
                null, null, false, true, true, false, 5);

        AppSettings normalized = settings.normalized();

        assertThat(normalized.receiverName()).isNotBlank();
        assertThat(normalized.customWidth()).isEqualTo(640);
        assertThat(normalized.customHeight()).isEqualTo(4320);
        assertThat(normalized.maxFps()).isEqualTo(120);
        assertThat(normalized.receiverEnabled()).isTrue();
        assertThat(normalized.volume()).isEqualTo(1);
    }

    @Test
    void preservesCustomFrameRateWithinTheSupportedRange() {
        AppSettings settings = new AppSettings("Office", AppSettings.DisplayMode.PRIMARY_DISPLAY,
                1920, 1080, 1, AppSettings.ThemeMode.SYSTEM, AppSettings.LanguageMode.EN,
                false, true, true, true, 0.5,
                AppSettings.FrameRateMode.CUSTOM, 360);

        AppSettings normalized = settings.normalized();

        assertThat(normalized.frameRateMode()).isEqualTo(AppSettings.FrameRateMode.CUSTOM);
        assertThat(normalized.customFrameRate()).isEqualTo(360);
        assertThat(normalized.resolvedFrameRate()).isEqualTo(360);
    }
}
