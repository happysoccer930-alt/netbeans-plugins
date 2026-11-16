package org.deepsetk.aierrors;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.awt.StatusDisplayer;

/** Activa o desactiva el cierre automatico de parentesis, llaves y comillas. */
public final class AutoPairToggleAction extends AbstractAction {

    public AutoPairToggleAction() {
        putValue(NAME, "Cierre automatico de simbolos");
        putValue(SHORT_DESCRIPTION,
                "Auto-cierra (), {}, [] y comillas, y envuelve la seleccion");
        putValue(Action.SMALL_ICON, UiIcons.braces());
        putValue(Action.SELECTED_KEY, GhostCompletion.autoPairEnabled());
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        boolean enabled = !GhostCompletion.autoPairEnabled();
        GhostCompletion.setAutoPair(enabled);
        putValue(Action.SELECTED_KEY, enabled);
        StatusDisplayer.getDefault().setStatusText(enabled
                ? "Cierre automatico de simbolos activado."
                : "Cierre automatico de simbolos desactivado.");
    }
}
