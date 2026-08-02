package io.github.qiuspace.airplay.app;

import io.github.qiuspace.airplay.app.i18n.I18n;
import io.github.qiuspace.airplay.app.settings.AppSettings;
import io.github.qiuspace.airplay.app.settings.SettingsStore;
import io.github.qiuspace.airplay.app.theme.ThemeManager;
import io.github.qiuspace.airplay.app.ui.MainFrame;
import io.github.qiuspace.airplay.player.gstreamer.GstPlayer;
import io.github.qiuspace.airplay.player.gstreamer.GstRuntime;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public final class PlayerApp {

    private static final String INSTALLER_TEST_HOLD = "--installer-test-hold";
    private static final String INSTALLER_TEST_ENVIRONMENT = "AIRPLAY_RECEIVER_INSTALLER_TEST";

    private PlayerApp() {
    }

    public static void main(String[] args) throws Exception {
        if (UninstallCleanup.handle(args)) {
            return;
        }
        Files.createDirectories(AppPaths.logsDirectory());
        System.setProperty("APP_LOG_DIR", AppPaths.logsDirectory().toString());
        System.setProperty("apple.awt.application.name", "AirPlay Receiver");

        if (Arrays.asList(args).contains(INSTALLER_TEST_HOLD)) {
            holdInstalledRuntimeForInstallerTest();
            return;
        }
        if (Arrays.asList(args).contains("--self-test")) {
            runSelfTest();
            return;
        }

        SettingsStore settingsStore = new SettingsStore();
        AppSettings settings = settingsStore.load();
        ThemeManager themeManager = new ThemeManager(settings.theme());
        I18n i18n = new I18n(settings.language());

        try {
            ReceiverController controller = new ReceiverController(settingsStore, settings, themeManager);
            SwingUtilities.invokeLater(() -> {
                AppIcons.installTaskbarIcon();
                MainFrame frame = new MainFrame(controller, i18n);
                controller.attachView(frame);
                frame.setVisible(true);
                controller.start();
            });
        } catch (RuntimeException | LinkageError error) {
            LoggerFactory.getLogger(PlayerApp.class).error("Media runtime initialization failed", error);
            themeManager.close();
            String details = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(null,
                    i18n.text("error.mediaRuntime", details),
                    "AirPlay Receiver", JOptionPane.ERROR_MESSAGE));
            System.exit(1);
        }
    }

    private static void holdInstalledRuntimeForInstallerTest() throws InterruptedException {
        if (!"1".equals(System.getenv(INSTALLER_TEST_ENVIRONMENT))) {
            throw new IllegalArgumentException(INSTALLER_TEST_HOLD + " is reserved for installer verification");
        }
        System.out.println("AirPlay Receiver installer lock test is holding the packaged runtime.");
        new CountDownLatch(1).await();
    }

    private static void runSelfTest() {
        GstRuntime.RuntimeCheck runtime = GstRuntime.verifyInstallation();
        if (Runtime.version().feature() < 21) {
            throw new IllegalStateException("Java 21 or later is required");
        }
        if (!runtime.available()) {
            throw new IllegalStateException(String.join(System.lineSeparator(), runtime.problems()));
        }
        try (GstPlayer ignored = new GstPlayer()) {
            // Construct all production pipelines so missing properties or plug-ins fail the smoke test.
        }
        String result = "AirPlay Receiver self-test passed. GStreamer: " + runtime.root()
                + "; D3D11 hardware decoder available: "
                + GstRuntime.hardwareVideoDecodeAvailable();
        LoggerFactory.getLogger(PlayerApp.class).info(result);
        System.out.println(result);
    }
}
