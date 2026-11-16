package org.deepsetk.aierrors;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.awt.StatusDisplayer;

/**
 * Boton de activacion rapida del LLM local (LM Studio / Ollama / llama.cpp)
 * como respaldo offline. Se muestra como toggle con marca de activado.
 */
public final class LocalLlmToggleAction extends AbstractAction {

    public LocalLlmToggleAction() {
        putValue(NAME, "IA local (respaldo offline)");
        putValue(SHORT_DESCRIPTION,
                "Activa o desactiva el LLM local cuando no hay internet");
        putValue(Action.SMALL_ICON, UiIcons.chip());
        putValue(Action.SELECTED_KEY, GhostCompletion.localEnabled());
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        boolean enabled = !GhostCompletion.localEnabled();
        GhostCompletion.setLocalEnabled(enabled);
        putValue(Action.SELECTED_KEY, enabled);
        StatusDisplayer.getDefault().setStatusText(enabled
                ? "IA local activada como respaldo offline."
                : "IA local desactivada.");
    }
}
