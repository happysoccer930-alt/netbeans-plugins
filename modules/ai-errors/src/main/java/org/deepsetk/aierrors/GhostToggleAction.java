package org.deepsetk.aierrors;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/** Activa o desactiva las sugerencias fantasma del editor. */
public final class GhostToggleAction extends AbstractAction {

    public GhostToggleAction() {
        putValue(NAME, "Sugerencias fantasma (Tab)");
        putValue(javax.swing.Action.SMALL_ICON, UiIcons.ghost());
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        GhostCompletion.toggle();
    }
}
