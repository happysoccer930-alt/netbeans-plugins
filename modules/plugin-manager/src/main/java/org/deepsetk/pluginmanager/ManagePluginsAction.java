package org.deepsetk.pluginmanager;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;

/** Abre el gestor de plugins. */
public final class ManagePluginsAction extends AbstractAction {

    public ManagePluginsAction() {
        putValue(Action.NAME, "Gestionar plugins (activar/desactivar)");
        putValue(Action.SMALL_ICON, ManagerIcon.icon());
        putValue(Action.SHORT_DESCRIPTION,
                "Elige qué módulos de NetBeans quieres activar o desactivar; protege las dependencias");
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        PluginManagerDialog.open();
    }
}
