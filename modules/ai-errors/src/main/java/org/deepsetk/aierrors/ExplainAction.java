package org.deepsetk.aierrors;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import org.openide.awt.StatusDisplayer;

/**
 * Accion "Explicar error con Gemini": envia el contexto del cursor y los
 * diagnosticos del compilador a Gemini y muestra la explicacion.
 */
public final class ExplainAction extends AbstractAction {

    private static final String SYSTEM_PROMPT = """
            Eres un ingeniero de software senior especializado en Java y en el IDE NetBeans.
            Explicas errores de programacion en espanol neutro, breve y directo.
            Estructura siempre la respuesta asi:
            1. Causa: una o dos frases con la causa raiz.
            2. Correccion: el codigo concreto que hay que cambiar (bloque de codigo).
            3. Por que ocurre: una frase de contexto.
            Si el contexto no alcanza para estar seguro, dilo y pide el dato que falta.
            No inventes APIs ni nombres de metodos que no aparezcan en el codigo.
            """;

    public ExplainAction() {
        putValue(Action.NAME, "Explicar error con IA");
        putValue(Action.SMALL_ICON, UiIcons.bulb());
        putValue(Action.SHORT_DESCRIPTION,
                "Analiza el error de la linea actual. Usa la IA local (Bionic/LM Studio) si esta disponible; si no, Gemini.");
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        CodeContext context = CodeContext.capture();
        if (context == null) {
            status("Abre un archivo en el editor y coloca el cursor en la linea del error");
            return;
        }

        boolean tryLocal = GhostCompletion.localEnabled();
        AnswerDialog dialog = new AnswerDialog(
                "Asistente IA - " + context.fileName,
                context.fileName + "  |  linea " + context.caretLine
                + "  |  " + (tryLocal ? "IA local" : shortModel()));
        dialog.showLoading("Consultando IA...");
        dialog.setVisible(true);

        new Thread(() -> {
            try {
                String answer;
                String provider;
                if (tryLocal && LocalLlmClient.available(GhostCompletion.localUrl())) {
                    String base = GhostCompletion.localUrl();
                    String model = LocalLlmClient.model(base, GhostCompletion.localModel());
                    answer = LocalLlmClient.generateLong(base, model, SYSTEM_PROMPT, userPrompt(context));
                    provider = "IA local - " + model;
                } else {
                    String key = GeminiSettings.apiKey();
                    if (key == null || key.isBlank()) {
                        throw new IllegalStateException(
                                "No hay IA local disponible y tampoco API key de Gemini. "
                                + "Abre Bionic (o Herramientas > Configurar LLM local) o configura Gemini.");
                    }
                    String model = GeminiSettings.model();
                    if (!GeminiSettings.modelChosen()) {
                        model = new GeminiClient().pickDefaultModel(key);
                        GeminiSettings.setModel(model);
                    }
                    answer = new GeminiClient().generate(key, model,
                            SYSTEM_PROMPT, userPrompt(context));
                    provider = "Gemini - " + model.replace("models/", "");
                }
                String finalProvider = provider;
                SwingUtilities.invokeLater(() -> {
                    dialog.setTitle(finalProvider + " - " + context.fileName);
                    dialog.showAnswer(answer);
                });
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                SwingUtilities.invokeLater(() -> dialog.showError(message));
            }
        }, "deepsetk-ai-explain").start();
    }

    private static String userPrompt(CodeContext context) {
        String diagnostics = context.diagnostics();
        StringBuilder sb = new StringBuilder();
        sb.append("Archivo: ").append(context.fileName)
                .append(" (").append(context.mimeType).append(")\n");
        sb.append("Linea del cursor: ").append(context.caretLine).append("\n\n");
        if (diagnostics.isBlank()) {
            sb.append("Diagnosticos de NetBeans: no hay diagnosticos activos en este archivo.\n")
                    .append("Analiza el codigo y di que esta mal en la linea del cursor.\n\n");
        } else {
            sb.append("Diagnosticos de NetBeans (compilador):\n").append(diagnostics).append('\n');
        }
        sb.append("Codigo (numeros de linea reales):\n");
        sb.append(context.code);
        return sb.toString();
    }

    private static String shortModel() {
        return GeminiSettings.model().replace("models/", "");
    }

    private static void status(String message) {
        StatusDisplayer.getDefault().setStatusText(message);
    }
}
