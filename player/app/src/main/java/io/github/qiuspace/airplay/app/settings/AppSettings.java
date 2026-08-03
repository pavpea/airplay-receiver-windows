package io.github.qiuspace.airplay.app.settings;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.awt.GraphicsEnvironment;
import java.util.List;

public record AppSettings(String receiverName,
                          DisplayMode displayMode,
                          int customWidth,
                          int customHeight,
                          int maxFps,
                          ThemeMode theme,
                          LanguageMode language,
                          boolean startWithWindows,
                          boolean bringToFront,
                          boolean closeToTray,
                          boolean receiverEnabled,
                          double volume,
                          FrameRateMode frameRateMode,
                          int customFrameRate) {

    /** Preset AirPlay display refresh-rate capabilities shown in the UI. */
    public static final List<Integer> FRAME_RATE_OPTIONS = List.of(60, 120);
    public static final int MIN_CUSTOM_FRAME_RATE = 1;
    public static final int MAX_CUSTOM_FRAME_RATE = 360;

    public static AppSettings defaults() {
        return new AppSettings(defaultReceiverName(), DisplayMode.PRIMARY_DISPLAY,
                1920, 1080, defaultFrameRate(), ThemeMode.SYSTEM, LanguageMode.SYSTEM,
                false, true, true, true, 0.5,
                FrameRateMode.FOLLOW_PRIMARY_DISPLAY, defaultFrameRate());
    }

    /** Returns the primary display refresh rate, clamped to the supported custom range. */
    public static int defaultFrameRate() {
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                int refresh = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDisplayMode().getRefreshRate();
                if (refresh > 0) {
                    return clamp(refresh, MIN_CUSTOM_FRAME_RATE, MAX_CUSTOM_FRAME_RATE);
                }
            }
        } catch (RuntimeException ignored) {
            // Headless/remote sessions may not expose a display mode.
        }
        return 60;
    }

    /** Compatibility constructor for callers using the 1.0.0 settings shape. */
    public AppSettings(String receiverName,
                       DisplayMode displayMode,
                       int customWidth,
                       int customHeight,
                       int maxFps,
                       ThemeMode theme,
                       LanguageMode language,
                       boolean startWithWindows,
                       boolean bringToFront,
                       boolean closeToTray,
                       boolean receiverEnabled,
                       double volume) {
        this(receiverName, displayMode, customWidth, customHeight, maxFps, theme, language,
                startWithWindows, bringToFront, closeToTray, receiverEnabled, volume,
                legacyFrameRateMode(maxFps), legacyCustomFrameRate(maxFps));
    }

    public AppSettings normalized() {
        String name = receiverName == null || receiverName.isBlank()
                ? defaultReceiverName()
                : receiverName.trim();
        if (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 63) {
            name = defaultReceiverName();
        }
        FrameRateMode normalizedMode = frameRateMode == null
                ? legacyFrameRateMode(maxFps)
                : frameRateMode;
        int normalizedCustom = clamp(
                customFrameRate > 0 ? customFrameRate : maxFps,
                MIN_CUSTOM_FRAME_RATE, MAX_CUSTOM_FRAME_RATE);
        int effectiveFps = switch (normalizedMode) {
            case FOLLOW_PRIMARY_DISPLAY -> defaultFrameRate();
            case PRESET_60 -> 60;
            case PRESET_120 -> 120;
            case CUSTOM -> normalizedCustom;
        };
        return new AppSettings(name,
                displayMode == null ? DisplayMode.PRIMARY_DISPLAY : displayMode,
                clamp(customWidth, 640, 7680),
                clamp(customHeight, 480, 4320),
                effectiveFps,
                theme == null ? ThemeMode.SYSTEM : theme,
                language == null ? LanguageMode.SYSTEM : language,
                startWithWindows, bringToFront, closeToTray, true,
                Math.max(0, Math.min(1, volume)), normalizedMode, normalizedCustom);
    }

    /** Resolves the value advertised to AirPlay for the current primary display. */
    public int resolvedFrameRate() {
        return normalized().maxFps();
    }

    /** Maps the legacy single integer field to the new selection mode. */
    public static int normalizeFrameRate(int fps) {
        return switch (legacyFrameRateMode(fps)) {
            case PRESET_60 -> 60;
            case PRESET_120 -> 120;
            case CUSTOM -> clamp(fps, MIN_CUSTOM_FRAME_RATE, MAX_CUSTOM_FRAME_RATE);
            case FOLLOW_PRIMARY_DISPLAY -> defaultFrameRate();
        };
    }

    public AppSettings withVolume(double newVolume) {
        return new AppSettings(receiverName, displayMode, customWidth, customHeight, maxFps,
                theme, language, startWithWindows, bringToFront, closeToTray, receiverEnabled,
                newVolume, frameRateMode, customFrameRate);
    }

    public AppSettings withFrameRate(FrameRateMode mode, int customRate) {
        return new AppSettings(receiverName, displayMode, customWidth, customHeight, maxFps,
                theme, language, startWithWindows, bringToFront, closeToTray, receiverEnabled,
                volume, mode, customRate).normalized();
    }

    private static String defaultReceiverName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
            return "AirPlay Receiver";
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static FrameRateMode legacyFrameRateMode(int fps) {
        if (fps == 60) {
            return FrameRateMode.PRESET_60;
        }
        if (fps == 120) {
            return FrameRateMode.PRESET_120;
        }
        return fps > 0 ? FrameRateMode.CUSTOM : FrameRateMode.FOLLOW_PRIMARY_DISPLAY;
    }

    private static int legacyCustomFrameRate(int fps) {
        return clamp(fps > 0 ? fps : 60, MIN_CUSTOM_FRAME_RATE, MAX_CUSTOM_FRAME_RATE);
    }

    public enum DisplayMode {
        PRIMARY_DISPLAY,
        HD_720,
        FULL_HD_1080,
        CUSTOM
    }

    public enum FrameRateMode {
        FOLLOW_PRIMARY_DISPLAY,
        PRESET_60,
        PRESET_120,
        CUSTOM
    }

    public enum ThemeMode {
        SYSTEM,
        LIGHT,
        DARK
    }

    public enum LanguageMode {
        SYSTEM,
        ZH_CN,
        EN
    }
}
