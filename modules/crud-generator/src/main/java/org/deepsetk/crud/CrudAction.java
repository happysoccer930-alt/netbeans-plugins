package org.deepsetk.crud;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;

/** Abre el generador de CRUD. */
public final class CrudAction extends AbstractAction {

    public CrudAction() {
        putValue(Action.NAME, "Generar CRUD (Java + MySQL)");
        putValue(Action.SMALL_ICON, CrudIcon.icon());
        putValue(Action.SHORT_DESCRIPTION,
                "Pega un CREATE TABLE y genera Entity, DAO JDBC, Service y SQL");
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        CrudDialog.open();
    }
}
