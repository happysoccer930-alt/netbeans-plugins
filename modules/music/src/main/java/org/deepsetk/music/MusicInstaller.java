package org.deepsetk.music;

import org.openide.modules.ModuleInstall;

/** Prepara el reproductor al arrancar y corta el audio al desinstalar. */
public final class MusicInstaller extends ModuleInstall {

    @Override
    public void restored() {
        // Carga la lista guardada sin abrir ninguna ventana.
        Player.get();
    }

    @Override
    public void close() {
        Player.shutdown();
    }
}
