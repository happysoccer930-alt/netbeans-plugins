package org.deepsetk.music;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

public final class NextAction extends AbstractAction implements Player.Listener {

    private final Player player = Player.get();

    public NextAction() {
        putValue(SMALL_ICON, Icons.next());
        putValue(SHORT_DESCRIPTION, "Siguiente cancion");
        player.addListener(this);
        update();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        player.next();
    }

    @Override
    public void playerChanged(Player source) {
        update();
    }

    private void update() {
        setEnabled(!player.tracks().isEmpty());
    }
}
