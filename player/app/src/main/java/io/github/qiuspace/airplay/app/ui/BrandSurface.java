package io.github.qiuspace.airplay.app.ui;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** Solid, theme-aware surfaces shared by the dashboard and settings page. */
final class BrandSurface extends JPanel {

    private static final int CARD_ARC = 24;

    private final Surface surface;

    private BrandSurface(Surface surface, LayoutManager layout) {
        super(layout);
        this.surface = surface;
        setOpaque(false);
    }

    static JPanel background(LayoutManager layout) {
        return new BrandSurface(Surface.BACKGROUND, layout);
    }

    static JPanel card(boolean hero, LayoutManager layout) {
        return new BrandSurface(hero ? Surface.HERO : Surface.CARD, layout);
    }

    @Override
    protected void paintComponent(java.awt.Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        if (surface == Surface.BACKGROUND) {
            g.setColor(color("AirPlay.background", getBackground()));
            g.fillRect(0, 0, getWidth(), getHeight());
        } else {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D shape = new RoundRectangle2D.Float(
                    0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, CARD_ARC, CARD_ARC);
            String fillKey = surface == Surface.HERO
                    ? "AirPlay.heroBackground" : "AirPlay.cardBackground";
            g.setColor(color(fillKey, getBackground()));
            g.fill(shape);
            g.setColor(color("AirPlay.cardBorder", new Color(128, 128, 128, 64)));
            g.draw(shape);
        }
        g.dispose();
        super.paintComponent(graphics);
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    private enum Surface {
        BACKGROUND,
        CARD,
        HERO
    }
}
