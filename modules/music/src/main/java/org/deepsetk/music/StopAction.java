package org.deepsetk.music;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

public final class StopAction extends AbstractAction implements Player.Listener {

    private final Player player = Player.get();

    public StopAction() {
        putValue(SMALL_ICON, Icons.stop());
        putValue(SHORT_DESCRIPTION, "Detener la reproduccion");
        player.addListener(this);
        update();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        player.stop();
    }

    @Override
    public void playerChanged(Player source) {
        update();
    }

    private void update() {
        setEnabled(player.state() != Player.State.STOPPED);
    }
}
