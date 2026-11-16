package org.deepsetk.aierrors;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.awt.StatusDisplayer;

/**
 * Accion "Configurar Gemini" (API key y modelo).
 */
public final class ConfigureAction extends AbstractAction {

    public ConfigureAction() {
        putValue(Action.NAME, "Configurar Gemini (API key y modelo)");
        putValue(Action.SMALL_ICON, UiIcons.gear());
        putValue(Action.SHORT_DESCRIPTION,
                "Guarda tu API key de Gemini y elige el modelo desde la lista oficial");
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        boolean saved = GeminiConfigDialog.open(null);
        if (saved) {
            StatusDisplayer.getDefault().setStatusText(
                    "Gemini configurado con el modelo " + GeminiSettings.model().replace("models/", ""));
        }
    }
}
