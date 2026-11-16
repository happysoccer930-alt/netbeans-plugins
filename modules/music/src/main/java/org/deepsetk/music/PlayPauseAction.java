package org.deepsetk.music;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/** Boton unico reproducir/pausar que cambia de icono segun el estado. */
public final class PlayPauseAction extends AbstractAction implements Player.Listener {

    private final Player player = Player.get();

    public PlayPauseAction() {
        putValue(SHORT_DESCRIPTION, "Reproducir o pausar");
        player.addListener(this);
        update();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        player.toggle();
    }

    @Override
    public void playerChanged(Player source) {
        update();
    }

    private void update() {
        if (player.state() == Player.State.PLAYING) {
            putValue(NAME, "Pausar");
            putValue(SMALL_ICON, Icons.pause());
        } else {
            putValue(NAME, "Reproducir");
            putValue(SMALL_ICON, Icons.play());
        }
        setEnabled(!player.tracks().isEmpty());
    }
}
