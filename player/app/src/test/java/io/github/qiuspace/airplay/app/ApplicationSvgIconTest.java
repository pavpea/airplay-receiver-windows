package io.github.qiuspace.airplay.app;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSvgIconTest {

    @Test
    void rendersAsTransparentBlueArtworkWithoutAWhiteTile() {
        int size = 256;
        FlatSVGIcon icon = new FlatSVGIcon("icons/app-icon.svg", size, size);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(null, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }

        assertThat(icon.hasFound()).isTrue();
        assertThat(alpha(image, 0, 0)).isZero();
        assertThat(alpha(image, size - 1, 0)).isZero();
        assertThat(alpha(image, 0, size - 1)).isZero();
        assertThat(alpha(image, size - 1, size - 1)).isZero();
        assertThat(maximumAlpha(image)).isEqualTo(0xFF);
        assertThat(countOpaqueWhitePixels(image)).isZero();
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private static long countOpaqueWhitePixels(BufferedImage image) {
        long count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                if (alpha > 240 && red > 240 && green > 240 && blue > 240) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int maximumAlpha(BufferedImage image) {
        int maximum = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                maximum = Math.max(maximum, alpha(image, x, y));
            }
        }
        return maximum;
    }
}
