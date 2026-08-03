package io.github.qiuspace.airplay.app.ui;

import com.formdev.flatlaf.FlatClientProperties;
import io.github.qiuspace.airplay.app.AppIcons;
import io.github.qiuspace.airplay.app.ReceiverController;
import io.github.qiuspace.airplay.app.i18n.I18n;
import io.github.qiuspace.airplay.app.platform.WindowsAspectRatioWindowResizer;
import io.github.qiuspace.airplay.app.settings.AppSettings;
import io.github.qiuspace.airplay.server.SessionInfo;
import io.github.qiuspace.airplay.player.gstreamer.PlaybackMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractButton;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** A dedicated, reusable window for the active mirroring session. */
final class PlaybackWindow extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(PlaybackWindow.class);
    static final Dimension FALLBACK_MINIMUM_SIZE = new Dimension(360, 260);
    static final int MINIMUM_PORTRAIT_WIDTH = 280;
    private static final int SCREEN_GAP = 18;

    private final ReceiverController controller;
    private final I18n i18n;
    private final JPanel player = new VideoStack();
    private final JPanel videoPanel = new JPanel(new BorderLayout());
    private final JPanel transitionVeil = new JPanel();
    private final DefaultBoundedRangeModel volumeModel;
    private final PlaybackTitleBar titleControls;
    private final PlaybackRotationModel rotationModel = new PlaybackRotationModel();
    private WindowsAspectRatioWindowResizer aspectResizer;

    private boolean activeSession;
    private boolean disconnectRequested;
    private String sessionAddress;
    private AppSettings sessionSettings;
    private int sourceWidth;
    private int sourceHeight;
    private int pendingFrameWidth;
    private int pendingFrameHeight;
    private PlaybackMetrics playbackMetrics;
    private Rectangle normalBounds;
    private Rectangle pendingNormalBounds;
    private Dimension stableChrome = new Dimension(0, PlaybackTitleBar.HEIGHT);
    private GraphicsConfiguration displayAnchor;
    private Dimension userInteractionStartSize;
    private Rectangle programmaticBounds;
    private WindowCenterAnchor rotationCenterAnchor;

    PlaybackWindow(ReceiverController controller, I18n i18n) {
        super("AirPlay Receiver");
        this.controller = controller;
        this.i18n = i18n;
        this.volumeModel = new DefaultBoundedRangeModel(
                (int) Math.round(controller.settings().volume() * 100), 0, 0, 100);
        this.titleControls = new PlaybackTitleBar(
                volumeModel,
                controller.muted(),
                this::updateMutedState,
                this::setAlwaysOnTop,
                () -> controller.setVolume(volumeModel.getValue() / 100.0));
        setIconImages(AppIcons.windowIcons());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(FALLBACK_MINIMUM_SIZE);
        setSize(980, 640);
        buildUi();
        installBehavior();
        refreshTexts();
    }

    void showSession(SessionInfo session, AppSettings settings) {
        activeSession = true;
        disconnectRequested = false;
        sessionSettings = settings;
        sessionAddress = session.remoteAddress() == null
                ? i18n.text("player.unknownDevice")
                : session.remoteAddress().getAddress().getHostAddress();
        titleControls.setSessionToolTip(i18n.text("player.device", sessionAddress));
        setTitle(playbackTitle());
        if (sourceWidth > 0 && sourceHeight > 0) {
            prepareAndShow(settings);
        }
    }

    void endSession() {
        activeSession = false;
        disconnectRequested = false;
        sessionAddress = null;
        sessionSettings = null;
        sourceWidth = 0;
        sourceHeight = 0;
        playbackMetrics = null;
        pendingNormalBounds = null;
        rotationModel.reset();
        displayAnchor = null;
        userInteractionStartSize = null;
        programmaticBounds = null;
        rotationCenterAnchor = null;
        titleControls.clearVideoFormat();
        endVideoTransition();
        if (aspectResizer != null) {
            aspectResizer.clearVideoFormat();
        }
        setMinimumSize(FALLBACK_MINIMUM_SIZE);
        setVisible(false);
    }

    void updateVideoFormat(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int previousWidth = sourceWidth;
        int previousHeight = sourceHeight;
        boolean firstFormat = previousWidth <= 0 || previousHeight <= 0;
        boolean aspectChanged = !firstFormat
                && !sameAspect(previousWidth, previousHeight, width, height);
        boolean orientationChanged = !firstFormat
                && isPortrait(previousWidth, previousHeight)
                != isPortrait(width, height);
        Rectangle formatBase = aspectChanged
                ? currentNormalWindowBounds()
                : null;
        if (firstFormat || aspectChanged) {
            beginVideoTransition(width, height);
        }
        sourceWidth = width;
        sourceHeight = height;
        titleControls.setVideoFormat(width, height);
        updateDetailsTooltip();
        ensureDisplayable();
        ensureAspectResizer();
        if (firstFormat) {
            fitWindowToVideo(width, height);
        } else {
            updateMinimumAndAspect(width, height);
            if (orientationChanged) {
                applyOrientationChange(
                        previousWidth, previousHeight, width, height, formatBase);
            } else if (aspectChanged) {
                applySameOrientationFormatChange(width, height, formatBase);
            }
        }
        if (activeSession && sessionSettings != null) {
            prepareAndShow(sessionSettings);
        }
    }

    void videoFrameReady(int width, int height) {
        if (width == pendingFrameWidth && height == pendingFrameHeight) {
            endVideoTransition();
        }
    }

    void updatePlaybackMetrics(PlaybackMetrics metrics) {
        if (metrics == null) {
            return;
        }
        playbackMetrics = metrics;
        if (activeSession) {
            updateDetailsTooltip();
        }
    }

    void refreshTexts() {
        setTitle(playbackTitle());
        titleControls.setTexts(
                i18n.text("player.mute"),
                i18n.text("player.unmute"),
                i18n.text("player.volume"),
                i18n.text("player.alwaysOnTop"));
        titleControls.setMuted(controller.muted());
        titleControls.refreshTheme();
        refreshPlaybackTitleBarTheme();
        updateDetailsTooltip();
        if (sessionAddress != null) {
            titleControls.setSessionToolTip(i18n.text("player.device", sessionAddress));
        }
        SwingUtilities.invokeLater(this::updateAspectResizer);
    }

    void closeWindow() {
        WindowsAspectRatioWindowResizer currentResizer = aspectResizer;
        if (currentResizer != null) {
            currentResizer.close();
        }
        dispose();
        aspectResizer = null;
    }

    private void buildUi() {
        JRootPane rootPane = getRootPane();
        refreshPlaybackTitleBarTheme();
        rootPane.putClientProperty(FlatClientProperties.USE_WINDOW_DECORATIONS, true);
        rootPane.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, false);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICONIFFY, true);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_MAXIMIZE, true);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_CLOSE, true);
        rootPane.putClientProperty(
                FlatClientProperties.TITLE_BAR_HEIGHT, PlaybackTitleBar.HEIGHT);
        setJMenuBar(titleControls);
        player.setBackground(Color.BLACK);
        videoPanel.setBackground(Color.BLACK);
        videoPanel.setAlignmentX(CENTER_ALIGNMENT);
        videoPanel.setAlignmentY(CENTER_ALIGNMENT);
        videoPanel.add(controller.videoComponent(), BorderLayout.CENTER);
        transitionVeil.setBackground(Color.BLACK);
        transitionVeil.setOpaque(true);
        transitionVeil.setAlignmentX(CENTER_ALIGNMENT);
        transitionVeil.setAlignmentY(CENTER_ALIGNMENT);
        transitionVeil.setVisible(false);
        player.add(videoPanel);
        player.add(transitionVeil);
        player.setComponentZOrder(transitionVeil, 0);
        setContentPane(player);
    }

    private void updateDetailsTooltip() {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            titleControls.clearVideoFormat();
            return;
        }
        PlaybackMetrics metrics = playbackMetrics;
        double fps = metrics == null ? 0 : metrics.framesPerSecond();
        String decoder = metrics != null
                && metrics.decoderPath() == PlaybackMetrics.DecoderPath.HARDWARE
                ? i18n.text("player.decoderHardware")
                : i18n.text("player.decoderSoftware");
        String tooltip = "<html>"
                + i18n.text("player.detailsFormat", sourceWidth, sourceHeight) + "<br>"
                + i18n.text("player.detailsMetrics", fps) + "<br>"
                + i18n.text("player.detailsCodec") + "<br>"
                + i18n.text("player.detailsDecoder", decoder)
                + "</html>";
        titleControls.setPlaybackDetails(tooltip);
    }

    private void installBehavior() {
        addPropertyChangeListener("graphicsConfiguration", event ->
                SwingUtilities.invokeLater(this::updateAspectResizer));
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent event) {
                rememberNormalBounds();
            }

            @Override
            public void componentResized(ComponentEvent event) {
                rememberNormalBounds();
            }
        });
        addWindowStateListener(event -> {
            boolean maximized = (event.getNewState() & MAXIMIZED_BOTH) != 0;
            SwingUtilities.invokeLater(this::refreshPlaybackTitleBarTheme);
            if (!maximized) {
                Rectangle pending = pendingNormalBounds;
                if (pending != null) {
                    pendingNormalBounds = null;
                    setBounds(pending);
                    validate();
                }
                rememberNormalBounds();
                SwingUtilities.invokeLater(this::reconcileRestoredVideoBounds);
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent event) {
                refreshPlaybackTitleBarTheme();
            }

            @Override
            public void windowDeactivated(WindowEvent event) {
                refreshPlaybackTitleBarTheme();
            }

            @Override
            public void windowClosing(WindowEvent event) {
                if (activeSession) {
                    requestDisconnect();
                } else {
                    setVisible(false);
                }
            }
        });
    }

    private void requestDisconnect() {
        if (!activeSession || disconnectRequested) {
            return;
        }
        disconnectRequested = true;
        setVisible(false);
        controller.disconnectSession();
    }

    private void updateMutedState(boolean muted) {
        controller.setMuted(muted);
        titleControls.setMuted(muted);
    }

    private void fitWindowToVideo(int width, int height) {
        GraphicsConfiguration configuration = currentGraphicsConfiguration();
        Rectangle screen = usableScreenBounds(configuration);
        int chromeWidth = chromeWidth();
        int chromeHeight = chromeHeight();
        int availableWidth = Math.max(1, screen.width - SCREEN_GAP * 2 - chromeWidth);
        int availableHeight = Math.max(1, screen.height - SCREEN_GAP * 2 - chromeHeight);
        rotationModel.initialize(initialPortraitBox(
                width, height, availableWidth, availableHeight));
        Dimension target = rotationModel.sizeFor(width, height);
        Dimension minimum = minimumVideoSize(width, height, screen, chromeWidth, chromeHeight);
        if (target.width < minimum.width || target.height < minimum.height) {
            target = minimum;
        }
        setMinimumSize(frameSizeForVideo(minimum));
        Rectangle applied = applyVideoSize(target, screen);
        rotationCenterAnchor = WindowCenterAnchor.from(applied);
        logGeometry("initial", width, height, configuration, screen, target, applied);
        updateAspectResizer();
    }

    private Rectangle applyVideoSize(Dimension videoSize, Rectangle usableScreen) {
        Rectangle target = sideWindowBounds(
                frameSizeForVideo(videoSize), usableScreen, SCREEN_GAP);
        Rectangle applied = applyExactVideoBounds(
                target, videoSize, usableScreen);
        normalBounds = new Rectangle(applied);
        return applied;
    }

    static Rectangle sideWindowBounds(Dimension windowSize, Rectangle usableScreen, int gap) {
        int x = usableScreen.x + usableScreen.width - windowSize.width - gap;
        int y = usableScreen.y + Math.max(gap, (usableScreen.height - windowSize.height) / 2);
        return new Rectangle(
                Math.max(usableScreen.x + gap, x),
                y,
                windowSize.width,
                windowSize.height);
    }

    private void ensureDisplayable() {
        if (!isDisplayable()) {
            addNotify();
            validate();
            layoutTree(getRootPane());
            captureStableChrome();
            refreshPlaybackTitleBarTheme();
        }
    }

    private void ensureAspectResizer() {
        if (aspectResizer == null) {
            aspectResizer = WindowsAspectRatioWindowResizer.install(
                    this,
                    () -> SwingUtilities.invokeLater(
                            this::completeNativeUserInteraction));
            setResizable(aspectResizer.isNativeActive());
        }
    }

    private void prepareAndShow(AppSettings settings) {
        ensureDisplayable();
        ensureAspectResizer();
        if (!isVisible()) {
            setAutoRequestFocus(settings.bringToFront());
            setVisible(true);
            setAutoRequestFocus(true);
        }
        if (settings.bringToFront()) {
            setExtendedState(getExtendedState() & ~ICONIFIED);
            toFront();
            requestFocus();
        }
    }

    private void updateAspectResizer() {
        if (aspectResizer != null && sourceWidth > 0 && sourceHeight > 0) {
            aspectResizer.setVideoFormat(
                    sourceWidth, sourceHeight, chromeWidth(), chromeHeight(), getMinimumSize());
        }
    }

    private void refreshPlaybackTitleBarTheme() {
        JRootPane rootPane = getRootPane();
        applyPlaybackTitleBarTheme(rootPane);
        applyFlatTitlePaneTheme(rootPane, PlaybackTitleBar.playbackBackground());
        if (isDisplayable()) {
            SwingUtilities.invokeLater(() -> {
                applyFlatTitlePaneTheme(
                        getRootPane(), PlaybackTitleBar.playbackBackground());
                getRootPane().repaint();
            });
        }
    }

    static void applyPlaybackTitleBarTheme(JRootPane rootPane) {
        Color background = solidColor(PlaybackTitleBar.playbackBackground());
        rootPane.putClientProperty(
                FlatClientProperties.TITLE_BAR_BACKGROUND,
                null);
        rootPane.putClientProperty(
                FlatClientProperties.TITLE_BAR_BACKGROUND,
                background);
        rootPane.repaint();
    }

    static boolean applyFlatTitlePaneTheme(Container root, Color background) {
        Color fill = solidColor(background);
        for (Component child : root.getComponents()) {
            if ("com.formdev.flatlaf.ui.FlatTitlePane".equals(
                    child.getClass().getName())) {
                child.setBackground(fill);
                applyCaptionAreaBackgrounds((Container) child, fill);
                child.repaint();
                return true;
            }
            if (child instanceof Container nested
                    && applyFlatTitlePaneTheme(nested, fill)) {
                return true;
            }
        }
        return false;
    }

    private static void applyCaptionAreaBackgrounds(
            Container root, Color background) {
        for (Component child : root.getComponents()) {
            if (child instanceof AbstractButton button) {
                button.setBackground(background);
            }
            if (child instanceof Container nested) {
                if (child instanceof JPanel panel
                        && containsCaptionButton(panel)) {
                    panel.setOpaque(true);
                    panel.setBackground(background);
                }
                applyCaptionAreaBackgrounds(nested, background);
            }
        }
    }

    private static boolean containsCaptionButton(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof AbstractButton) {
                return true;
            }
            if (child instanceof Container nested
                    && containsCaptionButton(nested)) {
                return true;
            }
        }
        return false;
    }

    private static Color solidColor(Color color) {
        return new Color(color.getRGB(), true);
    }

    private void updateMinimumAndAspect(int width, int height) {
        GraphicsConfiguration configuration = currentGraphicsConfiguration();
        Rectangle screen = usableScreenBounds(configuration);
        Dimension minimum = minimumVideoSize(
                width, height, screen, chromeWidth(), chromeHeight());
        setMinimumSize(frameSizeForVideo(minimum));
        updateAspectResizer();
    }

    private void applyOrientationChange(int previousWidth,
                                        int previousHeight,
                                        int width,
                                        int height,
                                        Rectangle currentNormal) {
        GraphicsConfiguration configuration = currentGraphicsConfiguration();
        Rectangle screen = usableScreenBounds(configuration);
        int chromeWidth = chromeWidth();
        int chromeHeight = chromeHeight();
        Dimension currentVideo = rotationModel.sizeFor(previousWidth, previousHeight);
        if (currentVideo == null) {
            currentVideo = contentSize(currentNormal, chromeWidth, chromeHeight);
            rotationModel.initialize(isPortrait(previousWidth, previousHeight)
                    ? currentVideo
                    : new Dimension(currentVideo.height, currentVideo.width));
        }
        Dimension targetVideo = automaticVideoSize(
                rotationModel.sizeFor(width, height),
                width, height, screen, chromeWidth, chromeHeight, SCREEN_GAP);
        Rectangle target = rotatedWindowBounds(
                currentRotationCenter(currentNormal), targetVideo, screen,
                chromeWidth, chromeHeight, SCREEN_GAP);
        if ((getExtendedState() & MAXIMIZED_BOTH) != 0) {
            boolean nativeUpdated = aspectResizer != null
                    && aspectResizer.updateNormalBounds(currentNormal, target);
            normalBounds = new Rectangle(target);
            pendingNormalBounds = nativeUpdated ? null : new Rectangle(target);
            logGeometry(
                    "orientation-maximized", width, height,
                    configuration, screen, targetVideo, target);
            return;
        }

        pendingNormalBounds = null;
        Rectangle applied = applyExactVideoBounds(
                target, targetVideo, screen);
        normalBounds = new Rectangle(applied);
        logGeometry(
                "orientation", width, height,
                configuration, screen, targetVideo, applied);
        updateAspectResizer();
    }

    private void applySameOrientationFormatChange(
            int width, int height, Rectangle currentNormal) {
        Rectangle screen = usableScreenBounds(currentGraphicsConfiguration());
        int chromeWidth = chromeWidth();
        int chromeHeight = chromeHeight();
        Dimension targetVideo = automaticVideoSize(
                rotationModel.sizeFor(width, height),
                width, height, screen, chromeWidth, chromeHeight, SCREEN_GAP);
        Rectangle applied;
        if (targetVideo == null) {
            applied = applyBoundsMatchingVideo(
                    currentNormal, width, height, screen);
        } else {
            Rectangle target = rotatedWindowBounds(
                    currentRotationCenter(currentNormal), targetVideo, screen,
                    chromeWidth, chromeHeight, SCREEN_GAP);
            applied = applyExactVideoBounds(target, targetVideo, screen);
        }
        normalBounds = new Rectangle(applied);
        updateAspectResizer();
    }

    private void beginVideoTransition(int width, int height) {
        pendingFrameWidth = width;
        pendingFrameHeight = height;
        transitionVeil.setVisible(true);
        player.revalidate();
        player.repaint();
    }

    private void endVideoTransition() {
        pendingFrameWidth = 0;
        pendingFrameHeight = 0;
        transitionVeil.setVisible(false);
        player.revalidate();
        player.repaint();
    }

    private void rememberNormalBounds() {
        if (isVisible() && (getExtendedState() & MAXIMIZED_BOTH) == 0
                && pendingNormalBounds == null) {
            Rectangle current = new Rectangle(getBounds());
            Rectangle programmed = programmaticBounds;
            if (programmed != null) {
                programmaticBounds = null;
                if (programmed.equals(current)) {
                    return;
                }
            }
            if (pendingFrameWidth > 0 || pendingFrameHeight > 0) {
                return;
            }
            if (aspectResizer != null && aspectResizer.isUserSizing()
                    && userInteractionStartSize == null) {
                Rectangle previous = normalBounds;
                userInteractionStartSize = previous == null
                        ? current.getSize()
                        : previous.getSize();
            }
            normalBounds = current;
        }
    }

    private void completeNativeUserInteraction() {
        if (!isDisplayable()) {
            userInteractionStartSize = null;
            return;
        }
        Rectangle current = new Rectangle(getBounds());
        Dimension startSize = userInteractionStartSize;
        userInteractionStartSize = null;
        GraphicsConfiguration actualConfiguration = getGraphicsConfiguration();
        if (actualConfiguration != null) {
            displayAnchor = actualConfiguration;
        }
        validate();
        layoutTree(getRootPane());
        captureStableChrome();
        Rectangle screen = usableScreenBounds(currentGraphicsConfiguration());
        boolean resized = startSize != null && !startSize.equals(current.getSize());
        if (resized && sourceWidth > 0 && sourceHeight > 0) {
            rotationModel.rememberUserSize(
                    sourceWidth, sourceHeight, currentContentSize(current));
        }
        rotationCenterAnchor = WindowCenterAnchor.from(current);
        Dimension expectedVideo = rotationModel.sizeFor(sourceWidth, sourceHeight);
        normalBounds = new Rectangle(current);
        updateAspectResizer();
        if (sourceWidth > 0 && sourceHeight > 0) {
            logGeometry(
                    resized ? "user-resize" : "user-move",
                    sourceWidth, sourceHeight,
                    currentGraphicsConfiguration(), screen,
                    expectedVideo, current);
        }
    }

    private Rectangle currentNormalWindowBounds() {
        return normalBounds == null
                ? new Rectangle(getBounds())
                : new Rectangle(normalBounds);
    }

    private String playbackTitle() {
        return "AirPlay Receiver";
    }

    static Dimension fitVideoSize(int sourceWidth,
                                  int sourceHeight,
                                  int availableWidth,
                                  int availableHeight) {
        double aspect = (double) sourceWidth / sourceHeight;
        int height = Math.max(1, Math.min(availableHeight, (int) Math.floor(availableWidth / aspect)));
        int width = Math.max(1, (int) Math.round(height * aspect));
        while (width > availableWidth && height > 1) {
            width = Math.max(1, (int) Math.round(--height * aspect));
        }
        return new Dimension(width, height);
    }

    static Dimension initialVideoSize(int sourceWidth,
                                      int sourceHeight,
                                      int availableWidth,
                                      int availableHeight) {
        Dimension portraitBox = initialPortraitBox(
                sourceWidth, sourceHeight, availableWidth, availableHeight);
        Dimension available = isPortrait(sourceWidth, sourceHeight)
                ? portraitBox
                : new Dimension(portraitBox.height, portraitBox.width);
        return fitVideoSize(
                sourceWidth, sourceHeight, available.width, available.height);
    }

    static Dimension initialPortraitBox(int sourceWidth,
                                        int sourceHeight,
                                        int availableWidth,
                                        int availableHeight) {
        int portraitWidth = Math.min(sourceWidth, sourceHeight);
        int portraitHeight = Math.max(sourceWidth, sourceHeight);
        int pairLimit = Math.max(1, Math.min(availableWidth, availableHeight));
        return fitVideoSize(
                portraitWidth, portraitHeight, pairLimit, pairLimit);
    }

    static Rectangle rotatedWindowBounds(Rectangle currentWindow,
                                         Dimension targetVideoSize,
                                         Rectangle usableScreen,
                                         int chromeWidth,
                                         int chromeHeight,
                                         int gap) {
        return rotatedWindowBounds(
                WindowCenterAnchor.from(currentWindow),
                targetVideoSize, usableScreen, chromeWidth, chromeHeight, gap);
    }

    static Rectangle rotatedWindowBounds(WindowCenterAnchor centerAnchor,
                                         Dimension targetVideoSize,
                                         Rectangle usableScreen,
                                         int chromeWidth,
                                         int chromeHeight,
                                         int gap) {
        return centeredWindowBounds(
                centerAnchor,
                targetVideoSize.width + chromeWidth,
                targetVideoSize.height + chromeHeight,
                usableScreen,
                gap);
    }

    static Dimension automaticVideoSize(Dimension requestedVideoSize,
                                        int sourceWidth,
                                        int sourceHeight,
                                        Rectangle usableScreen,
                                        int chromeWidth,
                                        int chromeHeight,
                                        int gap) {
        if (requestedVideoSize == null) {
            return null;
        }
        int maximumWidth = Math.max(
                1, usableScreen.width - gap * 2 - Math.max(0, chromeWidth));
        int maximumHeight = Math.max(
                1, usableScreen.height - gap * 2 - Math.max(0, chromeHeight));
        if (requestedVideoSize.width <= maximumWidth
                && requestedVideoSize.height <= maximumHeight) {
            return new Dimension(requestedVideoSize);
        }
        return fitVideoSize(
                sourceWidth, sourceHeight, maximumWidth, maximumHeight);
    }

    private WindowCenterAnchor currentRotationCenter(Rectangle fallbackBounds) {
        if (rotationCenterAnchor == null) {
            rotationCenterAnchor = WindowCenterAnchor.from(fallbackBounds);
        }
        return rotationCenterAnchor;
    }

    static WindowCenterAnchor windowCenterAnchor(Rectangle bounds) {
        return WindowCenterAnchor.from(bounds);
    }

    private static Rectangle centeredWindowBounds(WindowCenterAnchor centerAnchor,
                                                  int width,
                                                  int height,
                                                  Rectangle usableScreen,
                                                  int gap) {
        int minimumX = usableScreen.x + gap;
        int minimumY = usableScreen.y + gap;
        int maximumX = usableScreen.x + usableScreen.width - gap - width;
        int maximumY = usableScreen.y + usableScreen.height - gap - height;
        return new Rectangle(
                clamp(centerAnchor.leftFor(width),
                        minimumX, Math.max(minimumX, maximumX)),
                clamp(centerAnchor.topFor(height),
                        minimumY, Math.max(minimumY, maximumY)),
                width,
                height);
    }

    private static Rectangle centeredWindowBounds(Rectangle currentWindow,
                                                  int width,
                                                  int height,
                                                  Rectangle usableScreen,
                                                  int gap) {
        return centeredWindowBounds(
                WindowCenterAnchor.from(currentWindow),
                width, height, usableScreen, gap);
    }

    private Rectangle usableScreenBounds(GraphicsConfiguration configuration) {
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom);
    }

    static Dimension minimumVideoSize(int width,
                                      int height,
                                      Rectangle screen,
                                      int chromeWidth,
                                      int chromeHeight) {
        double aspect = (double) width / height;
        int maximumWidth = Math.max(1, (int) (screen.width * 0.82) - chromeWidth);
        int maximumHeight = Math.max(1, (int) (screen.height * 0.82) - chromeHeight);
        int minimumWidth;
        int minimumHeight;
        if (aspect < 1) {
            minimumWidth = MINIMUM_PORTRAIT_WIDTH;
            minimumHeight = (int) Math.round(minimumWidth / aspect);
        } else {
            minimumWidth = Math.max(
                    1, PlaybackTitleBar.MINIMUM_WINDOW_WIDTH - chromeWidth);
            minimumHeight = Math.max(
                    1, (int) Math.round(minimumWidth / aspect));
        }
        if (minimumWidth > maximumWidth || minimumHeight > maximumHeight) {
            return fitVideoSize(width, height, maximumWidth, maximumHeight);
        }
        return new Dimension(minimumWidth, minimumHeight);
    }

    private GraphicsConfiguration currentGraphicsConfiguration() {
        if (displayAnchor != null) {
            return displayAnchor;
        }
        GraphicsConfiguration configuration = getGraphicsConfiguration();
        displayAnchor = configuration != null
                ? configuration
                : GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        return displayAnchor;
    }

    private Dimension frameSizeForVideo(Dimension videoSize) {
        return new Dimension(videoSize.width + chromeWidth(), videoSize.height + chromeHeight());
    }

    private void captureStableChrome() {
        stableChrome = stableChromeSize(
                getSize(), player.getSize(), getInsets(), getRootPane().getInsets());
    }

    static Dimension stableChromeSize(Dimension outerSize,
                                      Dimension contentSize,
                                      Insets frameInsets,
                                      Insets rootInsets) {
        int fallbackWidth = Math.max(
                0,
                frameInsets.left + frameInsets.right
                        + rootInsets.left + rootInsets.right);
        int fallbackHeight = Math.max(
                PlaybackTitleBar.HEIGHT,
                frameInsets.top + frameInsets.bottom
                        + rootInsets.top + rootInsets.bottom
                        + PlaybackTitleBar.HEIGHT);
        int measuredWidth = outerSize.width - contentSize.width;
        int measuredHeight = outerSize.height - contentSize.height;
        boolean stableMeasurement = contentSize.width > 0
                && contentSize.height > 0
                && measuredWidth >= 0
                && measuredWidth <= 96
                && measuredHeight >= PlaybackTitleBar.HEIGHT
                && measuredHeight <= PlaybackTitleBar.HEIGHT + 128;
        return stableMeasurement
                ? new Dimension(measuredWidth, measuredHeight)
                : new Dimension(fallbackWidth, fallbackHeight);
    }

    private int chromeWidth() {
        return stableChrome.width;
    }

    private int chromeHeight() {
        return stableChrome.height;
    }

    private void logGeometry(String event,
                             int width,
                             int height,
                             GraphicsConfiguration configuration,
                             Rectangle screen,
                             Dimension targetVideo,
                             Rectangle targetOuter) {
        double scaleX = configuration.getDefaultTransform().getScaleX();
        double scaleY = configuration.getDefaultTransform().getScaleY();
        log.info(
                "Playback geometry event={} source={}x{} display={} scale={}x{} "
                        + "workArea={} chrome={} portraitBox={} targetVideo={} targetOuter={}",
                event,
                width,
                height,
                configuration.getDevice().getIDstring(),
                scaleX,
                scaleY,
                screen,
                stableChrome,
                rotationModel.portraitBox(),
                targetVideo,
                targetOuter);
    }

    static boolean sameAspect(int firstWidth,
                              int firstHeight,
                              int secondWidth,
                              int secondHeight) {
        return (long) firstWidth * secondHeight == (long) secondWidth * firstHeight;
    }

    private Rectangle applyBoundsMatchingVideo(Rectangle requested,
                                               int width,
                                               int height,
                                               Rectangle usableScreen) {
        Rectangle applied = new Rectangle(requested);
        for (int attempt = 0; attempt < 2; attempt++) {
            setBounds(applied);
            validate();
            layoutTree(getRootPane());
            if (player.getWidth() <= 0 || player.getHeight() <= 0) {
                break;
            }

            int decorationWidth = Math.max(0, getWidth() - player.getWidth());
            int decorationHeight = Math.max(0, getHeight() - player.getHeight());
            int minimumContentWidth = Math.max(
                    1, getMinimumSize().width - decorationWidth);
            int minimumContentHeight = Math.max(
                    1, getMinimumSize().height - decorationHeight);
            int maximumContentWidth = Math.max(
                    1, usableScreen.width - SCREEN_GAP * 2 - decorationWidth);
            int maximumContentHeight = Math.max(
                    1, usableScreen.height - SCREEN_GAP * 2 - decorationHeight);
            Dimension content = nearestVideoSize(
                    player.getWidth(), player.getHeight(), width, height,
                    minimumContentWidth, minimumContentHeight,
                    maximumContentWidth, maximumContentHeight);
            Rectangle corrected = centeredWindowBounds(
                    applied, content.width + decorationWidth,
                    content.height + decorationHeight, usableScreen, SCREEN_GAP);
            if (corrected.equals(getBounds())) {
                applied = corrected;
                break;
            }
            applied = corrected;
        }
        setBounds(applied);
        validate();
        layoutTree(getRootPane());
        return new Rectangle(getBounds());
    }

    private void reconcileRestoredVideoBounds() {
        if (!isVisible() || sourceWidth <= 0 || sourceHeight <= 0
                || (getExtendedState() & MAXIMIZED_BOTH) != 0) {
            return;
        }
        Rectangle screen = usableScreenBounds(currentGraphicsConfiguration());
        Dimension expectedVideo = automaticVideoSize(
                rotationModel.sizeFor(sourceWidth, sourceHeight),
                sourceWidth, sourceHeight, screen,
                chromeWidth(), chromeHeight(), SCREEN_GAP);
        Rectangle applied = expectedVideo == null
                ? applyBoundsMatchingVideo(
                getBounds(), sourceWidth, sourceHeight, screen)
                : applyExactVideoBounds(
                rotatedWindowBounds(
                        currentRotationCenter(getBounds()), expectedVideo, screen,
                        chromeWidth(), chromeHeight(), SCREEN_GAP),
                expectedVideo, screen);
        normalBounds = new Rectangle(applied);
        updateAspectResizer();
    }

    static Dimension nearestVideoSize(int currentWidth,
                                      int currentHeight,
                                      int sourceWidth,
                                      int sourceHeight,
                                      int minimumWidth,
                                      int minimumHeight,
                                      int maximumWidth,
                                      int maximumHeight) {
        double aspect = (double) sourceWidth / sourceHeight;
        int projectedHeight = Math.max(1, (int) Math.round(
                (aspect * currentWidth + currentHeight) / (aspect * aspect + 1d)));
        projectedHeight = Math.max(projectedHeight, minimumHeight);
        projectedHeight = Math.max(
                projectedHeight, (int) Math.ceil(minimumWidth / aspect));
        int projectedWidth = Math.max(1, (int) Math.round(projectedHeight * aspect));
        if (projectedWidth > maximumWidth || projectedHeight > maximumHeight) {
            return fitVideoSize(
                    sourceWidth, sourceHeight, maximumWidth, maximumHeight);
        }
        return new Dimension(projectedWidth, projectedHeight);
    }

    private static void layoutTree(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                layoutTree(nested);
            }
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private Rectangle applyExactVideoBounds(Rectangle requested,
                                            Dimension videoSize,
                                            Rectangle usableScreen) {
        Rectangle target = centeredWindowBounds(
                requested,
                videoSize.width + chromeWidth(),
                videoSize.height + chromeHeight(),
                usableScreen,
                SCREEN_GAP);
        programmaticBounds = new Rectangle(target);
        setBounds(target);
        validate();
        layoutTree(getRootPane());
        Rectangle applied = new Rectangle(getBounds());
        programmaticBounds = new Rectangle(applied);
        return applied;
    }

    private Dimension currentContentSize(Rectangle outerBounds) {
        return contentSize(outerBounds, chromeWidth(), chromeHeight());
    }

    private static Dimension contentSize(
            Rectangle outerBounds, int chromeWidth, int chromeHeight) {
        return new Dimension(
                Math.max(1, outerBounds.width - chromeWidth),
                Math.max(1, outerBounds.height - chromeHeight));
    }

    private static boolean isPortrait(int width, int height) {
        return width < height;
    }

    record WindowCenterAnchor(long doubledX, long doubledY) {

        private static WindowCenterAnchor from(Rectangle bounds) {
            return new WindowCenterAnchor(
                    bounds.x * 2L + bounds.width,
                    bounds.y * 2L + bounds.height);
        }

        private int leftFor(int width) {
            return toInt(Math.floorDiv(doubledX - width, 2L));
        }

        private int topFor(int height) {
            return toInt(Math.floorDiv(doubledY - height, 2L));
        }

        private static int toInt(long value) {
            return (int) Math.max(
                    Integer.MIN_VALUE,
                    Math.min(Integer.MAX_VALUE, value));
        }
    }

    /** Keeps the renderer and transition veil on exactly the same pixel bounds. */
    static final class VideoStack extends JPanel {

        VideoStack() {
            setLayout(null);
        }

        @Override
        public void doLayout() {
            for (java.awt.Component child : getComponents()) {
                child.setBounds(0, 0, getWidth(), getHeight());
            }
        }
    }

}
