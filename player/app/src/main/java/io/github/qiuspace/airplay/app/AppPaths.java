package io.github.qiuspace.airplay.app;

import java.nio.file.Path;

public final class AppPaths {

    private static final String APP_DIRECTORY = "AirPlay Receiver for Windows";

    private AppPaths() {
    }

    public static Path settingsDirectory() {
        return environmentPath("APPDATA", Path.of(System.getProperty("user.home")))
                .resolve(APP_DIRECTORY);
    }

    public static Path logsDirectory() {
        return localDataDirectory().resolve("logs");
    }

    public static Path localDataDirectory() {
        return environmentPath("LOCALAPPDATA", Path.of(System.getProperty("user.home")))
                .resolve(APP_DIRECTORY);
    }

    private static Path environmentPath(String name, Path fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Path.of(value);
    }
}
