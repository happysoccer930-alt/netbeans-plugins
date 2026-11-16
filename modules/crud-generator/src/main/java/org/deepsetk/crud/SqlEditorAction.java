package org.deepsetk.crud;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.windows.WindowManager;

/** Abre el editor SQL con conexion a MySQL. */
public final class SqlEditorAction extends AbstractAction {

    public SqlEditorAction() {
        putValue(Action.NAME, "Editor SQL (MySQL)");
        putValue(Action.SMALL_ICON, DbIcons.sql());
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        SqlEditorDialog.open(WindowManager.getDefault().getMainWindow());
    }
}
