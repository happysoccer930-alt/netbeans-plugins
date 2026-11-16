package org.deepsetk.music;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/** Abre (o trae al frente) la ventana con la lista de reproduccion. */
public final class ShowPlaylistAction extends AbstractAction {

    public ShowPlaylistAction() {
        putValue(NAME, "Lista de música");
        putValue(SMALL_ICON, Icons.list());
        putValue(SHORT_DESCRIPTION, "Abre la lista de reproducción de música");
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        PlaylistTopComponent component = PlaylistTopComponent.findInstance();
        component.open();
        component.requestActive();
    }
}
