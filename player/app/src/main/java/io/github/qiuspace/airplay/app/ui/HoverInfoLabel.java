package io.github.qiuspace.airplay.app.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

/** Information icon whose explanation is visible for exactly as long as it is hovered. */
class HoverInfoLabel extends JLabel {

    private static final int POPUP_GAP = 4;

    private final int iconSize;
    private final PopupFactory popupFactory;
    private String infoText;
    private Popup popup;
    private boolean hovered;

    HoverInfoLabel(int iconSize) {
        this(iconSize, PopupFactory.getSharedInstance());
    }

    HoverInfoLabel(int iconSize, PopupFactory popupFactory) {
        if (iconSize <= 0) {
            throw new IllegalArgumentException("iconSize must be positive");
        }
        this.iconSize = iconSize;
        this.popupFactory = Objects.requireNonNull(popupFactory, "popupFactory");
        refreshIcon();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                showInfoPopup();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                hideInfoPopup();
            }
        });
        addHierarchyListener(event -> {
            long flags = event.getChangeFlags();
            if ((flags & (HierarchyEvent.SHOWING_CHANGED
                    | HierarchyEvent.DISPLAYABILITY_CHANGED)) != 0 && !isShowing()) {
                hideInfoPopup();
            }
        });
    }

    void setInfoText(String text) {
        String normalized = text == null || text.isBlank() ? null : text;
        if (Objects.equals(infoText, normalized)) {
            return;
        }
        infoText = normalized;
        getAccessibleContext().setAccessibleDescription(normalized);
        hideInfoPopup();
        if (hovered && normalized != null) {
            showInfoPopup();
        }
    }

    String infoText() {
        return infoText;
    }

    void refreshTheme() {
        refreshIcon();
        if (popup != null) {
            hideInfoPopup();
            if (hovered) {
                showInfoPopup();
            }
        }
        repaint();
    }

    boolean isInfoPopupVisible() {
        return popup != null;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (iconSize > 0) {
            refreshIcon();
        }
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            hideInfoPopup();
        }
        super.setVisible(visible);
    }

    @Override
    public void removeNotify() {
        hovered = false;
        hideInfoPopup();
        super.removeNotify();
    }

    private void refreshIcon() {
        setIcon(new FlatSVGIcon("icons/info.svg", iconSize, iconSize));
    }

    private void showInfoPopup() {
        if (popup != null || infoText == null || !isShowing()) {
            return;
        }

        JToolTip tip = createToolTip();
        tip.setTipText(infoText);
        SwingUtilities.updateComponentTreeUI(tip);
        Dimension popupSize = tip.getPreferredSize();
        Point screenLocation;
        try {
            screenLocation = getLocationOnScreen();
        } catch (IllegalStateException exception) {
            return;
        }
        Rectangle anchor = new Rectangle(
                screenLocation.x, screenLocation.y, getWidth(), getHeight());
        Point location = popupLocation(anchor, popupSize, workingArea());

        try {
            popup = popupFactory.getPopup(this, tip, location.x, location.y);
            popup.show();
        } catch (IllegalArgumentException exception) {
            popup = null;
        }
    }

    private void hideInfoPopup() {
        Popup current = popup;
        popup = null;
        if (current != null) {
            current.hide();
        }
    }

    Rectangle workingArea() {
        GraphicsConfiguration configuration = getGraphicsConfiguration();
        if (configuration == null) {
            Point location = getLocationOnScreen();
            return new Rectangle(location.x, location.y, 1, 1);
        }
        Rectangle bounds = configuration.getBounds();
        try {
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
            return new Rectangle(
                    bounds.x + insets.left,
                    bounds.y + insets.top,
                    Math.max(1, bounds.width - insets.left - insets.right),
                    Math.max(1, bounds.height - insets.top - insets.bottom));
        } catch (HeadlessException exception) {
            return new Rectangle(bounds);
        }
    }

    static Point popupLocation(Rectangle anchor, Dimension popupSize, Rectangle workArea) {
        int maximumX = workArea.x + Math.max(0, workArea.width - popupSize.width);
        int centeredX = anchor.x + (anchor.width - popupSize.width) / 2;
        int x = clamp(centeredX, workArea.x, maximumX);

        int below = anchor.y + anchor.height + POPUP_GAP;
        int above = anchor.y - popupSize.height - POPUP_GAP;
        int desiredY = below + popupSize.height <= workArea.y + workArea.height
                ? below
                : above;
        int maximumY = workArea.y + Math.max(0, workArea.height - popupSize.height);
        int y = clamp(desiredY, workArea.y, maximumY);
        return new Point(x, y);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
