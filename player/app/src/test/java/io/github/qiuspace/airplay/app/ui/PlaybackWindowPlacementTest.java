package io.github.qiuspace.airplay.app.ui;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.JPanel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackWindowPlacementTest {

    @Test
    void initialVideoAreaExactlyMatchesThePhoneAspectWithoutSideBars() {
        Dimension video = PlaybackWindow.fitVideoSize(1179, 2556, 1884, 944);

        assertEquals(944, video.height);
        assertEquals(video.width, (int) Math.round(video.height * (1179d / 2556d)));
    }

    @Test
    void staleContentLayoutCannotBecomeAChromeMeasurement() {
        Dimension chrome = PlaybackWindow.stableChromeSize(
                new Dimension(1127, 574),
                new Dimension(505, 1132),
                new Insets(1, 1, 1, 1),
                new Insets(0, 0, 0, 0));

        assertEquals(new Dimension(2, PlaybackTitleBar.HEIGHT + 2), chrome);
    }

    @Test
    void actualMixedDpiWorkAreasKeepOneStablePhysicalPair() {
        assertStablePhysicalPair(new Dimension(2048, 1104), 1.25);
        assertStablePhysicalPair(new Dimension(1707, 1027), 1.5);
    }

    @Test
    void placesTallPhoneWindowAtRightWithEqualVerticalGaps() {
        Rectangle bounds = PlaybackWindow.sideWindowBounds(
                new Dimension(500, 1004),
                new Rectangle(0, 0, 1920, 1040),
                18);

        assertEquals(new Rectangle(1402, 18, 500, 1004), bounds);
    }

    @Test
    void compactPortraitMinimumUsesTwoHundredEightyPixelVideoWidth() {
        Dimension minimum = PlaybackWindow.minimumVideoSize(
                1179, 2556, new Rectangle(0, 0, 2560, 1440), 0, PlaybackTitleBar.HEIGHT);

        assertEquals(PlaybackWindow.MINIMUM_PORTRAIT_WIDTH, minimum.width);
        assertEquals((int) Math.round(280d * 2556d / 1179d), minimum.height);
    }

    @Test
    void compactLandscapeMinimumOnlyReservesUsableTitleBarWidth() {
        Dimension minimum = PlaybackWindow.minimumVideoSize(
                1920, 1080, new Rectangle(0, 0, 2560, 1440), 0, PlaybackTitleBar.HEIGHT);

        assertEquals(PlaybackTitleBar.MINIMUM_WINDOW_WIDTH, minimum.width);
        assertEquals((int) Math.round(
                PlaybackTitleBar.MINIMUM_WINDOW_WIDTH * 1080d / 1920d), minimum.height);
        assertTrue(PlaybackWindow.FALLBACK_MINIMUM_SIZE.width >=
                PlaybackTitleBar.MINIMUM_WINDOW_WIDTH);
    }

    @Test
    void landscapeManualSizeIsNotReducedForItsTallerPortraitPair() {
        Dimension requested = new Dimension(1300, 600);
        Dimension automatic = PlaybackWindow.automaticVideoSize(
                requested, 1300, 600,
                new Rectangle(0, 0, 1920, 1040),
                0, PlaybackTitleBar.HEIGHT, 18);

        assertEquals(requested, automatic);
    }

    @Test
    void automaticOrientationFitOnlyShrinksTheDirectionThatExceedsTheWorkArea() {
        Dimension automatic = PlaybackWindow.automaticVideoSize(
                new Dimension(640, 1400), 640, 1400,
                new Rectangle(0, 0, 1920, 1040),
                0, PlaybackTitleBar.HEIGHT, 18);

        assertEquals(960, automatic.height);
        assertEquals((int) Math.round(960d * 640d / 1400d), automatic.width);
    }

    @Test
    void orientationChangeRotatesTheVideoAreaAroundTheWindowCenter() {
        Rectangle current = new Rectangle(600, 40, 583, 1325);
        Rectangle screen = new Rectangle(0, 0, 2560, 1440);
        Rectangle rotated = PlaybackWindow.rotatedWindowBounds(
                current, new Dimension(1270, 583),
                screen, 0, 55, 18);

        assertEquals(current.getCenterX(), rotated.getCenterX(), 1);
        assertEquals(current.getCenterY(), rotated.getCenterY(), 1);
        assertEquals(new Dimension(1270, 638), rotated.getSize());
    }

    @Test
    void orientationChangeClampsWindowToTheCurrentWorkArea() {
        Rectangle rotated = PlaybackWindow.rotatedWindowBounds(
                new Rectangle(1800, 900, 460, 900),
                new Dimension(856, 460),
                new Rectangle(0, 0, 1920, 1040),
                0, 44, 18);

        assertTrue(rotated.x >= 18);
        assertTrue(rotated.y >= 18);
        assertTrue(rotated.x + rotated.width <= 1902);
        assertTrue(rotated.y + rotated.height <= 1022);
    }

    @Test
    void fixedCenterAnchorPreventsRepeatedClampingFromDrifting() {
        Rectangle initial = new Rectangle(1473, 801, 601, 301);
        Rectangle screen = new Rectangle(0, 0, 1920, 1040);
        PlaybackWindow.WindowCenterAnchor anchor =
                PlaybackWindow.windowCenterAnchor(initial);
        Rectangle portrait = PlaybackWindow.rotatedWindowBounds(
                anchor, new Dimension(401, 873),
                screen, 0, PlaybackTitleBar.HEIGHT, 18);
        Rectangle landscape = PlaybackWindow.rotatedWindowBounds(
                anchor, new Dimension(873, 401),
                screen, 0, PlaybackTitleBar.HEIGHT, 18);

        for (int index = 0; index < 20; index++) {
            assertEquals(portrait, PlaybackWindow.rotatedWindowBounds(
                    anchor, new Dimension(401, 873),
                    screen, 0, PlaybackTitleBar.HEIGHT, 18));
            assertEquals(landscape, PlaybackWindow.rotatedWindowBounds(
                    anchor, new Dimension(873, 401),
                    screen, 0, PlaybackTitleBar.HEIGHT, 18));
        }
    }

    @Test
    void rotatedOuterSizeAddsTheUnrotatedTitleBarHeight() {
        Rectangle rotated = PlaybackWindow.rotatedWindowBounds(
                new Rectangle(100, 40, 583, 1325),
                new Dimension(1270, 583),
                new Rectangle(0, 0, 2560, 1440),
                0, 55, 18);

        assertEquals(1270, rotated.width);
        assertEquals(638, rotated.height);
    }

    @Test
    void formatComparisonDetectsAspectChangesWithoutMistakingResolutionChanges() {
        assertTrue(PlaybackWindow.sameAspect(1920, 1080, 1280, 720));
        assertTrue(!PlaybackWindow.sameAspect(1080, 1920, 886, 1920));
        assertTrue(!PlaybackWindow.sameAspect(1920, 1080, 1920, 886));
    }

    @Test
    void videoAndTransitionVeilAlwaysShareTheExactContentBounds() {
        PlaybackWindow.VideoStack stack = new PlaybackWindow.VideoStack();
        JPanel video = new JPanel();
        JPanel veil = new JPanel();
        stack.add(video);
        stack.add(veil);
        stack.setSize(413, 896);

        stack.doLayout();

        Rectangle expected = new Rectangle(0, 0, 413, 896);
        assertEquals(expected, video.getBounds());
        assertEquals(expected, veil.getBounds());
    }

    @Test
    void nearestContentSizeCorrectsTheWindowWithoutChangingVideoAspect() {
        Dimension corrected = PlaybackWindow.nearestVideoSize(
                500, 900, 1179, 2556,
                280, 607, 1884, 944);

        assertEquals(corrected.width,
                (int) Math.round(corrected.height * (1179d / 2556d)));
        long correctedDistance = squaredDistance(
                corrected.width, corrected.height, 500, 900);
        int widthPreservingHeight = (int) Math.round(500d * 2556d / 1179d);
        int heightPreservingWidth = (int) Math.round(900d * 1179d / 2556d);
        assertTrue(correctedDistance <= squaredDistance(
                500, widthPreservingHeight, 500, 900));
        assertTrue(correctedDistance <= squaredDistance(
                heightPreservingWidth, 900, 500, 900));
    }

    private static long squaredDistance(int width,
                                        int height,
                                        int referenceWidth,
                                        int referenceHeight) {
        long widthDelta = width - referenceWidth;
        long heightDelta = height - referenceHeight;
        return widthDelta * widthDelta + heightDelta * heightDelta;
    }

    private static void assertStablePhysicalPair(
            Dimension workArea, double deviceScale) {
        Dimension chrome = new Dimension(2, PlaybackTitleBar.HEIGHT);
        int availableWidth = workArea.width - 36 - chrome.width;
        int availableHeight = workArea.height - 36 - chrome.height;
        PlaybackRotationModel model = new PlaybackRotationModel();
        model.initialize(PlaybackWindow.initialPortraitBox(
                2496, 1149, availableWidth, availableHeight));
        Dimension portrait = outerSize(
                model.sizeFor(662, 1440), chrome, deviceScale);
        Dimension landscape = outerSize(
                model.sizeFor(2496, 1149), chrome, deviceScale);

        for (int index = 0; index < 20; index++) {
            assertEquals(portrait, outerSize(
                    model.sizeFor(662, 1440), chrome, deviceScale));
            assertEquals(landscape, outerSize(
                    model.sizeFor(2496, 1149), chrome, deviceScale));
        }
    }

    private static Dimension outerSize(
            Dimension content, Dimension chrome, double deviceScale) {
        return new Dimension(
                (int) Math.round((content.width + chrome.width) * deviceScale),
                (int) Math.round((content.height + chrome.height) * deviceScale));
    }
}
