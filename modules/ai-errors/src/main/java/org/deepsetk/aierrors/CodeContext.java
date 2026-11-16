package org.deepsetk.aierrors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.java.source.JavaSource;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.cookies.EditorCookie;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Recoge el codigo alrededor del cursor (o el archivo completo, para el tutor)
 * y los diagnosticos reales que NetBeans ya calculo para ese fichero Java.
 */
final class CodeContext {

    private static final int CONTEXT_LINES = 25;
    private static final int MAX_CHARS = 14_000;
    private static final int MAX_TUTOR_CHARS = 24_000;
    private static final int MAX_SELECTION = 6_000;
    private static final int MAX_DIAGNOSTICS = 12;

    final String fileName;
    final String mimeType;
    final String code;
    final String selection;
    final int caretLine;
    private final FileObject file;

    private CodeContext(String fileName, String mimeType, String code, String selection,
            int caretLine, FileObject file) {
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.code = code;
        this.selection = selection;
        this.caretLine = caretLine;
        this.file = file;
    }

    /** Contexto corto alrededor del cursor, para explicar un error. */
    static CodeContext capture() {
        return capture(CONTEXT_LINES, MAX_CHARS);
    }

    /** Archivo completo (recortado si es enorme), para el tutor. */
    static CodeContext captureFull() {
        return capture(-1, MAX_TUTOR_CHARS);
    }

    /** Debe llamarse desde el hilo de la UI. */
    private static CodeContext capture(int contextLines, int maxChars) {
        JTextComponent pane = EditorRegistry.lastFocusedComponent();
        if (pane == null) {
            EditorCookie cookie = editorCookie();
            if (cookie != null) {
                JTextComponent[] panes = cookie.getOpenedPanes();
                if (panes != null && panes.length > 0) {
                    pane = panes[0];
                }
            }
        }
        if (pane == null) {
            return null;
        }
        Document doc = pane.getDocument();
        if (doc == null) {
            return null;
        }

        DataObject dataObject = (DataObject) doc.getProperty(Document.StreamDescriptionProperty);
        FileObject fo = dataObject == null ? null : dataObject.getPrimaryFile();
        String name = fo == null ? "(sin nombre)" : fo.getNameExt();
        String mime = fo == null ? "text/plain" : fo.getMIMEType();

        Element root = doc.getDefaultRootElement();
        int lineCount = root.getElementCount();
        int caretLine = root.getElementIndex(pane.getCaretPosition());
        int from;
        int to;
        if (contextLines < 0) {
            from = 0;
            to = lineCount - 1;
        } else {
            from = Math.max(0, caretLine - contextLines);
            to = Math.min(lineCount - 1, caretLine + contextLines);
        }

        String code;
        try {
            int start = root.getElement(from).getStartOffset();
            int end = root.getElement(to).getEndOffset();
            String raw = doc.getText(start, Math.max(0, end - start));
            StringBuilder sb = new StringBuilder();
            int number = from + 1;
            for (String line : raw.split("\n", -1)) {
                sb.append(number++).append(": ").append(line).append('\n');
                if (sb.length() > maxChars) {
                    sb.append("... (recortado)\n");
                    break;
                }
            }
            code = sb.toString();
        } catch (Exception ex) {
            code = "";
        }

        String selection = pane.getSelectedText();
        if (selection == null) {
            selection = "";
        } else if (selection.length() > MAX_SELECTION) {
            selection = selection.substring(0, MAX_SELECTION) + "\n... (seleccion recortada)";
        }
        return new CodeContext(name, mime, code, selection, caretLine + 1, fo);
    }

    /** Puede llamarse desde un hilo de fondo (usa el parser de Java). */
    String diagnostics() {
        if (file == null || !mimeType.contains("java")) {
            return "";
        }
        record Diag(long line, String text) {
        }
        List<Diag> found = new ArrayList<>();
        try {
            JavaSource source = JavaSource.forFileObject(file);
            if (source == null) {
                return "";
            }
            source.runUserActionTask(cc -> {
                cc.toPhase(JavaSource.Phase.RESOLVED);
                for (Diagnostic<? extends JavaFileObject> d : cc.getDiagnostics()) {
                    Diagnostic.Kind kind = d.getKind();
                    if (kind != Diagnostic.Kind.ERROR && kind != Diagnostic.Kind.WARNING
                            && kind != Diagnostic.Kind.MANDATORY_WARNING) {
                        continue;
                    }
                    String text = d.getMessage(Locale.getDefault());
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    found.add(new Diag(d.getLineNumber(),
                            (kind == Diagnostic.Kind.ERROR ? "error" : "aviso") + " (linea "
                                    + d.getLineNumber() + "): " + text.replace('\n', ' ')));
                }
            }, true);
        } catch (Exception ex) {
            return "";
        }
        if (found.isEmpty()) {
            return "";
        }
        found.sort(Comparator.comparingLong(d -> Math.abs(d.line() - caretLine)));
        StringBuilder sb = new StringBuilder();
        int count = Math.min(found.size(), MAX_DIAGNOSTICS);
        for (int i = 0; i < count; i++) {
            sb.append("- ").append(found.get(i).text()).append('\n');
        }
        return sb.toString();
    }

    private static EditorCookie editorCookie() {
        TopComponent active = WindowManager.getDefault().getRegistry().getActivated();
        if (active != null) {
            EditorCookie cookie = active.getLookup().lookup(EditorCookie.class);
            if (cookie != null) {
                return cookie;
            }
        }
        for (TopComponent component : WindowManager.getDefault().getRegistry().getOpened()) {
            EditorCookie cookie = component.getLookup().lookup(EditorCookie.class);
            if (cookie != null) {
                return cookie;
            }
        }
        return null;
    }
}
