package io.github.qiuspace.airplay.app.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import io.github.qiuspace.airplay.app.AppVersion;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;

/** Configures the fixed, full-window-content chrome used by the receiver console. */
final class MainWindowChrome {

    static final int TITLE_BAR_HEIGHT = 44;
    static final int TITLE_ICON_SIZE = 24;
    static final int ACTION_BUTTON_SIZE = 44;
    static final int ACTION_SEPARATOR_WIDTH = 16;
    static final Dimension WINDOW_SIZE = new Dimension(1040, 720);
    private static final int WORK_AREA_MARGIN = 16;

    private MainWindowChrome() {
    }

    static void configure(JFrame frame) {
        configureRootPane(frame.getRootPane());
        frame.setResizable(false);
        Dimension fittedSize = fitToWorkArea(workArea(frame.getGraphicsConfiguration()));
        frame.setMinimumSize(new Dimension(fittedSize));
        frame.setSize(new Dimension(fittedSize));
    }

    static void configureRootPane(JRootPane rootPane) {
        rootPane.putClientProperty(FlatClientProperties.USE_WINDOW_DECORATIONS, true);
        rootPane.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, false);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICONIFFY, true);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_MAXIMIZE, false);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_CLOSE, true);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT, TITLE_BAR_HEIGHT);
    }

    static JPanel createWindowsButtonsPlaceholder() {
        return createWindowsButtonsPlaceholder("appBar.nativeButtons");
    }

    static JPanel createWindowsButtonsPlaceholder(String name) {
        JPanel placeholder = new JPanel();
        placeholder.setName(name);
        placeholder.setOpaque(false);
        placeholder.putClientProperty(
                FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "win");
        return placeholder;
    }

    static JPanel createAppBar(AbstractButton logsButton, AbstractButton settingsButton) {
        JPanel appBar = new AppBarPanel();
        appBar.setName("appBar");
        appBar.setLayout(new BorderLayout());
        appBar.setPreferredSize(new Dimension(0, TITLE_BAR_HEIGHT));
        appBar.putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION,
                (java.util.function.Function<java.awt.Point, Boolean>) point -> null);

        JLabel icon = new JLabel(new FlatSVGIcon(
                "icons/app-icon.svg", TITLE_ICON_SIZE, TITLE_ICON_SIZE));
        icon.setName("appBar.icon");
        icon.setAlignmentY(JLabel.CENTER_ALIGNMENT);

        JLabel product = new JLabel("AirPlay Receiver", SwingConstants.LEFT);
        product.setName("appBar.product");
        product.setFont(product.getFont().deriveFont(Font.BOLD, 16f));
        product.setAlignmentY(JLabel.CENTER_ALIGNMENT);
        product.setMinimumSize(product.getPreferredSize());
        product.setMaximumSize(product.getPreferredSize());

        JLabel version = new JLabel(AppVersion.display());
        version.setName("appBar.version");
        version.putClientProperty("FlatLaf.styleClass", "small");
        version.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");
        version.setAlignmentY(JLabel.CENTER_ALIGNMENT);

        JPanel identity = new JPanel();
        identity.setName("appBar.identity");
        identity.setLayout(new BoxLayout(identity, BoxLayout.X_AXIS));
        identity.setOpaque(false);
        identity.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        identity.putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, true);
        identity.add(icon);
        identity.add(Box.createHorizontalStrut(9));
        identity.add(product);
        identity.add(Box.createHorizontalStrut(8));
        identity.add(version);
        appBar.add(identity, BorderLayout.WEST);

        logsButton.setName("appBar.logs");
        styleActionButton(logsButton);
        settingsButton.setName("appBar.settings");
        styleActionButton(settingsButton);
        JPanel actions = new JPanel();
        actions.setName("appBar.actions");
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.setOpaque(false);
        actions.add(logsButton);
        actions.add(settingsButton);
        actions.add(new ActionSeparator());
        actions.add(createWindowsButtonsPlaceholder());
        appBar.add(actions, BorderLayout.EAST);

        appBar.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                updateVersionVisibility(appBar, identity, actions, version);
            }
        });
        return appBar;
    }

    private static void styleActionButton(AbstractButton button) {
        Dimension size = new Dimension(ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE);
        button.setAlignmentY(AbstractButton.CENTER_ALIGNMENT);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.putClientProperty(FlatClientProperties.SQUARE_SIZE, true);
    }

    private static void updateVersionVisibility(JPanel appBar,
                                                JPanel identity,
                                                JPanel actions,
                                                JLabel version) {
        if (appBar.getWidth() <= 0) {
            return;
        }
        boolean wasVisible = version.isVisible();
        version.setVisible(true);
        int requiredWidth = identity.getPreferredSize().width + actions.getPreferredSize().width + 24;
        version.setVisible(appBar.getWidth() >= requiredWidth);
        if (wasVisible != version.isVisible()) {
            identity.revalidate();
            identity.repaint();
        }
    }

    static Dimension fitToWorkArea(Rectangle workArea) {
        int availableWidth = Math.max(1, workArea.width - (WORK_AREA_MARGIN * 2));
        int availableHeight = Math.max(1, workArea.height - (WORK_AREA_MARGIN * 2));
        return new Dimension(
                Math.min(WINDOW_SIZE.width, availableWidth),
                Math.min(WINDOW_SIZE.height, availableHeight));
    }

    private static Rectangle workArea(GraphicsConfiguration configuration) {
        if (configuration == null) {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        }
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom);
    }

    private static final class AppBarPanel extends JPanel {

        AppBarPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(color("AirPlay.titleBarBackground", getBackground()));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(color("AirPlay.titleBarBorder", new Color(0, 0, 0, 32)));
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ActionSeparator extends JComponent {

        ActionSeparator() {
            setName("appBar.actionSeparator");
            Dimension size = new Dimension(ACTION_SEPARATOR_WIDTH, TITLE_BAR_HEIGHT);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(color("Separator.foreground", new Color(128, 128, 128, 96)));
            int x = getWidth() / 2;
            int y = (getHeight() - 18) / 2;
            g.drawLine(x, y, x, y + 17);
            g.dispose();
        }
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }
}
