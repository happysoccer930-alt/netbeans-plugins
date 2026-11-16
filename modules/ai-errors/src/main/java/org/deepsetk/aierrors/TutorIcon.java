package org.deepsetk.aierrors;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** Icono del tutor: una burbuja de dialogo con un signo de pregunta. */
final class TutorIcon {

    private static Icon icon;

    private TutorIcon() {
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
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x22, 0xD3, 0xEE));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new RoundRectangle2D.Double(1.5, 2.0, 13.0, 9.5, 3.0, 3.0));
            Path2D tail = new Path2D.Double();
            tail.moveTo(5.0, 11.0);
            tail.lineTo(4.0, 14.5);
            tail.lineTo(8.0, 11.5);
            tail.closePath();
            g.fill(tail);
            g.setFont(new Font("Segoe UI", Font.BOLD, 9));
            g.drawString("?", 6, 9);
        } finally {
            g.dispose();
        }
        return image;
    }
}
