package io.github.qiuspace.airplay.app.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.ui.FlatSliderUI;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoundedRangeModel;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/** Responsive controls hosted in the native FlatLaf playback title bar. */
final class PlaybackTitleBar extends JMenuBar {

    static final int HEIGHT = 44;
    static final int CONTROL_SIZE = 34;
    static final int MINIMUM_WINDOW_WIDTH = 280;
    static final int CONTROL_GAP = 4;
    static final int NATIVE_GROUP_GAP = 6;
    private static final int LEFT_INSET = 8;
    private static final int CONTENT_GAP = 12;
    static final int POPUP_SLIDER_WIDTH = 24;
    private static final int POPUP_SLIDER_HEIGHT = 132;
    static final int POPUP_HORIZONTAL_PADDING = 4;
    private static final int POPUP_SHOW_DELAY_MS = 160;
    private static final int POPUP_HIDE_DELAY_MS = 260;

    private final JPanel caption = new JPanel();
    private final JLabel sessionLabel =
            new JLabel(new FlatSVGIcon("icons/app-icon.svg", 20, 20));
    private final java.awt.Component captionGap = Box.createHorizontalStrut(8);
    private final JLabel formatLabel = new JLabel("—");
    private final JToggleButton muteButton = toggleButton("icons/volume.svg", 18);
    private final JSlider popupVolume;
    private final JPopupMenu volumePopup = new JPopupMenu();
    private final JPanel popupContent = new JPanel();
    private final JToggleButton alwaysOnTopButton = toggleButton("icons/pin.svg", 18);
    private final Timer showVolumeTimer;
    private final Timer hideVolumeTimer;

    private String muteText = "Mute";
    private String unmuteText = "Unmute";
    private String volumeText = "Volume";

    PlaybackTitleBar(BoundedRangeModel volumeModel,
                     boolean muted,
                     Consumer<Boolean> muteAction,
                     Consumer<Boolean> alwaysOnTopAction,
                     Runnable volumeChanged) {
        popupVolume = new VolumeSlider(volumeModel);
        popupVolume.setOrientation(SwingConstants.VERTICAL);
        showVolumeTimer = oneShotTimer(POPUP_SHOW_DELAY_MS, this::showVolumePopup);
        hideVolumeTimer = oneShotTimer(POPUP_HIDE_DELAY_MS, this::hideVolumePopup);

        setName("playbackBar");
        setOpaque(true);
        setBackground(playbackBackground());
        setBorder(BorderFactory.createEmptyBorder(0, LEFT_INSET, 0, 0));
        setPreferredSize(new Dimension(0, HEIGHT));
        putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION,
                (java.util.function.Function<java.awt.Point, Boolean>) point -> null);

        caption.setName("playbackBar.caption");
        caption.setOpaque(false);
        caption.setLayout(new javax.swing.BoxLayout(caption, javax.swing.BoxLayout.X_AXIS));
        caption.putClientProperty(FlatClientProperties.COMPONENT_TITLE_BAR_CAPTION, true);
        sessionLabel.setName("playbackBar.session");
        sessionLabel.setAlignmentY(CENTER_ALIGNMENT);
        formatLabel.setName("playbackBar.format");
        formatLabel.setAlignmentY(CENTER_ALIGNMENT);
        formatLabel.putClientProperty("FlatLaf.styleClass", "small");
        caption.add(sessionLabel);
        caption.add(captionGap);
        caption.add(formatLabel);
        add(caption);
        add(Box.createHorizontalGlue());

        muteButton.setName("playbackBar.mute");
        muteButton.setSelected(muted);
        muteButton.addActionListener(event -> muteAction.accept(muteButton.isSelected()));
        add(muteButton);
        add(Box.createHorizontalStrut(CONTROL_GAP));

        alwaysOnTopButton.setName("playbackBar.alwaysOnTop");
        alwaysOnTopButton.addActionListener(event ->
                alwaysOnTopAction.accept(alwaysOnTopButton.isSelected()));
        add(alwaysOnTopButton);
        add(Box.createHorizontalStrut(NATIVE_GROUP_GAP));

        popupVolume.setName("playbackBar.popupVolume");
        popupVolume.setPreferredSize(
                new Dimension(POPUP_SLIDER_WIDTH, POPUP_SLIDER_HEIGHT));
        popupContent.setName("playbackBar.volumePopupContent");
        popupContent.setBorder(BorderFactory.createEmptyBorder(
                8, POPUP_HORIZONTAL_PADDING, 8, POPUP_HORIZONTAL_PADDING));
        popupContent.add(popupVolume);
        volumePopup.add(popupContent);
        installVolumeHoverBehavior(popupContent);
        applyVolumePopupTheme();

        volumeModel.addChangeListener(event -> volumeChanged.run());
        setMuted(muted);
        applyResponsiveLayout(MINIMUM_WINDOW_WIDTH);
    }

    void setTexts(String muteText, String unmuteText, String volumeText,
                  String alwaysOnTopText) {
        this.muteText = muteText;
        this.unmuteText = unmuteText;
        this.volumeText = volumeText;
        updateMuteToolTip();
        popupVolume.setToolTipText(volumeText);
        alwaysOnTopButton.setToolTipText(alwaysOnTopText);
    }

    void setMuted(boolean muted) {
        muteButton.setSelected(muted);
        muteButton.setIcon(new FlatSVGIcon(
                muted ? "icons/muted.svg" : "icons/volume.svg", 18, 18));
        updateMuteToolTip();
    }

    void setSessionToolTip(String text) {
        sessionLabel.setToolTipText(text);
    }

    void setVideoFormat(int width, int height) {
        formatLabel.setText(width + " × " + height);
        revalidate();
        repaint();
    }

    void clearVideoFormat() {
        formatLabel.setText("—");
        revalidate();
        repaint();
    }

    void refreshTheme() {
        setBackground(playbackBackground());
        applyVolumePopupTheme();
        revalidate();
        repaint();
    }

    boolean isSessionVisible() {
        return sessionLabel.isVisible();
    }

    boolean isFormatVisible() {
        return formatLabel.isVisible();
    }

    AbstractButton muteButton() {
        return muteButton;
    }

    AbstractButton alwaysOnTopButton() {
        return alwaysOnTopButton;
    }

    JSlider popupVolume() {
        return popupVolume;
    }

    JPopupMenu volumePopup() {
        return volumePopup;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setOpaque(true);
        setBackground(playbackBackground());
    }

    @Override
    public void doLayout() {
        applyResponsiveLayout(getWidth());
        super.doLayout();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics copy = graphics.create();
        copy.setColor(playbackBackground());
        copy.fillRect(0, 0, getWidth(), getHeight());
        copy.dispose();
        super.paintComponent(graphics);
    }

    @Override
    public void removeNotify() {
        showVolumeTimer.stop();
        hideVolumeTimer.stop();
        hideVolumePopup();
        super.removeNotify();
    }

    private void applyResponsiveLayout(int width) {
        int fullCaptionWidth = sessionLabel.getPreferredSize().width
                + captionGap.getPreferredSize().width + formatLabel.getPreferredSize().width;
        int controlsWidth = CONTROL_SIZE * 2 + CONTROL_GAP;
        int fixedWidth = LEFT_INSET + NATIVE_GROUP_GAP + controlsWidth;
        int remaining = width - fixedWidth;
        boolean showSession = remaining >= sessionLabel.getPreferredSize().width + CONTENT_GAP;
        boolean showFormat = showSession
                && remaining >= fullCaptionWidth + CONTENT_GAP + (CONTROL_GAP * 2);
        sessionLabel.setVisible(showSession);
        formatLabel.setVisible(showFormat);
        captionGap.setVisible(showFormat);
        caption.setVisible(showSession || showFormat);
    }

    private void showVolumePopup() {
        if (!muteButton.isShowing() || volumePopup.isVisible()) {
            return;
        }
        SwingUtilities.updateComponentTreeUI(volumePopup);
        applyVolumePopupTheme();
        Dimension preferred = volumePopup.getPreferredSize();
        int buttonX = SwingUtilities.convertPoint(muteButton, 0, 0, this).x;
        int x = popupX(getWidth(), buttonX, muteButton.getWidth(), preferred.width);
        volumePopup.show(muteButton, x, muteButton.getHeight());
    }

    private void hideVolumePopup() {
        volumePopup.setVisible(false);
    }

    private void applyVolumePopupTheme() {
        Color background = playbackBackground();
        volumePopup.setBackground(background);
        popupContent.setBackground(background);
        popupVolume.setBackground(background);
        Color track = javax.swing.UIManager.getColor("Slider.trackColor");
        Color value = javax.swing.UIManager.getColor("Slider.trackValueColor");
        Color thumb = javax.swing.UIManager.getColor("Slider.thumbColor");
        popupVolume.putClientProperty("FlatLaf.style",
                "trackWidth: 3;"
                        + (track == null ? "" : " trackColor: #" + hex(track) + ";")
                        + (value == null ? "" : " trackValueColor: #" + hex(value) + ";")
                        + (thumb == null ? "" : " thumbColor: #" + hex(thumb) + ";"));
        Color border = javax.swing.UIManager.getColor("AirPlay.titleBarBorder");
        if (border != null) {
            volumePopup.setBorder(BorderFactory.createLineBorder(border));
        }
    }

    private static String hex(Color color) {
        return "%02X%02X%02X".formatted(
                color.getRed(), color.getGreen(), color.getBlue());
    }

    static Color playbackBackground() {
        Color configured = javax.swing.UIManager.getColor(
                "AirPlay.playbackTitleBarBackground");
        if (configured != null) {
            return configured;
        }
        return FlatLaf.isLafDark()
                ? new Color(0x11, 0x18, 0x2B)
                : new Color(0xF8, 0xFA, 0xFF);
    }

    private static final class VolumeSlider extends JSlider {

        private VolumeSlider(BoundedRangeModel model) {
            super(model);
        }

        @Override
        public void updateUI() {
            setUI(new FlatSliderUI() {
                @Override
                protected Color getTrackColor() {
                    return themedColor("Slider.trackColor", super.getTrackColor());
                }

                @Override
                protected Color getTrackValueColor() {
                    return themedColor("Slider.trackValueColor", super.getTrackValueColor());
                }

                @Override
                protected Color getThumbColor() {
                    return themedColor("Slider.thumbColor", super.getThumbColor());
                }
            });
        }

        private static Color themedColor(String key, Color fallback) {
            Color color = javax.swing.UIManager.getColor(key);
            return color == null ? fallback : color;
        }
    }

    private void installVolumeHoverBehavior(JPanel popupContent) {
        MouseAdapter muteHover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hideVolumeTimer.stop();
                showVolumeTimer.restart();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                showVolumeTimer.stop();
                hideVolumeTimer.restart();
            }
        };
        muteButton.addMouseListener(muteHover);

        MouseAdapter popupHover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hideVolumeTimer.stop();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hideVolumeTimer.restart();
            }
        };
        volumePopup.addMouseListener(popupHover);
        popupContent.addMouseListener(popupHover);
        popupVolume.addMouseListener(popupHover);
    }

    private void updateMuteToolTip() {
        String actionText = muteButton.isSelected() ? unmuteText : muteText;
        muteButton.setToolTipText(actionText + " · " + volumeText);
    }

    static int popupX(int barWidth, int buttonX, int buttonWidth, int popupWidth) {
        int centered = (buttonWidth - popupWidth) / 2;
        int minimum = -buttonX;
        int maximum = barWidth - buttonX - popupWidth;
        return Math.max(minimum, Math.min(centered, maximum));
    }

    private static Timer oneShotTimer(int delay, Runnable action) {
        Timer timer = new Timer(delay, event -> action.run());
        timer.setRepeats(false);
        return timer;
    }

    private static JToggleButton toggleButton(String icon, int size) {
        JToggleButton button = new JToggleButton(new FlatSVGIcon(icon, size, size));
        styleButton(button);
        return button;
    }

    private static void styleButton(AbstractButton button) {
        Dimension size = new Dimension(CONTROL_SIZE, CONTROL_SIZE);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.putClientProperty(FlatClientProperties.SQUARE_SIZE, true);
        button.putClientProperty("FlatLaf.style",
                "arc: 8; borderWidth: 0; focusWidth: 0; margin: 0,0,0,0;"
                        + " hoverBackground: $AirPlay.titleBarHover;"
                        + " selectedBackground: fade(@accentColor,22%)");
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setFocusable(false);
    }

}
