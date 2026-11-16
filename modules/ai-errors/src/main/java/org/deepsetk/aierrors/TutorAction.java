package org.deepsetk.aierrors;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;

/** Abre la ventana del tutor de programacion con Gemini. */
public final class TutorAction extends AbstractAction {

    public TutorAction() {
        putValue(Action.NAME, "Tutor Gemini: aprender con mi código");
        putValue(Action.SMALL_ICON, TutorIcon.icon());
        putValue(Action.SHORT_DESCRIPTION,
                "Abre el tutor: explica tu código, lo revisa y te hace preguntas de práctica");
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        TutorTopComponent component = TutorTopComponent.findInstance();
        component.open();
        component.requestActive();
    }
}
