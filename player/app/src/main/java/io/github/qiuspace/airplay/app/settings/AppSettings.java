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
                          double volume) {

    /** The only advertised AirPlay display refresh-rate capabilities. */
    public static final List<Integer> FRAME_RATE_OPTIONS = List.of(60, 120);

    public static AppSettings defaults() {
        return new AppSettings(defaultReceiverName(), DisplayMode.PRIMARY_DISPLAY,
                1920, 1080, defaultFrameRate(), ThemeMode.SYSTEM, LanguageMode.SYSTEM,
                false, true, true, true, 0.5);
    }

    /** Uses the primary display refresh rate, capped to the advertised 60/120 options. */
    public static int defaultFrameRate() {
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                int refresh = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDisplayMode().getRefreshRate();
                if (refresh >= 120) {
                    return 120;
                }
            }
        } catch (RuntimeException ignored) {
            // Headless/remote sessions may not expose a display mode.
        }
        return 60;
    }

    public AppSettings normalized() {
        String name = receiverName == null || receiverName.isBlank()
                ? defaultReceiverName()
                : receiverName.trim();
        if (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 63) {
            name = defaultReceiverName();
        }
        return new AppSettings(name,
                displayMode == null ? DisplayMode.PRIMARY_DISPLAY : displayMode,
                clamp(customWidth, 640, 7680),
                clamp(customHeight, 480, 4320),
                normalizeFrameRate(maxFps),
                theme == null ? ThemeMode.SYSTEM : theme,
                language == null ? LanguageMode.SYSTEM : language,
                startWithWindows, bringToFront, closeToTray, true,
                Math.max(0, Math.min(1, volume)));
    }

    /** Maps legacy/custom values to the nearest supported capability. */
    public static int normalizeFrameRate(int fps) {
        return fps >= 90 ? 120 : 60;
    }

    public AppSettings withVolume(double newVolume) {
        return new AppSettings(receiverName, displayMode, customWidth, customHeight, maxFps,
                theme, language, startWithWindows, bringToFront, closeToTray, receiverEnabled, newVolume);
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

    public enum DisplayMode {
        PRIMARY_DISPLAY,
        HD_720,
        FULL_HD_1080,
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
