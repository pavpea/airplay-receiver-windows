package io.github.qiuspace.airplay.app.ui;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackRotationModelTest {

    @Test
    void swapsTheVideoContentSizeAndRestoresItWithoutDrift() {
        PlaybackRotationModel model = new PlaybackRotationModel();
        Dimension portrait = new Dimension(583, 1270);
        model.initialize(portrait);

        for (int index = 0; index < 20; index++) {
            Dimension landscape = model.sizeFor(1270, 583);
            assertEquals(new Dimension(1270, 583), landscape);

            portrait = model.sizeFor(583, 1270);
            assertEquals(new Dimension(583, 1270), portrait);
        }
    }

    @Test
    void manualResizeKeepsTheExactUserPairWithoutDisplayLimiting() {
        PlaybackRotationModel model = new PlaybackRotationModel();
        model.initialize(new Dimension(500, 1100));

        model.rememberUserSize(
                1400, 640, new Dimension(1400, 640));

        assertEquals(new Dimension(1400, 640), model.sizeFor(1400, 640));
        assertEquals(new Dimension(640, 1400), model.sizeFor(640, 1400));
        assertEquals(new Dimension(640, 1400), model.portraitBox());
    }

    @Test
    void realPhoneFormatsReturnToTheOriginalSizeWithoutCompoundShrink() {
        PlaybackRotationModel model = new PlaybackRotationModel();
        Dimension portraitBox = PlaybackWindow.initialPortraitBox(
                662, 1440, 2496, 1270);
        model.initialize(portraitBox);
        Dimension portrait = model.sizeFor(662, 1440);
        Dimension expectedLandscape = model.sizeFor(2496, 1149);

        for (int index = 0; index < 20; index++) {
            Dimension restoredPortrait = model.sizeFor(662, 1440);
            assertEquals(portrait, restoredPortrait);

            Dimension restoredLandscape = model.sizeFor(2496, 1149);
            assertEquals(expectedLandscape, restoredLandscape);
        }
    }

    @Test
    void directLandscapeUsesTheSamePortraitRotationBox() {
        Dimension directLandscape = PlaybackWindow.initialVideoSize(
                2496, 1149, 2496, 1270);
        Dimension unrestrictedLandscape = PlaybackWindow.fitVideoSize(
                2496, 1149, 2496, 1270);

        PlaybackRotationModel model = new PlaybackRotationModel();
        Dimension portraitBox = PlaybackWindow.initialPortraitBox(
                662, 1440, 2496, 1270);
        model.initialize(portraitBox);
        Dimension rotatedLandscape = model.sizeFor(2496, 1149);

        assertEquals(directLandscape.width, rotatedLandscape.width, 1);
        assertEquals(directLandscape.height, rotatedLandscape.height, 1);
        assertTrue(directLandscape.width < unrestrictedLandscape.width);
    }

    @Test
    void sameOrientationAspectUpdatesDoNotReplaceTheRotationBox() {
        PlaybackRotationModel model = new PlaybackRotationModel();
        Dimension portraitBox = PlaybackWindow.initialPortraitBox(
                662, 1440, 2496, 1270);
        model.initialize(portraitBox);
        Dimension portrait = model.sizeFor(662, 1440);
        Dimension expectedLandscape = model.sizeFor(2496, 1149);

        for (int index = 0; index < 20; index++) {
            Dimension alternateLandscape = model.sizeFor(1920, 886);
            assertTrue(alternateLandscape.width > 0);
            assertEquals(expectedLandscape, model.sizeFor(2496, 1149));
            assertEquals(portrait, model.sizeFor(662, 1440));
        }
    }

    @Test
    void manualResizeRebuildsThePairFromTheCurrentOrientation() {
        PlaybackRotationModel model = new PlaybackRotationModel();
        model.initialize(new Dimension(583, 1270));
        model.sizeFor(1270, 583);

        model.rememberUserSize(900, 400, new Dimension(900, 400));
        Dimension portrait = model.sizeFor(400, 900);

        assertEquals(new Dimension(400, 900), portrait);
    }
}
