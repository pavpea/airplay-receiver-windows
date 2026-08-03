package io.github.qiuspace.airplay.app.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HoverInfoLabelTest {

    @BeforeAll
    static void registerThemeDefaults() {
        FlatLaf.registerCustomDefaultsSource("themes");
    }

    @Test
    void hoverShowsImmediatelyAndRemainsUntilMouseLeaves() throws Exception {
        TrackingPopupFactory factory = new TrackingPopupFactory();
        TestHoverInfoLabel label = onEdt(() -> new TestHoverInfoLabel(factory));
        int initialDelay = ToolTipManager.sharedInstance().getInitialDelay();
        int dismissDelay = ToolTipManager.sharedInstance().getDismissDelay();

        onEdt(() -> {
            label.setSize(18, 18);
            label.setInfoText("Current playback details");
            enter(label);
            return null;
        });

        assertThat(factory.requests).isEqualTo(1);
        assertThat(factory.lastPopup.shown).isTrue();
        assertThat(label.isInfoPopupVisible()).isTrue();

        onEdt(() -> {
            label.repaint();
            label.revalidate();
            return null;
        });
        assertThat(label.isInfoPopupVisible()).isTrue();
        assertThat(ToolTipManager.sharedInstance().getInitialDelay()).isEqualTo(initialDelay);
        assertThat(ToolTipManager.sharedInstance().getDismissDelay()).isEqualTo(dismissDelay);

        onEdt(() -> {
            exit(label);
            return null;
        });
        assertThat(factory.lastPopup.hidden).isTrue();
        assertThat(label.isInfoPopupVisible()).isFalse();
    }

    @Test
    void clearingTextHidingOrRemovingTheLabelClosesItsPopup() throws Exception {
        TrackingPopupFactory factory = new TrackingPopupFactory();
        TestHoverInfoLabel label = onEdt(() -> new TestHoverInfoLabel(factory));

        onEdt(() -> {
            label.setSize(18, 18);
            label.setInfoText("details");
            enter(label);
            label.setInfoText(null);
            return null;
        });
        assertThat(factory.lastPopup.hidden).isTrue();
        assertThat(label.infoText()).isNull();

        onEdt(() -> {
            label.setInfoText("details again");
            return null;
        });
        assertThat(factory.requests).isEqualTo(2);
        TrackingPopup visibleBeforeHide = factory.lastPopup;
        onEdt(() -> {
            label.setVisible(false);
            return null;
        });
        assertThat(visibleBeforeHide.hidden).isTrue();

        onEdt(() -> {
            label.setVisible(true);
            exit(label);
            enter(label);
            label.removeNotify();
            return null;
        });
        assertThat(factory.lastPopup.hidden).isTrue();
        assertThat(label.isInfoPopupVisible()).isFalse();
    }

    @Test
    void popupIsClampedToTheCurrentMonitorWorkingArea() {
        Rectangle workArea = new Rectangle(-1920, 20, 1920, 1040);

        assertThat(HoverInfoLabel.popupLocation(
                new Rectangle(-20, 1030, 18, 18),
                new Dimension(300, 100),
                workArea)).isEqualTo(new Point(-300, 926));
        assertThat(HoverInfoLabel.popupLocation(
                new Rectangle(-1918, 30, 18, 18),
                new Dimension(300, 100),
                workArea)).isEqualTo(new Point(-1920, 52));
    }

    @Test
    void iconRefreshesForLightAndDarkThemes() throws Exception {
        HoverInfoLabel label = onEdt(() -> {
            FlatLightLaf.setup();
            return new HoverInfoLabel(14);
        });
        FlatSVGIcon light = (FlatSVGIcon) label.getIcon();

        FlatSVGIcon dark = onEdt(() -> {
            FlatDarkLaf.setup();
            label.refreshTheme();
            return (FlatSVGIcon) label.getIcon();
        });

        assertThat(light.getName()).isEqualTo("icons/info.svg");
        assertThat(dark.getName()).isEqualTo("icons/info.svg");
        assertThat(light).isNotSameAs(dark);
    }

    private static void enter(HoverInfoLabel label) {
        label.getMouseListeners()[0].mouseEntered(mouseEvent(label, MouseEvent.MOUSE_ENTERED));
    }

    private static void exit(HoverInfoLabel label) {
        label.getMouseListeners()[0].mouseExited(mouseEvent(label, MouseEvent.MOUSE_EXITED));
    }

    private static MouseEvent mouseEvent(Component source, int id) {
        return new MouseEvent(source, id, System.currentTimeMillis(), 0, 9, 9, 0, false);
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

    private static final class TestHoverInfoLabel extends HoverInfoLabel {

        private TestHoverInfoLabel(PopupFactory popupFactory) {
            super(18, popupFactory);
        }

        @Override
        public boolean isShowing() {
            return true;
        }

        @Override
        public Point getLocationOnScreen() {
            return new Point(100, 100);
        }

        @Override
        Rectangle workingArea() {
            return new Rectangle(0, 0, 800, 600);
        }
    }

    private static final class TrackingPopupFactory extends PopupFactory {

        private int requests;
        private TrackingPopup lastPopup;

        @Override
        public Popup getPopup(Component owner, Component contents, int x, int y) {
            requests++;
            lastPopup = new TrackingPopup();
            return lastPopup;
        }
    }

    private static final class TrackingPopup extends Popup {

        private boolean shown;
        private boolean hidden;

        @Override
        public void show() {
            shown = true;
        }

        @Override
        public void hide() {
            hidden = true;
        }
    }
}
