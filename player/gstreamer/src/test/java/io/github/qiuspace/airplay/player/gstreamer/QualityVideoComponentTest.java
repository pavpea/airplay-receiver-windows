package io.github.qiuspace.airplay.player.gstreamer;

import org.junit.jupiter.api.Test;

import java.awt.RenderingHints;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QualityVideoComponentTest {

    @Test
    void exactPixelFitDoesNotBlurEdges() {
        assertEquals(RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
                QualityVideoComponent.interpolationHint(1920, 1080, 1920, 1080));
    }

    @Test
    void fractionalFitUsesHighQualityInterpolationInsteadOfBilinear() {
        assertEquals(RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                QualityVideoComponent.interpolationHint(1920, 1080, 1279, 719));
    }
}
