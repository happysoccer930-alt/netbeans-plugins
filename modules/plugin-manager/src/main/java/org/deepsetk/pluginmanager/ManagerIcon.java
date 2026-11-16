package org.deepsetk.pluginmanager;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** Icono de ajustes: tres deslizadores. */
final class ManagerIcon {

    private static Icon icon;

    private ManagerIcon() {
    }

    static synchronized Icon icon() {
        if (icon == null) {
            icon = new ImageIcon(image());
        }
        return icon;
    }

    static synchronized BufferedImage image() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x4E, 0xA1, 0xFF));
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(2, 4, 14, 4);
            g.drawLine(2, 8, 14, 8);
            g.drawLine(2, 12, 14, 12);
            g.fillRoundRect(9, 2, 3, 4, 1, 1);
            g.fillRoundRect(4, 6, 3, 4, 1, 1);
            g.fillRoundRect(10, 10, 3, 4, 1, 1);
        } finally {
            g.dispose();
        }
        return image;
    }
}
