package io.github.qiuspace.airplay.app.ui;

import java.awt.Dimension;

/** Keeps one immutable portrait-oriented box between explicit user resizes. */
final class PlaybackRotationModel {

    private Dimension basePortraitBox;

    void reset() {
        basePortraitBox = null;
    }

    void initialize(Dimension initialPortraitBox) {
        if (valid(initialPortraitBox) && basePortraitBox == null) {
            basePortraitBox = copy(initialPortraitBox);
        }
    }

    void rememberUserSize(int sourceWidth, int sourceHeight, Dimension contentSize) {
        if (valid(contentSize)) {
            basePortraitBox = isPortrait(sourceWidth, sourceHeight)
                    ? copy(contentSize)
                    : transpose(contentSize);
        }
    }

    Dimension sizeFor(int sourceWidth, int sourceHeight) {
        Dimension portraitBox = portraitBox();
        if (!valid(portraitBox)) {
            return null;
        }
        Dimension available = isPortrait(sourceWidth, sourceHeight)
                ? portraitBox
                : transpose(portraitBox);
        return PlaybackWindow.fitVideoSize(
                sourceWidth, sourceHeight, available.width, available.height);
    }

    Dimension portraitBox() {
        return copy(basePortraitBox);
    }

    private static boolean valid(Dimension size) {
        return size != null && size.width > 0 && size.height > 0;
    }

    private static Dimension copy(Dimension size) {
        return valid(size) ? new Dimension(size) : null;
    }

    private static Dimension transpose(Dimension size) {
        return valid(size) ? new Dimension(size.height, size.width) : null;
    }

    private static boolean isPortrait(int width, int height) {
        return width < height;
    }
}
