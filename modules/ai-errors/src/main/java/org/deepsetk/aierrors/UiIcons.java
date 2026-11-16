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

/** Iconos 16x16 dibujados para los botones y menus del plugin. */
final class UiIcons {

    private static Icon chip;
    private static Icon braces;
    private static Icon gear;
    private static Icon ghost;
    private static Icon sparkle;
    private static Icon code;
    private static Icon bulb;

    private UiIcons() {
    }

    static synchronized Icon chip() {
        if (chip == null) {
            chip = new ImageIcon(draw(g -> {
                g.setColor(new Color(0x22D3EE));
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new RoundRectangle2D.Double(3, 3, 10, 10, 3, 3));
                for (int i = 5; i <= 11; i += 3) {
                    g.drawLine(i, 1, i, 3);
                    g.drawLine(i, 13, i, 15);
                    g.drawLine(1, i, 3, i);
                    g.drawLine(13, i, 15, i);
                }
                g.setFont(new Font("Segoe UI", Font.BOLD, 6));
                g.drawString("AI", 5, 10);
            }));
        }
        return chip;
    }

    static synchronized Icon braces() {
        if (braces == null) {
            braces = new ImageIcon(draw(g -> {
                g.setColor(new Color(0x00C853));
                g.setFont(new Font("Consolas", Font.BOLD, 13));
                g.drawString("{", 1, 12);
                g.drawString("}", 9, 12);
            }));
        }
        return braces;
    }

    static synchronized Icon gear() {
        if (gear == null) {
            gear = new ImageIcon(draw(g -> {
                g.setColor(new Color(0xB0BEC5));
                g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(4, 4, 8, 8);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * i / 4.0;
                    int x1 = 8 + (int) Math.round(5 * Math.cos(angle));
                    int y1 = 8 + (int) Math.round(5 * Math.sin(angle));
                    int x2 = 8 + (int) Math.round(7 * Math.cos(angle));
                    int y2 = 8 + (int) Math.round(7 * Math.sin(angle));
                    g.drawLine(x1, y1, x2, y2);
                }
                g.fillOval(7, 7, 2, 2);
            }));
        }
        return gear;
    }

    static synchronized Icon ghost() {
        if (ghost == null) {
            ghost = new ImageIcon(draw(g -> {
                g.setColor(new Color(0xB39DDB));
                g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D path = new Path2D.Double();
                path.moveTo(3, 14);
                path.lineTo(3, 7);
                path.curveTo(3, 3, 13, 3, 13, 7);
                path.lineTo(13, 14);
                path.lineTo(11, 12);
                path.lineTo(9, 14);
                path.lineTo(7, 12);
                path.lineTo(5, 14);
                path.closePath();
                g.draw(path);
                g.fillOval(6, 7, 1, 2);
                g.fillOval(9, 7, 1, 2);
            }));
        }
        return ghost;
    }

    static synchronized Icon sparkle() {
        if (sparkle == null) {
            sparkle = new ImageIcon(draw(g -> {
                g.setColor(new Color(0xFFD54F));
                Path2D star = new Path2D.Double();
                star.moveTo(8, 1);
                star.lineTo(9.5, 6.5);
                star.lineTo(15, 8);
                star.lineTo(9.5, 9.5);
                star.lineTo(8, 15);
                star.lineTo(6.5, 9.5);
                star.lineTo(1, 8);
                star.lineTo(6.5, 6.5);
                star.closePath();
                g.fill(star);
            }));
        }
        return sparkle;
    }

    static synchronized Icon code() {
        if (code == null) {
            code = new ImageIcon(draw(g -> {
                g.setColor(new Color(0x22D3EE));
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(5, 4, 1, 8);
                g.drawLine(1, 8, 5, 12);
                g.drawLine(11, 4, 15, 8);
                g.drawLine(15, 8, 11, 12);
                g.drawLine(9, 2, 7, 14);
            }));
        }
        return code;
    }

    static synchronized Icon bulb() {
        if (bulb == null) {
            bulb = new ImageIcon(draw(g -> {
                g.setColor(new Color(0xFFB020));
                g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(4, 2, 8, 8);
                g.drawLine(6, 10, 6, 12);
                g.drawLine(10, 10, 10, 12);
                g.drawLine(6, 13, 10, 13);
            }));
        }
        return bulb;
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
