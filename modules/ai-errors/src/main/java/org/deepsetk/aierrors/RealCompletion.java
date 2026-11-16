package org.deepsetk.aierrors;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.TypeElement;
import javax.swing.text.Document;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.ElementHandle;

/**
 * Nombres de clase reales del proyecto y del JDK usando el indice de NetBeans
 * (ClassIndex). Sustituye la lista estatica de ~58 palabras por todas las
 * clases disponibles en el classpath de compilacion.
 */
final class RealCompletion {

    private static final Logger LOG = Logger.getLogger(RealCompletion.class.getName());

    private RealCompletion() {
    }

    /** Clases cuyo nombre simple empieza por el prefijo dado. */
    static List<String> typeNames(Document document, String prefix) {
        if (document == null || prefix == null || prefix.length() < 2) {
            return List.of();
        }
        try {
            ClasspathInfo info = ClasspathInfo.create(document);
            if (info == null) {
                return List.of();
            }
            ClassIndex index = info.getClassIndex();
            if (index == null) {
                return List.of();
            }
            Set<ElementHandle<TypeElement>> types = index.getDeclaredTypes(
                    prefix,
                    ClassIndex.NameKind.PREFIX,
                    EnumSet.of(ClassIndex.SearchScope.SOURCE, ClassIndex.SearchScope.DEPENDENCIES));
            if (types == null || types.isEmpty()) {
                return List.of();
            }
            List<String> names = new ArrayList<>(types.size());
            for (ElementHandle<TypeElement> handle : types) {
                String simple = simpleName(handle.getBinaryName());
                if (simple != null && simple.length() > prefix.length()
                        && simple.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    names.add(simple);
                }
            }
            return names;
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "Indice de clases no disponible", ex);
            return List.of();
        }
    }

    private static String simpleName(String binaryName) {
        if (binaryName == null) {
            return null;
        }
        int dot = binaryName.lastIndexOf('.');
        int dollar = binaryName.lastIndexOf('$');
        int cut = Math.max(dot, dollar);
        return cut < 0 ? binaryName : binaryName.substring(cut + 1);
    }
}
