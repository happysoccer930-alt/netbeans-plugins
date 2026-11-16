package org.deepsetk.aierrors;

import com.sun.source.util.TreePath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.swing.text.Document;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.JavaSource;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 * Resuelve el tipo real del receptor antes del punto y devuelve sus metodos y
 * campos usando el parser de NetBeans (JavaSource).
 */
final class RealMembers {

    private static final Logger LOG = Logger.getLogger(RealMembers.class.getName());

    private RealMembers() {
    }

    /** Miembros cuyo nombre empieza por prefix, para un cursor situado tras un punto. */
    static List<String> members(Document document, int dot, String prefix) {
        if (document == null || dot <= 0) {
            return List.of();
        }
        FileObject file = fileObject(document);
        if (file == null) {
            return List.of();
        }
        JavaSource source = JavaSource.forFileObject(file);
        if (source == null) {
            return List.of();
        }
        String wanted = prefix == null ? "" : prefix;
        List<String> result = new ArrayList<>();
        try {
            source.runUserActionTask(ci -> {
                try {
                    collect(ci, dot, wanted, result);
                } catch (RuntimeException ex) {
                    LOG.log(Level.FINE, "No se pudo resolver el receptor", ex);
                }
            }, true);
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.FINE, "JavaSource no disponible para miembros", ex);
            return List.of();
        }
        return result;
    }

    private static void collect(CompilationInfo ci, int dot, String prefix, List<String> out) {
        TreePath path = ci.getTreeUtilities().pathFor(dot - 1);
        if (path == null) {
            return;
        }
        TypeMirror type = ci.getTrees().getTypeMirror(path);
        if (type == null) {
            return;
        }
        Element element = ci.getTypes().asElement(type);
        if (!(element instanceof TypeElement typeElement)) {
            return;
        }
        Set<String> names = new TreeSet<>();
        for (Element member : ci.getElements().getAllMembers(typeElement)) {
            ElementKind kind = member.getKind();
            if (kind != ElementKind.METHOD && kind != ElementKind.FIELD
                    && kind != ElementKind.ENUM_CONSTANT) {
                continue;
            }
            if (member.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            String name = member.getSimpleName().toString();
            if (name.isEmpty() || name.equals("<init>") || name.equals("<clinit>")) {
                continue;
            }
            if (!name.startsWith(prefix)) {
                continue;
            }
            names.add(name);
        }
        out.addAll(names);
    }

    private static FileObject fileObject(Document document) {
        Object stream = document.getProperty(Document.StreamDescriptionProperty);
        if (stream instanceof DataObject dataObject) {
            return dataObject.getPrimaryFile();
        }
        return null;
    }
}
