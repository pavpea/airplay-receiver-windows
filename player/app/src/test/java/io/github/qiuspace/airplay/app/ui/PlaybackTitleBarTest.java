package io.github.qiuspace.airplay.app.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import org.junit.jupiter.api.Test;

import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JSlider;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;
import java.awt.Component;
import java.awt.Container;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybackTitleBarTest {

    @Test
    void minimumPortraitWidthKeepsRightAlignedMuteAndPinControls() throws Exception {
        PlaybackTitleBar bar = onEdt(() -> titleBar(new AtomicInteger()));

        onEdt(() -> {
            bar.setVideoFormat(1179, 2556);
            bar.setSize(PlaybackTitleBar.MINIMUM_WINDOW_WIDTH, PlaybackTitleBar.HEIGHT);
            bar.doLayout();
            return null;
        });

        assertThat(bar.muteButton().isVisible()).isTrue();
        assertThat(bar.alwaysOnTopButton().isVisible()).isTrue();
        assertThat(bar.muteButton().getPreferredSize().width)
                .isEqualTo(PlaybackTitleBar.CONTROL_SIZE);
        assertThat(bar.alwaysOnTopButton().getPreferredSize())
                .isEqualTo(bar.muteButton().getPreferredSize());
        assertThat(find(bar, "playbackBar.inlineVolume", JSlider.class)).isNull();
        assertThat(find(bar, "playbackBar.compactVolume", Component.class)).isNull();
    }

    @Test
    void wideWindowShowsFullCaptionWithoutAddingAnotherVolumeControl() throws Exception {
        PlaybackTitleBar bar = onEdt(() -> titleBar(new AtomicInteger()));

        onEdt(() -> {
            bar.setVideoFormat(1920, 1080);
            bar.setSize(640, PlaybackTitleBar.HEIGHT);
            bar.doLayout();
            return null;
        });

        assertThat(bar.isSessionVisible()).isTrue();
        assertThat(bar.isFormatVisible()).isTrue();
        assertThat(find(bar, "playbackBar.inlineVolume", JSlider.class)).isNull();
        assertThat(find(bar, "playbackBar.compactVolume", Component.class)).isNull();
    }

    @Test
    void verticalPopupSliderUsesSharedModelAndOneCallback() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        PlaybackTitleBar bar = onEdt(() -> titleBar(changes));

        onEdt(() -> {
            bar.popupVolume().setValue(73);
            return null;
        });

        assertThat(bar.popupVolume().getOrientation()).isEqualTo(SwingConstants.VERTICAL);
        assertThat(bar.popupVolume().getValue()).isEqualTo(73);
        assertThat(changes.get()).isEqualTo(1);
    }

    @Test
    void volumePopupIsCenteredUnlessAWindowEdgeRequiresClamping() {
        int popupWidth = PlaybackTitleBar.POPUP_SLIDER_WIDTH
                + PlaybackTitleBar.POPUP_HORIZONTAL_PADDING * 2;
        assertThat(popupWidth).isEqualTo(32);
        assertThat(PlaybackTitleBar.popupX(280, 120, 34, popupWidth)).isEqualTo(1);
        assertThat(PlaybackTitleBar.popupX(280, 0, 34, popupWidth)).isEqualTo(1);
        assertThat(PlaybackTitleBar.popupX(280, 260, 34, popupWidth)).isEqualTo(-12);
    }

    @Test
    void muteTooltipAlsoExplainsHoverVolumeControl() throws Exception {
        PlaybackTitleBar bar = onEdt(() -> titleBar(new AtomicInteger()));

        onEdt(() -> {
            bar.setTexts("静音", "取消静音", "音量", "窗口置顶");
            return null;
        });

        assertThat(bar.muteButton().getToolTipText()).isEqualTo("静音 · 音量");
        onEdt(() -> {
            bar.setMuted(true);
            return null;
        });
        assertThat(bar.muteButton().getToolTipText()).isEqualTo("取消静音 · 音量");
    }

    @Test
    void darkVolumePopupUsesTitleBarAndVisibleTrackColorsAcrossItsPadding() throws Exception {
        FlatLaf.registerCustomDefaultsSource("themes");
        FlatDarkLaf.setup();
        PlaybackTitleBar bar = onEdt(() -> titleBar(new AtomicInteger()));

        onEdt(() -> {
            bar.refreshTheme();
            return null;
        });

        JPanel content = find(
                bar.volumePopup(), "playbackBar.volumePopupContent", JPanel.class);
        assertThat(content).isNotNull();
        assertThat(content.getBackground())
                .isEqualTo(UIManager.getColor("AirPlay.playbackTitleBarBackground"));
        assertThat(bar.popupVolume().getBackground())
                .isEqualTo(UIManager.getColor("AirPlay.playbackTitleBarBackground"));
        assertThat(UIManager.getColor("Slider.trackColor"))
                .isNotEqualTo(UIManager.getColor("AirPlay.playbackTitleBarBackground"));
        assertThat(bar.popupVolume().getClientProperty("FlatLaf.style").toString())
                .contains("trackColor", "trackValueColor", "thumbColor");
        assertThat(bar.volumePopup().getBorder()).isInstanceOf(LineBorder.class);
        assertThat(((LineBorder) bar.volumePopup().getBorder()).getLineColor())
                .isEqualTo(UIManager.getColor("AirPlay.titleBarBorder"));
    }

    @Test
    void darkVerticalSliderPaintsBothInactiveAndActiveTrackHalves() throws Exception {
        FlatLaf.registerCustomDefaultsSource("themes");
        FlatDarkLaf.setup();
        PlaybackTitleBar bar = onEdt(() -> titleBar(new AtomicInteger()));
        BufferedImage image = onEdt(() -> {
            JSlider slider = bar.popupVolume();
            slider.setSize(slider.getPreferredSize());
            slider.doLayout();
            BufferedImage rendered = new BufferedImage(
                    slider.getWidth(), slider.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = rendered.createGraphics();
            slider.paint(graphics);
            graphics.dispose();
            return rendered;
        });

        assertThat(containsColorNear(
                image, UIManager.getColor("Slider.trackColor"), 8, 12, 16, 48)).isTrue();
        assertThat(containsColorNear(
                image, UIManager.getColor("Slider.trackValueColor"), 8, 84, 16, 120)).isTrue();
    }

    private static PlaybackTitleBar titleBar(AtomicInteger changes) {
        return new PlaybackTitleBar(
                new DefaultBoundedRangeModel(50, 0, 0, 100),
                false,
                ignored -> {
                },
                ignored -> {
                },
                changes::incrementAndGet);
    }

    private static boolean containsColorNear(BufferedImage image,
                                             Color expected,
                                             int left,
                                             int top,
                                             int right,
                                             int bottom) {
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                Color actual = new Color(image.getRGB(x, y), true);
                int difference = Math.abs(actual.getRed() - expected.getRed())
                        + Math.abs(actual.getGreen() - expected.getGreen())
                        + Math.abs(actual.getBlue() - expected.getBlue());
                if (actual.getAlpha() > 200 && difference < 24) {
                    return true;
                }
            }
        }
        return false;
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
