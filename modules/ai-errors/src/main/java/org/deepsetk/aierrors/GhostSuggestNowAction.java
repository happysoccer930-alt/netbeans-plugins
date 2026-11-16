package org.deepsetk.aierrors;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/** Pide una sugerencia de IA inmediata, sin esperar al temporizador. */
public final class GhostSuggestNowAction extends AbstractAction {

    public GhostSuggestNowAction() {
        putValue(NAME, "Pedir sugerencia IA ahora (Ctrl+Alt+Espacio)");
        putValue(javax.swing.Action.SMALL_ICON, UiIcons.sparkle());
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        GhostCompletion.suggestNow();
    }
}
