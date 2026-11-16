package org.deepsetk.crud;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.windows.WindowManager;

/** Abre el importador/exportador de bases MySQL. */
public final class DbBackupAction extends AbstractAction {

    public DbBackupAction() {
        putValue(Action.NAME, "Importar/Exportar base MySQL");
        putValue(Action.SMALL_ICON, DbIcons.database());
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        DbBackupDialog.open(WindowManager.getDefault().getMainWindow());
    }
}
