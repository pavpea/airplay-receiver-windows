package io.github.qiuspace.airplay.app.ui;

import io.github.qiuspace.airplay.app.i18n.I18n;
import io.github.qiuspace.airplay.app.settings.AppSettings;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
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
