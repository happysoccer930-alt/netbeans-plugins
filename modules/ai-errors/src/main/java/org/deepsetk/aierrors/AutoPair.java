package org.deepsetk.aierrors;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * Cierre automatico de parentesis, llaves, corchetes y comillas, con salto del
 * cierre existente y envoltura de la seleccion.
 *
 * Es idempotente: si NetBeans ya inserto el cierre (bracket completion nativo),
 * no duplica nada.
 */
final class AutoPair {

    private static final String OPEN = "([{\"'";
    private static final String CLOSE = ")]}\"'";
    private static final String KEY_INSTALLED = "org.deepsetk.aierrors.autopair.installed";

    private AutoPair() {
    }

    static void install(JTextComponent component) {
        if (component == null || Boolean.TRUE.equals(component.getClientProperty(KEY_INSTALLED))) {
            return;
        }
        component.putClientProperty(KEY_INSTALLED, Boolean.TRUE);
        component.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent event) {
                handleKey(component, event);
            }
        });
        component.getDocument().addDocumentListener(new DocumentListener() {
            boolean inserting;

            @Override
            public void insertUpdate(DocumentEvent event) {
                afterInsert(component, event);
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
            }

            private void afterInsert(JTextComponent component, DocumentEvent event) {
                if (!GhostCompletion.autoPairEnabled() || inserting) {
                    return;
                }
                if (event.getLength() != 1) {
                    return;
                }
                Document document = event.getDocument();
                int offset = event.getOffset();
                String text;
                try {
                    text = document.getText(offset, 1);
                } catch (BadLocationException ex) {
                    return;
                }
                int openIndex = OPEN.indexOf(text.charAt(0));
                if (openIndex < 0) {
                    return;
                }
                String close = String.valueOf(CLOSE.charAt(openIndex));
                try {
                    if (offset + 1 < document.getLength()
                            && document.getText(offset + 1, 1).equals(close)) {
                        return; // ya cerrado (NetBeans lo hizo)
                    }
                    inserting = true;
                    document.insertString(offset + 1, close, null);
                    component.setCaretPosition(offset + 1);
                } catch (BadLocationException ignored) {
                } finally {
                    inserting = false;
                }
            }
        });
    }

    private static void handleKey(JTextComponent component, KeyEvent event) {
        if (!GhostCompletion.autoPairEnabled() || !component.isEditable()) {
            return;
        }
        char c = event.getKeyChar();
        if (c == KeyEvent.CHAR_UNDEFINED) {
            return;
        }
        int openIndex = OPEN.indexOf(c);
        if (openIndex >= 0 && component.getSelectionStart() != component.getSelectionEnd()) {
            event.consume();
            wrapSelection(component, openIndex);
            return;
        }
        int closeIndex = CLOSE.indexOf(c);
        if (closeIndex >= 0 && nextIs(component, c)) {
            event.consume();
            component.setCaretPosition(component.getCaretPosition() + 1);
        }
    }

    private static boolean nextIs(JTextComponent component, char c) {
        int caret = component.getCaretPosition();
        try {
            return caret < component.getDocument().getLength()
                    && component.getDocument().getText(caret, 1).charAt(0) == c;
        } catch (BadLocationException ex) {
            return false;
        }
    }

    private static void wrapSelection(JTextComponent component, int openIndex) {
        int start = component.getSelectionStart();
        int end = component.getSelectionEnd();
        if (start < 0 || end <= start) {
            return;
        }
        String selected = component.getSelectedText();
        if (selected == null) {
            selected = "";
        }
        String open = String.valueOf(OPEN.charAt(openIndex));
        String close = String.valueOf(CLOSE.charAt(openIndex));
        try {
            component.getDocument().remove(start, end - start);
            String wrapped = open + selected + close;
            component.getDocument().insertString(start, wrapped, null);
            component.setCaretPosition(start + wrapped.length());
        } catch (BadLocationException ignored) {
        }
    }
}
