package org.deepsetk.uithemes;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.openide.awt.StatusDisplayer;

/** Quita el tema y vuelve al perfil Soft Dark. */
public final class DisableThemeAction extends AbstractAction {

    public DisableThemeAction() {
        putValue(NAME, "Quitar tema (volver a Soft Dark)");
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        ThemeSwitch.activate(ThemeSwitch.SOFT_DARK);
        DeepSetkUI.markDisabled();
        StatusDisplayer.getDefault().setStatusText(
                "Tema desactivado. Se volverá a Soft Dark al reiniciar.");
        ThemeSwitch.askRestart("Soft Dark");
    }
}
