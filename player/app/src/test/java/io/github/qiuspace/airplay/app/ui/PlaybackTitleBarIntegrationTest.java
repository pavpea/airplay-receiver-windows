package io.github.qiuspace.airplay.app.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PlaybackTitleBarIntegrationTest {

    @Test
    void darkPlaybackWindowAppliesItsThemeToTheRealFlatTitlePane() throws Exception {
        assumeTrue(Platform.isWindows(), "native playback integration test only runs on Windows");
        assumeFalse(GraphicsEnvironment.isHeadless(),
                "Windows playback CI must provide a non-headless desktop session");

        FlatLaf.registerCustomDefaultsSource("themes");
        FlatDarkLaf.setup();
        FlatLaf.setUseNativeWindowDecorations(true);

        Fixture fixture = onEdt(() -> {
            JFrame result = new JFrame("playback title color integration test");
            JRootPane rootPane = result.getRootPane();
            PlaybackWindow.applyPlaybackTitleBarTheme(rootPane);
            rootPane.putClientProperty(FlatClientProperties.USE_WINDOW_DECORATIONS, true);
            rootPane.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false);
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, false);
            PlaybackTitleBar bar = new PlaybackTitleBar(
                    new DefaultBoundedRangeModel(50, 0, 0, 100),
                    false, ignored -> {
                    }, ignored -> {
                    }, () -> {
                    });
            bar.setPlaybackDetails("<html>1920x1080<br>60.0 fps<br>D3D11 H.264</html>");
            result.setJMenuBar(bar);
            result.setSize(640, 400);
            result.setLocation(-10_000, -10_000);
            result.setVisible(true);
            PlaybackWindow.applyFlatTitlePaneTheme(
                    rootPane, PlaybackTitleBar.playbackBackground());
            return new Fixture(result, bar);
        });

        try {
            Color expected = new Color(0x11, 0x18, 0x2B);
            onEdt(() -> null);
            assertEquals(expected, onEdt(() -> fixture.frame().getRootPane().getClientProperty(
                    FlatClientProperties.TITLE_BAR_BACKGROUND)));
            Component titlePane = onEdt(() -> findComponent(
                    fixture.frame().getRootPane(), "com.formdev.flatlaf.ui.FlatTitlePane"));
            assertNotNull(titlePane);
            assertEquals(expected, onEdt(titlePane::getBackground));
            assertEquals(expected, onEdt(fixture.bar()::getBackground));
            assertEquals(expected, onEdt(() -> renderedCenter(fixture.bar())));
            JPanel captionButtons = onEdt(() ->
                    findCaptionButtonPanel((Container) titlePane));
            assertNotNull(captionButtons);
            assertTrue(onEdt(captionButtons::isOpaque));
            assertEquals(expected, onEdt(captionButtons::getBackground));
            assertEquals(expected, onEdt(() -> renderedPixel(captionButtons, 2, 2)));

            Color lightExpected = new Color(0xF8, 0xFA, 0xFF);
            onEdt(() -> {
                FlatLightLaf.setup();
                SwingUtilities.updateComponentTreeUI(fixture.frame());
                PlaybackWindow.applyPlaybackTitleBarTheme(
                        fixture.frame().getRootPane());
                PlaybackWindow.applyFlatTitlePaneTheme(
                        fixture.frame().getRootPane(),
                        PlaybackTitleBar.playbackBackground());
                fixture.frame().validate();
                return null;
            });
            Component lightTitlePane = onEdt(() -> findComponent(
                    fixture.frame().getRootPane(), "com.formdev.flatlaf.ui.FlatTitlePane"));
            assertNotNull(lightTitlePane);
            JPanel lightCaptionButtons = onEdt(() ->
                    findCaptionButtonPanel((Container) lightTitlePane));
            assertNotNull(lightCaptionButtons);
            assertEquals(lightExpected, onEdt(lightTitlePane::getBackground));
            assertEquals(lightExpected, onEdt(lightCaptionButtons::getBackground));
            assertEquals(lightExpected,
                    onEdt(() -> renderedPixel(lightCaptionButtons, 2, 2)));

            HoverInfoLabel details = onEdt(() -> findNamed(
                    fixture.bar(), "playbackBar.details", HoverInfoLabel.class));
            assertNotNull(details);
            assertTrue(onEdt(details::isShowing));
            assertTrue(onEdt(() -> {
                details.dispatchEvent(new MouseEvent(
                        details,
                        MouseEvent.MOUSE_ENTERED,
                        System.currentTimeMillis(),
                        0,
                        details.getWidth() / 2,
                        details.getHeight() / 2,
                        0,
                        false));
                return details.isInfoPopupVisible();
            }));
            assertFalse(onEdt(() -> {
                details.dispatchEvent(new MouseEvent(
                        details,
                        MouseEvent.MOUSE_EXITED,
                        System.currentTimeMillis(),
                        0,
                        details.getWidth() / 2,
                        details.getHeight() / 2,
                        0,
                        false));
                return details.isInfoPopupVisible();
            }));
        } finally {
            onEdt(() -> {
                fixture.frame().dispose();
                return null;
            });
        }
    }

    @Test
    void minimumPortraitWindowRightAlignsControlsAndOpensVerticalVolumeOnHover()
            throws Exception {
        assumeTrue(Platform.isWindows(), "native playback integration test only runs on Windows");
        assumeFalse(GraphicsEnvironment.isHeadless(),
                "Windows playback CI must provide a non-headless desktop session");

        FlatLaf.registerCustomDefaultsSource("themes");
        FlatLightLaf.setup();
        FlatLaf.setUseNativeWindowDecorations(true);

        Fixture fixture = onEdt(() -> {
            JFrame frame = new JFrame("playback title integration test");
            JRootPane rootPane = frame.getRootPane();
            rootPane.putClientProperty(FlatClientProperties.USE_WINDOW_DECORATIONS, true);
            rootPane.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false);
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, false);
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_MAXIMIZE, true);
            rootPane.putClientProperty(
                    FlatClientProperties.TITLE_BAR_HEIGHT, PlaybackTitleBar.HEIGHT);

            PlaybackTitleBar bar = new PlaybackTitleBar(
                    new DefaultBoundedRangeModel(50, 0, 0, 100),
                    false, ignored -> {
                    }, ignored -> {
                    }, () -> {
                    });
            bar.setVideoFormat(1179, 2556);
            frame.setJMenuBar(bar);
            frame.setMinimumSize(new Dimension(PlaybackTitleBar.MINIMUM_WINDOW_WIDTH, 260));
            frame.setSize(new Dimension(PlaybackTitleBar.MINIMUM_WINDOW_WIDTH, 700));
            frame.setLocation(-10_000, -10_000);
            frame.setVisible(true);
            bar.doLayout();
            return new Fixture(frame, bar);
        });

        try {
            onEdt(() -> null);
            Snapshot snapshot = onEdt(() -> {
                Rectangle nativeButtons = (Rectangle) fixture.frame().getRootPane()
                        .getClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS);
                Rectangle mute = boundsInRoot(fixture.frame(), fixture.bar().muteButton());
                Rectangle pin = boundsInRoot(
                        fixture.frame(), fixture.bar().alwaysOnTopButton());
                Rectangle bar = SwingUtilities.convertRectangle(
                        fixture.bar().getParent(),
                        fixture.bar().getBounds(),
                        fixture.frame().getRootPane());
                return new Snapshot(
                        nativeButtons,
                        mute,
                        pin,
                        bar,
                        fixture.frame().getWidth(),
                        fixture.bar().isFormatVisible());
            });

            assertNotNull(snapshot.nativeButtons());
            assertEquals(snapshot.nativeButtons().x, snapshot.bar().x + snapshot.bar().width);
            assertEquals(PlaybackTitleBar.MINIMUM_WINDOW_WIDTH, snapshot.windowWidth());
            assertFalse(snapshot.formatVisible());
            assertFalse(snapshot.mute().intersects(snapshot.nativeButtons()));
            assertFalse(snapshot.pin().intersects(snapshot.nativeButtons()));
            assertEquals(PlaybackTitleBar.CONTROL_SIZE, snapshot.mute().width);
            assertEquals(snapshot.mute().getSize(), snapshot.pin().getSize());
            assertEquals(snapshot.mute().x + snapshot.mute().width
                            + PlaybackTitleBar.CONTROL_GAP,
                    snapshot.pin().x);
            assertEquals(snapshot.pin().x + snapshot.pin().width
                            + PlaybackTitleBar.NATIVE_GROUP_GAP,
                    snapshot.nativeButtons().x);
            assertEquals(snapshot.nativeButtons().height * 3, snapshot.nativeButtons().width);

            onEdt(() -> {
                AbstractButton mute = fixture.bar().muteButton();
                mute.dispatchEvent(new MouseEvent(
                        mute,
                        MouseEvent.MOUSE_ENTERED,
                        System.currentTimeMillis(),
                        0,
                        mute.getWidth() / 2,
                        mute.getHeight() / 2,
                        0,
                        false));
                return null;
            });
            Thread.sleep(240);
            assertTrue(onEdt(() -> fixture.bar().volumePopup().isVisible()));
            assertEquals(SwingConstants.VERTICAL,
                    onEdt(() -> fixture.bar().popupVolume().getOrientation()));
        } finally {
            onEdt(() -> {
                fixture.frame().dispose();
                return null;
            });
        }
    }

    private static Rectangle boundsInRoot(JFrame frame, AbstractButton button) {
        return SwingUtilities.convertRectangle(
                button.getParent(), button.getBounds(), frame.getRootPane());
    }

    private static Component findComponent(Container parent, String className) {
        for (Component child : parent.getComponents()) {
            if (child.getClass().getName().equals(className)) {
                return child;
            }
            if (child instanceof Container nested) {
                Component match = findComponent(nested, className);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T findNamed(
            Container parent, String name, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container nested) {
                T match = findNamed(nested, name, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static JPanel findCaptionButtonPanel(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JPanel panel
                    && directButtonCount(panel) >= 3) {
                return panel;
            }
            if (child instanceof Container nested) {
                JPanel match = findCaptionButtonPanel(nested);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static long directButtonCount(Container parent) {
        return java.util.Arrays.stream(parent.getComponents())
                .filter(AbstractButton.class::isInstance)
                .count();
    }

    private static Color renderedCenter(Component component) {
        return renderedPixel(
                component, component.getWidth() / 2, component.getHeight() / 2);
    }

    private static Color renderedPixel(Component component, int x, int y) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                component.getWidth(), component.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        component.paint(graphics);
        graphics.dispose();
        return new Color(image.getRGB(x, y), true);
    }

    private static <T> T onEdt(EdtSupplier<T> supplier)
            throws InvocationTargetException, InterruptedException {
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

    private record Fixture(JFrame frame, PlaybackTitleBar bar) {
    }

    private record Snapshot(Rectangle nativeButtons,
                            Rectangle mute,
                            Rectangle pin,
                            Rectangle bar,
                            int windowWidth,
                            boolean formatVisible) {
    }
}
