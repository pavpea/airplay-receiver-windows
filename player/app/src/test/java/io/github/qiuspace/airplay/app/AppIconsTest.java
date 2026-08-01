package io.github.qiuspace.airplay.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

class AppIconsTest {

    @ParameterizedTest
    @ValueSource(ints = {16, 20, 24, 32, 40, 48, 64, 92, 128, 256})
    void loadsEveryWhiteTileSystemIconAtItsNativeSize(int size) {
        BufferedImage image = AppIcons.image(size);

        assertThat(image.getWidth()).isEqualTo(size);
        assertThat(image.getHeight()).isEqualTo(size);
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat(image.getRGB(0, 0) >>> 24).isLessThanOrEqualTo(4);
        assertThat(maximumAlpha(image)).isEqualTo(0xFF);
        assertThat(countOpaqueWhitePixels(image)).isGreaterThan(size * size / 3L);
        assertThat(countOpaqueBluePixels(image)).isGreaterThan(0);
    }

    @Test
    void trayIconContainsNativeScaleVariants() {
        Image trayIcon = AppIcons.trayIcon();

        assertThat(trayIcon).isInstanceOf(MultiResolutionImage.class);
        assertThat(((MultiResolutionImage) trayIcon).getResolutionVariants())
                .extracting(image -> image.getWidth(null))
                .containsExactly(16, 20, 24, 32, 40, 48, 64);
    }

    @Test
    void legacyTrayResourceMatchesTheNative32PixelApplicationIcon() throws IOException {
        try (InputStream input = AppIcons.class.getResourceAsStream("/menu/tray_icon.png")) {
            assertThat(input).isNotNull();
            BufferedImage trayResource = ImageIO.read(input);
            BufferedImage applicationIcon = AppIcons.image(32);

            assertThat(trayResource.getWidth()).isEqualTo(32);
            assertThat(trayResource.getHeight()).isEqualTo(32);
            assertThat(trayResource.getRGB(0, 0, 32, 32, null, 0, 32))
                    .containsExactly(applicationIcon.getRGB(0, 0, 32, 32, null, 0, 32));
        }
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

    private static long countOpaqueBluePixels(BufferedImage image) {
        long count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                if (alpha > 128 && blue > green && green > red) {
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
                maximum = Math.max(maximum, image.getRGB(x, y) >>> 24);
            }
        }
        return maximum;
    }
}
