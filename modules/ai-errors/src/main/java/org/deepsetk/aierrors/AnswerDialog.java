package org.deepsetk.aierrors;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;
import org.openide.windows.WindowManager;

/**
 * Ventana no modal con la respuesta de Gemini en HTML.
 */
final class AnswerDialog extends JDialog {

    private static final String STYLE = "<html><body style='font-family:Segoe UI;font-size:11px'>";

    private final JEditorPane pane = new JEditorPane();
    private final JLabel status = new JLabel(" ");
    private final JButton copy = new JButton("Copiar");
    private String raw = "";

    AnswerDialog(String title, String subtitle) {
        super(WindowManager.getDefault().getMainWindow(), title, false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setBackground(new Color(0x1B1E22));
        pane.setForeground(new Color(0xD6DAE0));
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        JLabel header = new JLabel(subtitle);
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        status.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        status.setForeground(new Color(0x8A929C));

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        copy.setEnabled(false);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(raw), null);
            status.setText("Copiado al portapapeles");
        });
        JButton close = new JButton("Cerrar");
        close.addActionListener(e -> dispose());

        JPanel south = new JPanel(new BorderLayout());
        south.add(status, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        buttons.add(copy);
        buttons.add(close);
        south.add(buttons, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(940, 640));
        pack();
        setLocationRelativeTo(getOwner());
    }

    void showLoading(String message) {
        render("<p>" + message + "</p>");
    }

    void showAnswer(String markdown) {
        raw = markdown;
        copy.setEnabled(true);
        status.setText("Listo");
        render(Markdown.toHtml(markdown));
    }

    void showError(String message) {
        status.setText("Error");
        render("<p><b><font color=\"#d98b8b\">No se pudo obtener la explicacion</font></b></p>"
                + "<p>" + escape(message) + "</p>"
                + "<p><font color=\"#8A929C\">Revisa Herramientas &gt; Configurar Gemini: "
                + "API key, modelo y conexion.</font></p>");
    }

    private void render(String html) {
        pane.setText(STYLE + html + "</body></html>");
        pane.setCaretPosition(0);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
