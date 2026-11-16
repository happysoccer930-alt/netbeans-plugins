package org.deepsetk.aierrors;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.openide.awt.StatusDisplayer;

/** Configura el servidor LLM local (LM Studio / Ollama / llama.cpp). */
public final class LocalLlmConfigAction extends AbstractAction {

    public LocalLlmConfigAction() {
        putValue(NAME, "Configurar LLM local");
        putValue(javax.swing.Action.SMALL_ICON, UiIcons.chip());
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        JTextField url = new JTextField(GhostCompletion.localUrl(), 32);
        JTextField model = new JTextField(GhostCompletion.localModel(), 32);
        JCheckBox enabled = new JCheckBox(
                "Usar este LLM local como respaldo (si no hay internet)",
                GhostCompletion.localEnabled());
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(enabled);
        panel.add(new JLabel("URL base (LM Studio: http://127.0.0.1:1234/v1):"));
        panel.add(url);
        panel.add(new JLabel("Modelo (vacío = elegir uno con 'coder'):"));
        panel.add(model);
        int result = JOptionPane.showConfirmDialog(null, panel,
                "LLM local", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            GhostCompletion.setLocalEnabled(enabled.isSelected());
            GhostCompletion.setLocalConfig(url.getText(), model.getText());
            StatusDisplayer.getDefault().setStatusText(
                    (enabled.isSelected() ? "LLM local activo: " : "LLM local desactivado. ")
                    + url.getText().strip()
                    + (model.getText().isBlank() ? "" : " / " + model.getText().strip()));
        }
    }
}
