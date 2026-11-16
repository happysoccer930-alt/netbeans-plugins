package org.deepsetk.crud;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** Icono: una tabla/BD con un signo mas. */
final class CrudIcon {

    private static Icon icon;

    private CrudIcon() {
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
            g.setColor(new Color(0x22, 0xD3, 0xEE));
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRoundRect(1, 2, 11, 10, 2, 2);
            g.drawLine(1, 5, 12, 5);
            g.drawLine(5, 5, 5, 12);
            g.drawLine(8, 5, 8, 12);
            g.drawLine(1, 8, 12, 8);
            Path2D plus = new Path2D.Double();
            plus.moveTo(12.5, 8.5);
            plus.lineTo(12.5, 14.5);
            plus.moveTo(9.5, 11.5);
            plus.lineTo(15.5, 11.5);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(plus);
        } finally {
            g.dispose();
        }
        return image;
    }
}
