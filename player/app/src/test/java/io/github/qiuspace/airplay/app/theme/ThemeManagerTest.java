package io.github.qiuspace.airplay.app.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import io.github.qiuspace.airplay.app.settings.AppSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeManagerTest {

    @AfterEach
    void restoreLightTheme() {
        FlatLightLaf.setup();
    }

    @Test
    void initialApplicationAlwaysInstallsTheRequestedTheme() {
        FlatDarkLaf.setup();

        try (ThemeManager manager = new ThemeManager(AppSettings.ThemeMode.LIGHT)) {
            assertThat(manager.isDark()).isFalse();
            assertThat(UIManager.getLookAndFeel()).isInstanceOf(FlatLightLaf.class);
        }
    }

    @Test
    void applyingTheSameResolvedThemeDoesNotReinstallFlatLaf() {
        try (ThemeManager manager = new ThemeManager(AppSettings.ThemeMode.LIGHT)) {
            Object installedLookAndFeel = UIManager.getLookAndFeel();

            manager.apply(AppSettings.ThemeMode.LIGHT);

            assertThat(UIManager.getLookAndFeel()).isSameAs(installedLookAndFeel);
        }
    }

    @Test
    void applyingARealThemeChangeReplacesFlatLaf() {
        try (ThemeManager manager = new ThemeManager(AppSettings.ThemeMode.LIGHT)) {
            Object lightLookAndFeel = UIManager.getLookAndFeel();

            manager.apply(AppSettings.ThemeMode.DARK);

            assertThat(manager.isDark()).isTrue();
            assertThat(UIManager.getLookAndFeel())
                    .isInstanceOf(FlatDarkLaf.class)
                    .isNotSameAs(lightLookAndFeel);
        }
    }

    @Test
    void focusIsRestoredAfterARealUiRefresh() throws Exception {
        FocusTrackingField editor = new FocusTrackingField();

        ThemeManager.restoreFocusAfterUiUpdate(editor);
        SwingUtilities.invokeAndWait(() -> {
            // Flush the restoration queued by ThemeManager.
        });

        assertThat(editor.focusRequests()).isEqualTo(1);
    }

    @Test
    void hiddenOrDisabledEditorsDoNotReceiveRestoredFocus() throws Exception {
        FocusTrackingField hiddenEditor = new FocusTrackingField();
        hiddenEditor.showing = false;
        FocusTrackingField disabledEditor = new FocusTrackingField();
        disabledEditor.setEnabled(false);

        ThemeManager.restoreFocusAfterUiUpdate(hiddenEditor);
        ThemeManager.restoreFocusAfterUiUpdate(disabledEditor);
        SwingUtilities.invokeAndWait(() -> {
            // Flush both queued restoration checks.
        });

        assertThat(hiddenEditor.focusRequests()).isZero();
        assertThat(disabledEditor.focusRequests()).isZero();
    }

    private static final class FocusTrackingField extends JTextField {

        private final AtomicInteger focusRequests = new AtomicInteger();
        private boolean showing = true;

        @Override
        public boolean isDisplayable() {
            return true;
        }

        @Override
        public boolean isShowing() {
            return showing;
        }

        @Override
        public boolean requestFocusInWindow() {
            focusRequests.incrementAndGet();
            return true;
        }

        int focusRequests() {
            return focusRequests.get();
        }
    }
}
