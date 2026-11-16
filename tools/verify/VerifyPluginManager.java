package org.deepsetk.pluginmanager;

import java.io.File;
import org.openide.filesystems.XMLFileSystem;

/** Verificacion offline del gestor de plugins: layer e icono. */
public final class VerifyPluginManager {

    private static int failures;

    public static void main(String[] args) throws Exception {
        String resDir = args[0];
        XMLFileSystem fs = new XMLFileSystem(new File(resDir, "layer.xml").toURI().toURL());

        check(fs.findResource("Actions/Tools/org-deepsetk-pluginmanager-manage.instance") != null,
                "accion del gestor registrada");
        check(fs.findResource("Menu/Tools/org-deepsetk-pluginmanager-manage.shadow") != null,
                "menu Herramientas -> Gestionar plugins");
        check(fs.findResource("Toolbars/File/org-deepsetk-pluginmanager-manage.shadow") != null,
                "boton del gestor en la barra");
        check(ManagerIcon.image().getWidth() == 16 && ManagerIcon.image().getHeight() == 16,
                "icono del gestor 16x16");
        check(ManagerIcon.icon() != null, "icono del gestor disponible");

        System.out.println(failures == 0 ? "VERIFICACION OK" : "FALLOS: " + failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "  OK   " : "  FALLO ") + what);
        if (!ok) {
            failures++;
        }
    }
}
