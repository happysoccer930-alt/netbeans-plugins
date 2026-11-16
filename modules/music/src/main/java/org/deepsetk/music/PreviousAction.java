package org.deepsetk.music;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

public final class PreviousAction extends AbstractAction implements Player.Listener {

    private final Player player = Player.get();

    public PreviousAction() {
        putValue(SMALL_ICON, Icons.previous());
        putValue(SHORT_DESCRIPTION, "Cancion anterior");
        player.addListener(this);
        update();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        player.previous();
    }

    @Override
    public void playerChanged(Player source) {
        update();
    }

    private void update() {
        setEnabled(!player.tracks().isEmpty());
    }
}
