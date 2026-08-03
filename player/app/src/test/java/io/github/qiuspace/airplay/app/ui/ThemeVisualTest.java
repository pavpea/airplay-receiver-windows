package io.github.qiuspace.airplay.app.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeVisualTest {

    private static final Color LIGHT_TITLE = new Color(0xF8FAFF);
    private static final Color DARK_TITLE = new Color(0x10172A);

    @BeforeAll
    static void registerThemeDefaults() {
        FlatLaf.registerCustomDefaultsSource("themes");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "logs.svg", "settings.svg", "settings-selected.svg",
            "volume.svg", "muted.svg", "pin.svg", "info.svg"
    })
    void titleBarIconsRemainVisibleInLightAndDarkThemes(String resource) {
        FlatLightLaf.setup();
        assertThat(contrastingPixels(resource, LIGHT_TITLE)).isGreaterThan(8);

        FlatDarkLaf.setup();
        assertThat(contrastingPixels(resource, DARK_TITLE)).isGreaterThan(8);
    }

    @Test
    void informationIconUsesTheSameToolbarPaletteInBothThemes() {
        FlatLightLaf.setup();
        assertThat(renderIcon("info.svg", 24))
                .matches(image -> containsColor(image, new Color(0x4F5D75)));

        FlatDarkLaf.setup();
        assertThat(renderIcon("info.svg", 24))
                .matches(image -> containsColor(image, new Color(0xD6DEED)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void brandSurfacesUseUniformSolidFills(boolean dark) {
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }

        JPanel background = BrandSurface.background(null);
        background.setSize(180, 100);
        BufferedImage backgroundImage = paint(background);
        assertThat(backgroundImage.getRGB(12, 12)).isEqualTo(backgroundImage.getRGB(168, 88));

        JPanel card = BrandSurface.card(true, null);
        card.setSize(180, 100);
        BufferedImage cardImage = paint(card);
        assertThat(cardImage.getRGB(45, 50)).isEqualTo(cardImage.getRGB(135, 50));
        assertThat(new Color(cardImage.getRGB(90, 50)).getRGB())
                .isEqualTo(UIManager.getColor("AirPlay.heroBackground").getRGB());
    }

    @ParameterizedTest
    @ValueSource(strings = {"settings.svg", "settings-selected.svg"})
    void settingsArtworkIsCenteredInsideItsVectorCanvas(String resource) {
        FlatLightLaf.setup();
        assertCentered(resource);
        FlatDarkLaf.setup();
        assertCentered(resource);
    }

    @Test
    void darkTitleBarAndStepBadgeUseTheProductPalette() {
        FlatDarkLaf.setup();

        assertThat(UIManager.getColor("AirPlay.titleBarBackground"))
                .isEqualTo(new Color(0x10172A));
        assertThat(UIManager.getColor("AirPlay.playbackTitleBarBackground"))
                .isEqualTo(UIManager.getColor("Panel.background"))
                .isEqualTo(new Color(0x11182B));
        assertThat(UIManager.getColor("AirPlay.titleBarBorder"))
                .isEqualTo(new Color(0x25324C));
        assertThat(UIManager.getColor("AirPlay.titleBarHover"))
                .isEqualTo(new Color(0x1B2945));
        assertThat(UIManager.getColor("AirPlay.stepBadgeBackground"))
                .isEqualTo(new Color(0x28385F));
        assertThat(UIManager.getColor("AirPlay.stepBadgeForeground"))
                .isEqualTo(new Color(0xD5DCFF));
    }

    @Test
    void playbackWindowUsesItsOwnTitleBarColorWithoutChangingTheMainTheme() {
        FlatDarkLaf.setup();
        JRootPane rootPane = new JRootPane();

        PlaybackWindow.applyPlaybackTitleBarTheme(rootPane);

        assertThat(rootPane.getClientProperty(FlatClientProperties.TITLE_BAR_BACKGROUND))
                .isEqualTo(new Color(0x11182B));
        assertThat(UIManager.getColor("AirPlay.titleBarBackground"))
                .isEqualTo(new Color(0x10172A));
        assertThat(UIManager.getColor("TitlePane.background"))
                .isEqualTo(new Color(0x10172A));
    }

    private static void assertCentered(String resource) {
        BufferedImage image = renderIcon(resource, 24);
        Rectangle bounds = opaqueBounds(image);

        int left = bounds.x;
        int right = image.getWidth() - bounds.x - bounds.width;
        int top = bounds.y;
        int bottom = image.getHeight() - bounds.y - bounds.height;
        assertThat(Math.abs(left - right))
                .as("horizontal margins left=%s right=%s bounds=%s", left, right, bounds)
                .isLessThanOrEqualTo(1);
        assertThat(Math.abs(top - bottom))
                .as("vertical margins top=%s bottom=%s bounds=%s", top, bottom, bounds)
                .isLessThanOrEqualTo(1);
    }

    private static int contrastingPixels(String resource, Color background) {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(background);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        new FlatSVGIcon("icons/" + resource, 24, 24)
                .paintIcon(new JLabel(), graphics, 0, 0);
        graphics.dispose();

        int pixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                int difference = Math.abs(color.getRed() - background.getRed())
                        + Math.abs(color.getGreen() - background.getGreen())
                        + Math.abs(color.getBlue() - background.getBlue());
                if (difference > 90) {
                    pixels++;
                }
            }
        }
        return pixels;
    }

    private static BufferedImage renderIcon(String resource, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        new FlatSVGIcon("icons/" + resource, size, size)
                .paintIcon(new JLabel(), graphics, 0, 0);
        graphics.dispose();
        return image;
    }

    private static boolean containsColor(BufferedImage image, Color expected) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color actual = new Color(image.getRGB(x, y), true);
                if (actual.getAlpha() > 0
                        && actual.getRed() == expected.getRed()
                        && actual.getGreen() == expected.getGreen()
                        && actual.getBlue() == expected.getBlue()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Rectangle opaqueBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static BufferedImage paint(JPanel panel) {
        BufferedImage image = new BufferedImage(
                panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        return image;
    }
}
