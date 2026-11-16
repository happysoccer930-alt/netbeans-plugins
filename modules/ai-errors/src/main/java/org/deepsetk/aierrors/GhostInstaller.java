package org.deepsetk.aierrors;

import org.openide.modules.ModuleInstall;

/** Arranca el motor de sugerencias fantasma al cargar el modulo. */
public final class GhostInstaller extends ModuleInstall {

    @Override
    public void restored() {
        GhostCompletion.install();
    }
}
