package io.github.qiuspace.airplay.app.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import io.github.qiuspace.airplay.app.platform.WindowsIntegration;
import io.github.qiuspace.airplay.app.settings.AppSettings;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ThemeManager implements AutoCloseable {

    private final ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "windows-theme-watcher");
        thread.setDaemon(true);
        return thread;
    });
    private volatile AppSettings.ThemeMode mode;
    private volatile boolean dark;
    private volatile boolean initialized;

    public ThemeManager(AppSettings.ThemeMode mode) {
        System.setProperty("flatlaf.useWindowDecorations", "true");
        System.setProperty("flatlaf.useRoundedPopupBorder", "true");
        FlatLaf.registerCustomDefaultsSource("themes");
        apply(mode);
        watcher.scheduleWithFixedDelay(this::pollSystemTheme, 2, 2, TimeUnit.SECONDS);
    }

    public synchronized void apply(AppSettings.ThemeMode nextMode) {
        Objects.requireNonNull(nextMode, "nextMode");
        mode = nextMode;
        boolean nextDark = switch (nextMode) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> WindowsIntegration.isSystemDarkTheme();
        };
        if (initialized && nextDark == dark) {
            return;
        }
        applyLookAndFeel(nextDark);
        dark = nextDark;
        initialized = true;
    }

    public boolean isDark() {
        return dark;
    }

    @Override
    public void close() {
        watcher.shutdownNow();
    }

    private void pollSystemTheme() {
        if (mode == AppSettings.ThemeMode.SYSTEM) {
            boolean systemDark = WindowsIntegration.isSystemDarkTheme();
            if (systemDark != dark) {
                SwingUtilities.invokeLater(() -> {
                    if (mode == AppSettings.ThemeMode.SYSTEM) {
                        apply(AppSettings.ThemeMode.SYSTEM);
                    }
                });
            }
        }
    }

    private void applyLookAndFeel(boolean useDark) {
        Component focusOwner = KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .getFocusOwner();
        if (useDark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        FlatLaf.setUseNativeWindowDecorations(true);
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
        restoreFocusAfterUiUpdate(focusOwner);
    }

    static void restoreFocusAfterUiUpdate(Component focusOwner) {
        if (focusOwner == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (focusOwner.isDisplayable()
                    && focusOwner.isShowing()
                    && focusOwner.isEnabled()
                    && focusOwner.isFocusable()) {
                focusOwner.requestFocusInWindow();
            }
        });
    }
}
