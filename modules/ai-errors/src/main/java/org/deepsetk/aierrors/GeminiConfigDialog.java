package org.deepsetk.aierrors;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.openide.windows.WindowManager;

/**
 * Dialogo de configuracion: API key de Gemini + modelo (lista descargada de la
 * propia API) + prueba de conexion.
 */
final class GeminiConfigDialog extends JDialog {

    private static final String API_KEY_URL = "https://aistudio.google.com/apikey";

    private final JPasswordField keyField = new JPasswordField(40);
    private final JComboBox<GeminiClient.Model> modelCombo = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final JButton refresh = new JButton("Actualizar modelos");
    private final JButton test = new JButton("Probar");
    private final GeminiClient client = new GeminiClient();
    private boolean saved;

    private GeminiConfigDialog(Component parent) {
        super(WindowManager.getDefault().getMainWindow(), "Configurar Gemini", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        keyField.setText(GeminiSettings.apiKey());
        keyField.setEchoChar('\u2022');

        JLabel link = new JLabel("<html><a href='" + API_KEY_URL + "'>obtener una API key</a></html>");
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(URI.create(API_KEY_URL));
                } catch (Exception ignored) {
                    // sin navegador disponible
                }
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = 0;

        gbc.gridx = 0;
        form.add(new JLabel("API key:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(keyField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        form.add(link, gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        form.add(new JLabel("Modelo:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(modelCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tools.add(refresh);
        tools.add(new JLabel("  "));
        tools.add(test);
        form.add(tools, gbc);

        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        status.setForeground(new Color(0x6F7883));
        form.add(status, gbc);

        refresh.addActionListener(e -> loadModels(true));
        test.addActionListener(e -> testConnection());

        JButton save = new JButton("Guardar");
        save.addActionListener(e -> {
            String key = new String(keyField.getPassword()).trim();
            if (!key.isEmpty()) {
                GeminiSettings.setApiKey(key);
            }
            GeminiClient.Model selected = (GeminiClient.Model) modelCombo.getSelectedItem();
            if (selected != null) {
                GeminiSettings.setModel(selected.name());
            }
            saved = true;
            dispose();
        });
        JButton cancel = new JButton("Cancelar");
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        buttons.add(save);
        buttons.add(cancel);

        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.EAST);

        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(620, 260));
        pack();
        setLocationRelativeTo(parent != null ? parent : getOwner());

        if (GeminiSettings.hasKey()) {
            loadModels(false);
        } else {
            status.setText("Pega tu API key y pulsa \"Actualizar modelos\".");
        }
    }

    /** Abre el dialogo y devuelve true si se guardo la configuracion. */
    static boolean open(Component parent) {
        GeminiConfigDialog dialog = new GeminiConfigDialog(parent);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private void loadModels(boolean announce) {
        String key = new String(keyField.getPassword()).trim();
        if (key.isEmpty()) {
            status.setText("Escribe primero tu API key.");
            return;
        }
        setBusy(true);
        status.setText("Descargando lista de modelos...");
        new Thread(() -> {
            try {
                List<GeminiClient.Model> models = client.listModels(key);
                SwingUtilities.invokeLater(() -> {
                    fillModels(models);
                    status.setText(models.size() + " modelos disponibles. Elige uno y guarda.");
                    setBusy(false);
                });
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                SwingUtilities.invokeLater(() -> {
                    status.setText(message);
                    setBusy(false);
                });
            }
        }, "deepsetk-gemini-models").start();
        if (announce) {
            status.setText("Descargando lista de modelos...");
        }
    }

    private void testConnection() {
        String key = new String(keyField.getPassword()).trim();
        if (key.isEmpty()) {
            status.setText("Escribe primero tu API key.");
            return;
        }
        GeminiClient.Model selected = (GeminiClient.Model) modelCombo.getSelectedItem();
        setBusy(true);
        status.setText("Probando conexion...");
        new Thread(() -> {
            try {
                List<GeminiClient.Model> models = client.listModels(key);
                SwingUtilities.invokeLater(() -> {
                    fillModels(models);
                    status.setText("Conexion correcta: " + models.size() + " modelos.");
                    setBusy(false);
                });
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                SwingUtilities.invokeLater(() -> {
                    status.setText("Fallo: " + message
                            + (selected == null ? "" : " (modelo probado: " + selected.name() + ")"));
                    setBusy(false);
                });
            }
        }, "deepsetk-gemini-test").start();
    }

    private void fillModels(List<GeminiClient.Model> models) {
        String current = GeminiSettings.model();
        if (modelCombo.getSelectedItem() instanceof GeminiClient.Model selected) {
            current = selected.name();
        }
        modelCombo.removeAllItems();
        for (GeminiClient.Model model : models) {
            modelCombo.addItem(model);
        }
        for (int i = 0; i < modelCombo.getItemCount(); i++) {
            if (modelCombo.getItemAt(i).name().equals(current)) {
                modelCombo.setSelectedIndex(i);
                return;
            }
        }
        for (int i = 0; i < modelCombo.getItemCount(); i++) {
            if (modelCombo.getItemAt(i).name().toLowerCase().contains("flash")) {
                modelCombo.setSelectedIndex(i);
                return;
            }
        }
        if (modelCombo.getItemCount() > 0) {
            modelCombo.setSelectedIndex(0);
        }
    }

    private void setBusy(boolean busy) {
        refresh.setEnabled(!busy);
        test.setEnabled(!busy);
    }
}
