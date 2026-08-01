package io.github.qiuspace.airplay.app.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MainWindowChromeIntegrationTest {

    private static final int GWL_STYLE = -16;
    private static final long WS_MAXIMIZEBOX = 0x00010000L;
    private static final long WS_MINIMIZEBOX = 0x00020000L;
    private static final long WS_THICKFRAME = 0x00040000L;
    private static final long WS_SYSMENU = 0x00080000L;

    @Test
    void realWindowsFrameKeepsActionsClearOfOnlyMinimizeAndCloseButtons() throws Exception {
        assumeTrue(Platform.isWindows(), "native main-window integration test only runs on Windows");
        assumeFalse(GraphicsEnvironment.isHeadless(),
                "Windows main-window CI must provide a non-headless desktop session");

        FlatLightLaf.setup();
        FlatLaf.setUseNativeWindowDecorations(true);
        WindowFixture fixture = onEdt(() -> {
            JFrame frame = new JFrame("main chrome integration test");
            MainWindowChrome.configure(frame);
            frame.setLocation(-10_000, -10_000);

            JButton logs = new JButton("logs");
            JButton settings = new JButton("settings");
            JPanel appBar = MainWindowChrome.createAppBar(logs, settings);
            JPanel actions = (JPanel) ((BorderLayout) appBar.getLayout())
                    .getLayoutComponent(BorderLayout.EAST);
            java.awt.Component separator = java.util.Arrays.stream(actions.getComponents())
                    .filter(component -> "appBar.actionSeparator".equals(component.getName()))
                    .findFirst()
                    .orElseThrow();
            JPanel placeholder = (JPanel) actions.getComponent(actions.getComponentCount() - 1);
            frame.add(appBar, BorderLayout.NORTH);
            frame.setVisible(true);
            return new WindowFixture(frame, appBar, logs, settings, separator, placeholder);
        });

        try {
            onEdt(() -> null);
            assertChrome(fixture);

            onEdt(() -> {
                FlatDarkLaf.setup();
                SwingUtilities.updateComponentTreeUI(fixture.frame());
                return null;
            });
            onEdt(() -> null);
            assertChrome(fixture);
        } finally {
            onEdt(() -> {
                fixture.frame().dispose();
                return null;
            });
        }
    }

    private static void assertChrome(WindowFixture fixture) throws Exception {
        ChromeSnapshot snapshot = onEdt(() -> {
            Rectangle nativeButtons = (Rectangle) fixture.frame().getRootPane()
                    .getClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS);
            Rectangle logs = SwingUtilities.convertRectangle(
                    fixture.logs().getParent(), fixture.logs().getBounds(), fixture.frame().getRootPane());
            Rectangle settings = SwingUtilities.convertRectangle(
                    fixture.settings().getParent(), fixture.settings().getBounds(), fixture.frame().getRootPane());
            Rectangle separator = SwingUtilities.convertRectangle(
                    fixture.separator().getParent(), fixture.separator().getBounds(),
                    fixture.frame().getRootPane());
            return new ChromeSnapshot(nativeButtons, logs, settings, separator,
                    fixture.placeholder().getPreferredSize(),
                    fixture.appBar().getBounds(),
                    fixture.frame().isResizable());
        });

        assertNotNull(snapshot.nativeButtons());
        assertTrue(snapshot.nativeButtons().width > 0);
        assertTrue(snapshot.nativeButtons().height > 0);
        assertEquals(snapshot.nativeButtons().height * 2, snapshot.nativeButtons().width);
        assertEquals(snapshot.nativeButtons().getSize(), snapshot.placeholderSize());
        assertFalse(snapshot.logs().intersects(snapshot.nativeButtons()));
        assertFalse(snapshot.settings().intersects(snapshot.nativeButtons()));
        assertEquals(MainWindowChrome.ACTION_BUTTON_SIZE, snapshot.logs().width);
        assertEquals(MainWindowChrome.ACTION_BUTTON_SIZE, snapshot.logs().height);
        assertEquals(snapshot.logs().getSize(), snapshot.settings().getSize());
        assertEquals(snapshot.logs().x + snapshot.logs().width, snapshot.settings().x);
        assertEquals(MainWindowChrome.ACTION_SEPARATOR_WIDTH, snapshot.separator().width);
        assertEquals(snapshot.settings().x + snapshot.settings().width, snapshot.separator().x);
        assertEquals(snapshot.separator().x + snapshot.separator().width, snapshot.nativeButtons().x);
        assertEquals(0, snapshot.appBar().y);
        assertEquals(MainWindowChrome.TITLE_BAR_HEIGHT, snapshot.appBar().height);
        assertFalse(snapshot.resizable());

        HWND hwnd = new HWND(Native.getWindowPointer(fixture.frame()));
        long style = User32.INSTANCE.GetWindowLongPtr(hwnd, GWL_STYLE).longValue();
        assertEquals(0, style & WS_THICKFRAME);
        assertEquals(0, style & WS_MAXIMIZEBOX);
        assertTrue((style & WS_MINIMIZEBOX) != 0);
        assertTrue((style & WS_SYSMENU) != 0);
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

    private record WindowFixture(
            JFrame frame, JPanel appBar, JButton logs, JButton settings,
            java.awt.Component separator, JPanel placeholder) {
    }

    private record ChromeSnapshot(Rectangle nativeButtons,
                                  Rectangle logs,
                                  Rectangle settings,
                                  Rectangle separator,
                                  Dimension placeholderSize,
                                  Rectangle appBar,
                                  boolean resizable) {
    }
}
