package io.github.qiuspace.airplay.app.ui;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrayControllerTest {

    private static final Rectangle POPUP = new Rectangle(100, 100, 220, 180);

    @Test
    void newlyPressedMouseButtonOutsideDismissesPopup() {
        assertTrue(TrayController.shouldDismissPopup(
                POPUP, new Point(80, 130), 0, 1));
    }

    @Test
    void clickInsidePopupAndHeldButtonDoNotDismissIt() {
        assertFalse(TrayController.shouldDismissPopup(
                POPUP, new Point(150, 130), 0, 1));
        assertFalse(TrayController.shouldDismissPopup(
                POPUP, new Point(80, 130), 1, 1));
    }

    @Test
    void oneLeftClickRestoresTheWindowWithoutWaitingForDoubleClick() {
        assertTrue(TrayController.isSingleLeftClick(MouseEvent.BUTTON1, 1));
        assertFalse(TrayController.isSingleLeftClick(MouseEvent.BUTTON1, 2));
        assertFalse(TrayController.isSingleLeftClick(MouseEvent.BUTTON3, 1));
    }
}
