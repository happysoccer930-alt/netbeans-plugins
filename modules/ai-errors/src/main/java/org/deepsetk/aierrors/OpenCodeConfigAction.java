package org.deepsetk.aierrors;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import org.openide.awt.StatusDisplayer;

/** Configura OpenCode Zen free: URL, modelo y API key. */
public final class OpenCodeConfigAction extends AbstractAction {

    public OpenCodeConfigAction() {
        putValue(NAME, "Configurar OpenCode Zen free");
        putValue(javax.swing.Action.SMALL_ICON, UiIcons.code());
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        JTextField url = new JTextField(OpenCodeSettings.url(), 34);
        JTextField model = new JTextField(OpenCodeSettings.model(), 34);
        JPasswordField key = new JPasswordField(OpenCodeSettings.apiKey(), 34);
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("URL base:"));
        panel.add(url);
        panel.add(new JLabel("Modelo free:"));
        panel.add(model);
        panel.add(new JLabel("API key de OpenCode Zen:"));
        panel.add(key);
        int result = JOptionPane.showConfirmDialog(null, panel,
                "OpenCode Zen free", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            OpenCodeSettings.save(url.getText(), model.getText(), new String(key.getPassword()));
            SuggestionCache.clear();
            StatusDisplayer.getDefault().setStatusText(
                    "OpenCode Zen configurado: " + model.getText().strip());
        }
    }
}
