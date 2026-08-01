package io.github.qiuspace.airplay.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PerceptualVolumeTest {

    @Test
    void mapsPerceivedLevelToGentlePowerCurve() {
        assertThat(PerceptualVolume.toGain(0)).isZero();
        assertThat(PerceptualVolume.toGain(0.25)).isEqualTo(0.125);
        assertThat(PerceptualVolume.toGain(0.5))
                .isCloseTo(0.353553, within(0.000001));
        assertThat(PerceptualVolume.toGain(1)).isEqualTo(1);
    }

    @Test
    void clampsLevelsOutsideTheSupportedRange() {
        assertThat(PerceptualVolume.toGain(-1)).isZero();
        assertThat(PerceptualVolume.toGain(2)).isEqualTo(1);
    }
}
