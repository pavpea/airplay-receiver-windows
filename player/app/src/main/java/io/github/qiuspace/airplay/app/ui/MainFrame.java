package io.github.qiuspace.airplay.app.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.FlatClientProperties;
import io.github.qiuspace.airplay.app.AppIcons;
import io.github.qiuspace.airplay.app.AppPaths;
import io.github.qiuspace.airplay.app.ReceiverController;
import io.github.qiuspace.airplay.app.ReceiverView;
import io.github.qiuspace.airplay.app.i18n.I18n;
import io.github.qiuspace.airplay.app.platform.NetworkInfo;
import io.github.qiuspace.airplay.app.platform.ProcessExit;
import io.github.qiuspace.airplay.app.platform.WindowsIntegration;
import io.github.qiuspace.airplay.app.settings.AppSettings;
import io.github.qiuspace.airplay.server.ServerState;
import io.github.qiuspace.airplay.server.SessionInfo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainFrame extends JFrame implements ReceiverView {

    private static final String DASHBOARD_PAGE = "dashboard";
    private static final String SETTINGS_PAGE = "settings";

    private final ReceiverController controller;
    private final I18n i18n;
    private final PlaybackWindow playbackWindow;
    private final SettingsPanel settingsPanel;
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final JLabel waitingTitle = heading(22, Font.BOLD);
    private final JTextArea waitingSubtitle = textArea(15f);
    private final JLabel receiverCaption = new JLabel();
    private final JTextArea receiverName = textArea(28f, Font.BOLD);
    private final JLabel instructionsTitle = heading(18, Font.BOLD);
    private final JLabel[] instructionSteps = {bodyLabel(14f), bodyLabel(14f), bodyLabel(14f)};
    private final JLabel deviceInfoTitle = heading(18, Font.BOLD);
    private final JLabel networkTitle = new JLabel();
    private final JLabel capabilityTitle = new JLabel();
    private final JLabel trustedNetworkLabel = new JLabel();
    private final JTextArea networkValue = textArea(14f);
    private final JTextArea resolutionValue = textArea(14f);
    private final JPanel errorBanner = new JPanel(new BorderLayout(12, 0));
    private final JLabel errorLabel = new JLabel();
    private final JToggleButton settingsButton = iconToggleButton("icons/settings.svg", 18);
    private final JButton logsButton = iconButton("icons/logs.svg", 17);
    private final JButton firewallButton = new JButton();
    private final AtomicBoolean exiting = new AtomicBoolean();
    private TrayController tray;
    private AppSettings settings;
    private boolean playing;
    private boolean settingsPageVisible;
    private ServerState serverState = ServerState.STOPPED;

    public MainFrame(ReceiverController controller, I18n i18n) {
        super("AirPlay Receiver");
        this.controller = controller;
        this.i18n = i18n;
        this.settings = controller.settings();
        this.playbackWindow = new PlaybackWindow(controller, i18n);
        this.settingsPanel = new SettingsPanel(i18n);
        setIconImages(AppIcons.windowIcons());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        MainWindowChrome.configure(this);
        setLocationRelativeTo(null);
        buildUi();
        installWindowBehavior();
        refreshTexts();
        tray = new TrayController(this, i18n);
    }

    @Override
    public void onServerState(ServerState state) {
        serverState = state;
        refreshHero();
        tray.update(state, playing);
        if (state == ServerState.READY) {
            hideError();
        }
    }

    @Override
    public void onSessionStarted(SessionInfo session) {
        playing = true;
        refreshHero();
        tray.update(serverState, true);
        playbackWindow.showSession(session, settings);
    }

    @Override
    public void onSessionStopped() {
        playing = false;
        refreshHero();
        tray.update(serverState, false);
        playbackWindow.endSession();
    }

    @Override
    public void onVideoFormat(int width, int height) {
        playbackWindow.updateVideoFormat(width, height);
    }

    @Override
    public void onVideoFrameReady(int width, int height) {
        playbackWindow.videoFrameReady(width, height);
    }

    @Override
    public void onError(String message, Throwable error) {
        String displayMessage;
        if (message != null && message.contains("No active multicast-capable IPv4 network")) {
            displayMessage = i18n.text("error.noNetwork");
        } else if (message == null || message.isBlank()) {
            displayMessage = i18n.text("error.generic");
        } else if (error == null) {
            displayMessage = i18n.text("error.mediaPlayback", message);
        } else {
            displayMessage = message;
        }
        errorLabel.setText(displayMessage);
        errorBanner.setVisible(true);
        revalidate();
        tray.showError(displayMessage);
    }

    @Override
    public void onSettingsChanged(AppSettings updatedSettings) {
        boolean languageChanged = this.settings.language() != updatedSettings.language();
        this.settings = updatedSettings;
        i18n.setLanguage(updatedSettings.language());
        refreshTexts();
        playbackWindow.refreshTexts();
        if (languageChanged) {
            tray.close();
            tray = new TrayController(this, i18n);
        }
        tray.update(serverState, playing);
    }

    public void restoreAndShow() {
        if (!isVisible()) {
            setVisible(true);
        }
        setExtendedState(getExtendedState() & ~ICONIFIED);
        toFront();
        requestFocus();
    }

    public void exitApplication() {
        if (!exiting.compareAndSet(false, true)) {
            return;
        }
        // Arm this before touching Swing, the tray or the native playback window.
        // Any of those can block while a mirroring session is being torn down.
        ProcessExit.armWatchdog();
        setVisible(false);
        playbackWindow.closeWindow();
        tray.close();
        dispose();

        Thread.ofPlatform().name("airplay-shutdown").start(() -> {
            try {
                controller.close();
            } finally {
                System.exit(0);
            }
        });
    }

    private void buildUi() {
        JPanel root = BrandSurface.background(new BorderLayout());
        root.add(buildAppBar(), BorderLayout.NORTH);

        JPanel workspace = new JPanel(new BorderLayout(0, 16));
        workspace.setOpaque(false);
        workspace.setBorder(BorderFactory.createEmptyBorder(22, 28, 28, 28));
        workspace.add(buildErrorBanner(), BorderLayout.NORTH);
        pages.setOpaque(false);
        pages.add(buildDashboard(), DASHBOARD_PAGE);
        pages.add(settingsPanel, SETTINGS_PAGE);
        workspace.add(pages, BorderLayout.CENTER);
        root.add(workspace, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel buildAppBar() {
        logsButton.addActionListener(event -> WindowsIntegration.openDirectory(AppPaths.logsDirectory()));
        settingsButton.addActionListener(event -> {
            if (settingsPageVisible) {
                showDashboardPage();
            } else {
                showSettingsPage();
            }
        });
        return MainWindowChrome.createAppBar(logsButton, settingsButton);
    }

    private JPanel buildErrorBanner() {
        errorBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 83, 83, 100)),
                BorderFactory.createEmptyBorder(10, 14, 10, 8)));
        errorBanner.putClientProperty("FlatLaf.style", "arc: 16; background: fade(#d84d4d,12%)");
        errorBanner.add(errorLabel, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);
        firewallButton.addActionListener(event -> WindowsIntegration.openFirewallSettings());
        JButton dismiss = new JButton("×");
        dismiss.putClientProperty("FlatLaf.style", "borderWidth: 0; focusWidth: 0");
        dismiss.addActionListener(event -> hideError());
        actions.add(firewallButton);
        actions.add(dismiss);
        errorBanner.add(actions, BorderLayout.EAST);
        errorBanner.setVisible(false);
        return errorBanner;
    }

    private JPanel buildDashboard() {
        JPanel dashboard = new JPanel(new GridBagLayout());
        dashboard.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 18, 0);
        dashboard.add(buildHero(), constraints);

        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 9);
        dashboard.add(buildInstructions(), constraints);
        constraints.gridx = 1;
        constraints.insets = new Insets(0, 9, 0, 0);
        dashboard.add(buildDeviceInfo(), constraints);
        return dashboard;
    }

    private JPanel buildHero() {
        JPanel hero = cardPanel(new BorderLayout());
        hero.setBorder(BorderFactory.createEmptyBorder(28, 34, 28, 34));

        JPanel copy = transparentColumn();
        waitingTitle.setAlignmentX(LEFT_ALIGNMENT);
        copy.add(waitingTitle);
        copy.add(Box.createVerticalStrut(5));
        waitingSubtitle.setAlignmentX(LEFT_ALIGNMENT);
        copy.add(waitingSubtitle);
        copy.add(Box.createVerticalStrut(17));
        receiverCaption.putClientProperty("FlatLaf.styleClass", "small");
        receiverCaption.setAlignmentX(LEFT_ALIGNMENT);
        copy.add(receiverCaption);
        copy.add(Box.createVerticalStrut(2));
        receiverName.setRows(2);
        receiverName.setAlignmentX(LEFT_ALIGNMENT);
        copy.add(receiverName);
        hero.add(copy, BorderLayout.CENTER);
        return hero;
    }

    private JPanel buildInstructions() {
        JPanel panel = cardPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, 18, 0);
        panel.add(instructionsTitle, constraints);

        for (int index = 0; index < instructionSteps.length; index++) {
            constraints.gridy = index + 1;
            constraints.insets = new Insets(0, 0,
                    index < instructionSteps.length - 1 ? 15 : 0, 0);
            panel.add(instructionRow(index + 1, instructionSteps[index]), constraints);
        }

        constraints.gridy = instructionSteps.length + 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 0);
        panel.add(Box.createGlue(), constraints);
        return panel;
    }

    static JPanel instructionRow(int number, JLabel text) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setName("instructions.step." + number);
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);

        JLabel badge = new JLabel(String.valueOf(number), numberIcon(), SwingConstants.CENTER);
        badge.setName("instructions.badge." + number);
        badge.setHorizontalTextPosition(SwingConstants.CENTER);
        badge.putClientProperty("FlatLaf.style",
                "foreground: $AirPlay.stepBadgeForeground");
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 13f));

        text.setName("instructions.text." + number);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(0, 0, 0, 13);
        row.add(badge, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 0, 0);
        row.add(text, constraints);
        return row;
    }

    private JPanel buildDeviceInfo() {
        JPanel panel = cardPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        deviceInfoTitle.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(deviceInfoTitle);
        panel.add(Box.createVerticalStrut(18));
        networkTitle.putClientProperty("FlatLaf.styleClass", "small");
        networkTitle.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(networkTitle);
        panel.add(Box.createVerticalStrut(5));
        networkValue.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(networkValue);
        panel.add(Box.createVerticalStrut(18));
        capabilityTitle.putClientProperty("FlatLaf.styleClass", "small");
        capabilityTitle.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(capabilityTitle);
        panel.add(Box.createVerticalStrut(5));
        resolutionValue.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(resolutionValue);
        panel.add(Box.createVerticalGlue());
        trustedNetworkLabel.setIcon(new FlatSVGIcon("icons/shield.svg", 18, 18));
        trustedNetworkLabel.setIconTextGap(9);
        trustedNetworkLabel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(trustedNetworkLabel);
        return panel;
    }

    private void refreshTexts() {
        refreshHero();
        instructionsTitle.setText(i18n.text("home.howTo"));
        for (int index = 0; index < instructionSteps.length; index++) {
            instructionSteps[index].setText(i18n.text("home.step" + (index + 1)));
        }
        deviceInfoTitle.setText(i18n.text("home.deviceInfo"));
        networkTitle.setText(i18n.text("home.network"));
        capabilityTitle.setText(i18n.text("home.capability"));
        trustedNetworkLabel.setText(i18n.text("home.trustedNetwork"));
        List<String> addresses = NetworkInfo.localAddresses();
        String addressText = addresses.isEmpty() ? i18n.text("home.noNetwork") : String.join("  ·  ", addresses);
        networkValue.setText(addressText);
        networkValue.setToolTipText(addressText);
        resolutionValue.setText(displayDescription(settings));
        logsButton.setToolTipText(i18n.text("action.logs"));
        settingsButton.setToolTipText(i18n.text("settings.title"));
        settingsPanel.refreshTexts();
        firewallButton.setText(i18n.text("action.firewall"));
        receiverCaption.setText(i18n.text("home.receiverName"));
    }

    private void refreshHero() {
        receiverName.setText(settings.receiverName());
        receiverName.setToolTipText(settings.receiverName());
        waitingTitle.setText(StatusText.resolve(i18n, serverState, playing));
        if (playing) {
            waitingSubtitle.setText(i18n.text("home.castingSubtitle"));
            return;
        }
        waitingSubtitle.setText(switch (serverState) {
            case STARTING -> i18n.text("home.startingSubtitle");
            case READY -> i18n.text("home.readySubtitle", settings.receiverName());
            case STOPPING -> i18n.text("home.stoppingSubtitle");
            case STOPPED -> i18n.text("home.stoppedSubtitle");
            case FAILED -> i18n.text("home.failedSubtitle");
        });
    }

    private String displayDescription(AppSettings appSettings) {
        return switch (appSettings.displayMode()) {
            case PRIMARY_DISPLAY -> {
                java.awt.DisplayMode display = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDisplayMode();
                yield i18n.text("display.primary", String.valueOf(display.getWidth()),
                        String.valueOf(display.getHeight()), String.valueOf(appSettings.maxFps()));
            }
            case HD_720 -> "1280 × 720  ·  " + appSettings.maxFps() + "fps";
            case FULL_HD_1080 -> "1920 × 1080  ·  " + appSettings.maxFps() + "fps";
            case CUSTOM -> appSettings.customWidth() + " × " + appSettings.customHeight()
                    + "  ·  " + appSettings.maxFps() + "fps";
        };
    }

    private void installWindowBehavior() {
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                if (settingsPageVisible) {
                    settingsPanel.flushAutoSave();
                }
                if (settings.closeToTray() && tray.available()) {
                    setVisible(false);
                } else {
                    exitApplication();
                }
            }
        });
    }

    private void hideError() {
        errorBanner.setVisible(false);
        revalidate();
    }

    private void showSettingsPage() {
        settingsPageVisible = true;
        updateSettingsButton();
        settingsPanel.open(settings, controller::updateSettings);
        pageLayout.show(pages, SETTINGS_PAGE);
        pages.revalidate();
        pages.repaint();
    }

    private void showDashboardPage() {
        if (settingsPageVisible) {
            settingsPanel.flushAutoSave();
        }
        settingsPageVisible = false;
        updateSettingsButton();
        pageLayout.show(pages, DASHBOARD_PAGE);
        pages.revalidate();
        pages.repaint();
    }

    private static JPanel transparentColumn() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private static JPanel cardPanel() {
        return BrandSurface.card(false, null);
    }

    private static JPanel cardPanel(LayoutManager layout) {
        return BrandSurface.card(layout instanceof BorderLayout, layout);
    }

    private static JLabel heading(float size, int style) {
        JLabel label = new JLabel();
        label.setFont(label.getFont().deriveFont(style, size));
        return label;
    }

    private static JLabel bodyLabel(float size) {
        JLabel label = new JLabel();
        label.setFont(label.getFont().deriveFont(Font.PLAIN, size));
        return label;
    }

    private static JTextArea textArea(float size) {
        return textArea(size, Font.PLAIN);
    }

    private static JTextArea textArea(float size, int style) {
        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBorder(null);
        text.setFont(text.getFont().deriveFont(style, size));
        return text;
    }

    private static JButton iconButton(String icon, int size) {
        JButton button = new JButton(new FlatSVGIcon(icon, size, size));
        styleIconButton(button);
        return button;
    }

    private static JToggleButton iconToggleButton(String icon, int size) {
        JToggleButton button = new JToggleButton(new FlatSVGIcon(icon, size, size));
        styleIconButton(button);
        button.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 0; focusWidth: 0; margin: 0,0,0,0;"
                        + " selectedBackground: fade(@accentColor,22%)");
        return button;
    }

    private static void styleIconButton(javax.swing.AbstractButton button) {
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFocusable(false);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.putClientProperty("FlatLaf.style",
                "arc: 0; borderWidth: 0; focusWidth: 0; margin: 0,0,0,0;"
                        + " hoverBackground: $AirPlay.titleBarHover");
    }

    private void updateSettingsButton() {
        settingsButton.setSelected(settingsPageVisible);
        settingsButton.setIcon(new FlatSVGIcon(
                settingsPageVisible ? "icons/settings-selected.svg" : "icons/settings.svg",
                18, 18));
    }

    private static javax.swing.Icon numberIcon() {
        return new javax.swing.Icon() {
            @Override
            public void paintIcon(java.awt.Component component, java.awt.Graphics graphics, int x, int y) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                Color badgeBackground = javax.swing.UIManager.getColor(
                        "AirPlay.stepBadgeBackground");
                g.setColor(badgeBackground != null
                        ? badgeBackground
                        : new Color(83, 109, 254));
                g.fillOval(x, y, 28, 28);
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return 28;
            }

            @Override
            public int getIconHeight() {
                return 28;
            }
        };
    }

}
