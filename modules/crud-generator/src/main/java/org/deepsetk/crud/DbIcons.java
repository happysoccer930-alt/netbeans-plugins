package org.deepsetk.crud;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** Iconos 16x16 para las acciones de base de datos y editor SQL. */
final class DbIcons {

    private static Icon database;
    private static Icon sql;

    private DbIcons() {
    }

    static synchronized Icon database() {
        if (database == null) {
            database = new ImageIcon(draw(g -> {
                g.setColor(new Color(0x22D3EE));
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(3, 2, 10, 4);
                g.drawLine(3, 4, 3, 12);
                g.drawLine(13, 4, 13, 12);
                g.drawArc(3, 10, 10, 4, 180, 180);
                g.setColor(new Color(0x00C853));
                g.drawLine(5, 7, 5, 10);
                g.drawLine(4, 9, 5, 10);
                g.drawLine(6, 9, 5, 10);
                g.setColor(new Color(0xFFB020));
                g.drawLine(11, 10, 11, 7);
                g.drawLine(10, 8, 11, 7);
                g.drawLine(12, 8, 11, 7);
            }));
        }
        return database;
    }

    static synchronized Icon sql() {
        if (sql == null) {
            sql = new ImageIcon(draw(g -> {
                g.setColor(new Color(0x22D3EE));
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawRoundRect(2, 1, 12, 14, 3, 3);
                g.setColor(new Color(0x00C853));
                g.setFont(new Font("Consolas", Font.BOLD, 6));
                g.drawString("SQL", 3, 9);
                g.setColor(new Color(0x8FA6C4));
                g.drawLine(4, 11, 12, 11);
                g.drawLine(4, 12, 9, 12);
            }));
        }
        return sql;
    }

    private static BufferedImage draw(java.util.function.Consumer<Graphics2D> painter) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            painter.accept(g);
        } finally {
            g.dispose();
        }
        return image;
    }
}
