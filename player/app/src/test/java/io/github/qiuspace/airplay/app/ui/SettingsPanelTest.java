package io.github.qiuspace.airplay.app.ui;

import io.github.qiuspace.airplay.app.i18n.I18n;
import io.github.qiuspace.airplay.app.settings.AppSettings;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsPanelTest {

    @Test
    void editsAreSavedAutomaticallyWithoutCreatingASeparateWindow() throws Exception {
        AtomicReference<AppSettings> saved = new AtomicReference<>();

        SettingsPanel panel = onEdt(() -> {
            SettingsPanel created = new SettingsPanel(new I18n(AppSettings.LanguageMode.EN));
            created.open(AppSettings.defaults(), saved::set);
            find(created, "settings.receiverName", JTextField.class).setText("Living Room");
            created.flushAutoSave();
            return created;
        });

        assertThat(SwingUtilities.getWindowAncestor(panel)).isNull();
        assertThat(saved.get()).isNotNull();
        assertThat(saved.get().receiverName()).isEqualTo("Living Room");
        assertThat(find(panel, "settings.save", Component.class)).isNull();
        assertThat(find(panel, "settings.cancel", Component.class)).isNull();
    }

    @Test
    void invalidNameStaysOnTheEmbeddedPageAndShowsInlineFeedback() throws Exception {
        AtomicReference<AppSettings> saved = new AtomicReference<>();

        SettingsPanel panel = onEdt(() -> {
            SettingsPanel created = new SettingsPanel(new I18n(AppSettings.LanguageMode.EN));
            created.open(AppSettings.defaults(), saved::set);
            find(created, "settings.receiverName", JTextField.class).setText(" ");
            created.flushAutoSave();
            return created;
        });

        assertThat(saved.get()).isNull();
        assertThat(find(panel, "settings.validation", JLabel.class).isVisible()).isTrue();
    }

    @Test
    void customFrameRateIsShownAndSavedAsTheBroadcastLimit() throws Exception {
        AtomicReference<AppSettings> saved = new AtomicReference<>();

        SettingsPanel panel = onEdt(() -> {
            SettingsPanel created = new SettingsPanel(new I18n(AppSettings.LanguageMode.EN));
            created.open(AppSettings.defaults(), saved::set);
            @SuppressWarnings("unchecked")
            JComboBox<AppSettings.FrameRateMode> modes = find(
                    created, "settings.fps", JComboBox.class);
            modes.setSelectedItem(AppSettings.FrameRateMode.CUSTOM);
            find(created, "settings.customFps", JSpinner.class).setValue(144);
            created.flushAutoSave();
            return created;
        });

        assertThat(panel).isNotNull();
        assertThat(saved.get()).isNotNull();
        assertThat(saved.get().frameRateMode()).isEqualTo(AppSettings.FrameRateMode.CUSTOM);
        assertThat(saved.get().customFrameRate()).isEqualTo(144);
        assertThat(saved.get().resolvedFrameRate()).isEqualTo(144);
    }

    @Test
    void customInputsShareTheirModeRowAndAreHiddenForPresetModes() throws Exception {
        onEdt(() -> {
            SettingsPanel panel = new SettingsPanel(new I18n(AppSettings.LanguageMode.EN));
            panel.open(AppSettings.defaults(), ignored -> {
            });

            JPanel resolutionRow = find(panel, "settings.resolutionFields", JPanel.class);
            JPanel resolutionCustom = find(panel, "settings.customResolutionFields", JPanel.class);
            JPanel frameRateRow = find(panel, "settings.frameRateFields", JPanel.class);
            JPanel frameRateCustom = find(panel, "settings.customFrameRateFields", JPanel.class);
            JComboBox<?> resolutionMode = find(panel, "settings.displayMode", JComboBox.class);
            JComboBox<?> frameRateMode = find(panel, "settings.fps", JComboBox.class);
            HoverInfoLabel resolutionInfo = find(
                    panel, "settings.displayInfo", HoverInfoLabel.class);
            HoverInfoLabel frameRateInfo = find(
                    panel, "settings.frameRateInfo", HoverInfoLabel.class);

            assertThat(resolutionCustom.isVisible()).isFalse();
            assertThat(frameRateCustom.isVisible()).isFalse();
            assertThat(resolutionMode.getParent()).isSameAs(resolutionRow);
            assertThat(resolutionCustom.getParent()).isSameAs(resolutionRow);
            assertThat(frameRateMode.getParent()).isSameAs(frameRateRow);
            assertThat(frameRateCustom.getParent()).isSameAs(frameRateRow);
            assertThat(resolutionInfo.infoText()).isNotBlank();
            assertThat(frameRateInfo.infoText()).isNotBlank();
            assertThat(resolutionInfo.getToolTipText()).isNull();
            assertThat(frameRateInfo.getToolTipText()).isNull();

            for (AppSettings.DisplayMode mode : AppSettings.DisplayMode.values()) {
                resolutionMode.setSelectedItem(mode);
                assertThat(resolutionCustom.isVisible())
                        .as("custom resolution visibility for %s", mode)
                        .isEqualTo(mode == AppSettings.DisplayMode.CUSTOM);
            }
            for (AppSettings.FrameRateMode mode : AppSettings.FrameRateMode.values()) {
                frameRateMode.setSelectedItem(mode);
                assertThat(frameRateCustom.isVisible())
                        .as("custom frame-rate visibility for %s", mode)
                        .isEqualTo(mode == AppSettings.FrameRateMode.CUSTOM);
            }
            return null;
        });
    }

    @Test
    void switchingModesPreservesCustomValuesAndKeepsTheGroupsIndependent() throws Exception {
        AtomicReference<AppSettings> saved = new AtomicReference<>();

        onEdt(() -> {
            SettingsPanel panel = new SettingsPanel(new I18n(AppSettings.LanguageMode.EN));
            panel.open(AppSettings.defaults(), saved::set);
            @SuppressWarnings("unchecked")
            JComboBox<AppSettings.DisplayMode> resolutionMode = find(
                    panel, "settings.displayMode", JComboBox.class);
            @SuppressWarnings("unchecked")
            JComboBox<AppSettings.FrameRateMode> frameRateMode = find(
                    panel, "settings.fps", JComboBox.class);
            JPanel resolutionCustom = find(panel, "settings.customResolutionFields", JPanel.class);
            JPanel frameRateCustom = find(panel, "settings.customFrameRateFields", JPanel.class);
            JSpinner width = find(panel, "settings.width", JSpinner.class);
            JSpinner height = find(panel, "settings.height", JSpinner.class);
            JSpinner customFps = find(panel, "settings.customFps", JSpinner.class);

            resolutionMode.setSelectedItem(AppSettings.DisplayMode.CUSTOM);
            width.setValue(2048);
            height.setValue(1152);
            assertThat(resolutionCustom.isVisible()).isTrue();
            assertThat(frameRateCustom.isVisible()).isFalse();

            frameRateMode.setSelectedItem(AppSettings.FrameRateMode.CUSTOM);
            customFps.setValue(144);
            assertThat(frameRateCustom.isVisible()).isTrue();

            resolutionMode.setSelectedItem(AppSettings.DisplayMode.FULL_HD_1080);
            frameRateMode.setSelectedItem(AppSettings.FrameRateMode.PRESET_60);
            assertThat(resolutionCustom.isVisible()).isFalse();
            assertThat(frameRateCustom.isVisible()).isFalse();
            assertThat(width.getValue()).isEqualTo(2048);
            assertThat(height.getValue()).isEqualTo(1152);
            assertThat(customFps.getValue()).isEqualTo(144);

            resolutionMode.setSelectedItem(AppSettings.DisplayMode.CUSTOM);
            frameRateMode.setSelectedItem(AppSettings.FrameRateMode.CUSTOM);
            panel.flushAutoSave();
            return null;
        });

        assertThat(saved.get()).isNotNull();
        assertThat(saved.get().displayMode()).isEqualTo(AppSettings.DisplayMode.CUSTOM);
        assertThat(saved.get().customWidth()).isEqualTo(2048);
        assertThat(saved.get().customHeight()).isEqualTo(1152);
        assertThat(saved.get().frameRateMode()).isEqualTo(AppSettings.FrameRateMode.CUSTOM);
        assertThat(saved.get().customFrameRate()).isEqualTo(144);
    }

    @Test
    void customEditorsRemainToTheRightAtTheCompactMainWindowWidth() throws Exception {
        onEdt(() -> {
            SettingsPanel panel = new SettingsPanel(new I18n(AppSettings.LanguageMode.EN));
            panel.open(AppSettings.defaults(), ignored -> {
            });
            find(panel, "settings.displayMode", JComboBox.class)
                    .setSelectedItem(AppSettings.DisplayMode.CUSTOM);
            find(panel, "settings.fps", JComboBox.class)
                    .setSelectedItem(AppSettings.FrameRateMode.CUSTOM);
            panel.setSize(928, 464);
            layoutTree(panel);

            JComboBox<?> resolutionMode = find(
                    panel, "settings.displayMode", JComboBox.class);
            JPanel resolutionCustom = find(
                    panel, "settings.customResolutionFields", JPanel.class);
            JComboBox<?> frameRateMode = find(panel, "settings.fps", JComboBox.class);
            JPanel frameRateCustom = find(
                    panel, "settings.customFrameRateFields", JPanel.class);

            assertThat(resolutionCustom.getY()).isEqualTo(resolutionMode.getY());
            assertThat(resolutionCustom.getX())
                    .isGreaterThanOrEqualTo(resolutionMode.getX() + resolutionMode.getWidth());
            assertThat(resolutionCustom.getX() + resolutionCustom.getWidth())
                    .isLessThanOrEqualTo(resolutionCustom.getParent().getWidth());
            assertThat(frameRateCustom.getY()).isEqualTo(frameRateMode.getY());
            assertThat(frameRateCustom.getX())
                    .isGreaterThanOrEqualTo(frameRateMode.getX() + frameRateMode.getWidth());
            assertThat(frameRateCustom.getX() + frameRateCustom.getWidth())
                    .isLessThanOrEqualTo(frameRateCustom.getParent().getWidth());
            return null;
        });
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) {
                layoutTree(child);
            }
        }
    }

    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName()) && type.isInstance(component)) {
                return type.cast(component);
            }
            if (component instanceof Container container) {
                T match = find(container, name, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static <T> T onEdt(EdtSupplier<T> supplier) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(supplier.get());
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError("EDT operation failed", failure.get());
        }
        return result.get();
    }

    @FunctionalInterface
    private interface EdtSupplier<T> {
        T get() throws Exception;
    }
}
