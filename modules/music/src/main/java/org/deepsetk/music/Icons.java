package org.deepsetk.music;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Iconos de 16x16 dibujados con Java2D, en un gris visible sobre temas claros
 * y oscuros. No hace falta empaquetar imagenes.
 */
final class Icons {

    private static final Color FOREGROUND = new Color(0x22, 0xD3, 0xEE);

    private static Icon play;
    private static Icon pause;
    private static Icon stop;
    private static Icon previous;
    private static Icon next;
    private static Icon list;
    private static Icon folder;

    private Icons() {
    }

    static synchronized Icon play() {
        if (play == null) {
            play = icon(g -> {
                Path2D triangle = new Path2D.Double();
                triangle.moveTo(4, 2);
                triangle.lineTo(13, 8);
                triangle.lineTo(4, 14);
                triangle.closePath();
                g.fill(triangle);
            });
        }
        return play;
    }

    static synchronized Icon pause() {
        if (pause == null) {
            pause = icon(g -> {
                g.fill(new RoundRectangle2D.Double(4, 2, 3, 12, 1, 1));
                g.fill(new RoundRectangle2D.Double(9, 2, 3, 12, 1, 1));
            });
        }
        return pause;
    }

    static synchronized Icon stop() {
        if (stop == null) {
            stop = icon(g -> g.fill(new RoundRectangle2D.Double(4, 4, 8, 8, 1, 1)));
        }
        return stop;
    }

    static synchronized Icon previous() {
        if (previous == null) {
            previous = icon(g -> {
                g.fill(new RoundRectangle2D.Double(3, 3, 2, 10, 1, 1));
                Path2D triangle = new Path2D.Double();
                triangle.moveTo(13, 2);
                triangle.lineTo(5, 8);
                triangle.lineTo(13, 14);
                triangle.closePath();
                g.fill(triangle);
            });
        }
        return previous;
    }

    static synchronized Icon next() {
        if (next == null) {
            next = icon(g -> {
                Path2D triangle = new Path2D.Double();
                triangle.moveTo(3, 2);
                triangle.lineTo(11, 8);
                triangle.lineTo(3, 14);
                triangle.closePath();
                g.fill(triangle);
                g.fill(new RoundRectangle2D.Double(11, 3, 2, 10, 1, 1));
            });
        }
        return next;
    }

    static synchronized Icon list() {
        if (list == null) {
            list = icon(g -> {
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int y = 4; y <= 12; y += 4) {
                    g.fillRect(2, y - 1, 3, 3);
                    g.drawLine(7, y, 14, y);
                }
            });
        }
        return list;
    }

    static synchronized Icon folder() {
        if (folder == null) {
            folder = icon(g -> {
                Path2D shape = new Path2D.Double();
                shape.moveTo(2, 4);
                shape.lineTo(6, 4);
                shape.lineTo(7.5, 6);
                shape.lineTo(14, 6);
                shape.lineTo(14, 13);
                shape.lineTo(2, 13);
                shape.closePath();
                g.fill(shape);
            });
        }
        return folder;
    }

    static synchronized BufferedImage listImage() {
        return redraw(g -> {
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int y = 4; y <= 12; y += 4) {
                g.fillRect(2, y - 1, 3, 3);
                g.drawLine(7, y, 14, y);
            }
        });
    }

    private interface Painter {

        void paint(Graphics2D g);
    }

    private static Icon icon(Painter painter) {
        return new ImageIcon(redraw(painter));
    }

    private static BufferedImage redraw(Painter painter) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(FOREGROUND);
            painter.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }
}
