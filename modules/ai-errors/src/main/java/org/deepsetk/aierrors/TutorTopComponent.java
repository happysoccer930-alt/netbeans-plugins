package org.deepsetk.aierrors;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Ventana del tutor: conversacion con Gemini usando el archivo abierto como
 * material de estudio. Incluye acciones rapidas para explicar, revisar,
 * generar preguntas y entender errores.
 */
public final class TutorTopComponent extends TopComponent {

    private static final String[] LEVELS = {"Principiante", "Intermedio", "Avanzado"};
    private static final Color BG = new Color(0x1B1E22);
    private static final Color FG = new Color(0xD6DAE0);
    private static final Color MUTED = new Color(0x8A929C);

    private static TutorTopComponent instance;

    private final JEditorPane chat = new JEditorPane();
    private final JTextArea input = new JTextArea(4, 40);
    private final JComboBox<String> level = new JComboBox<>(LEVELS);
    private final JCheckBox includeFile = new JCheckBox("Incluir archivo actual", true);
    private final JLabel status = new JLabel("Listo");
    private final JLabel modelLabel = new JLabel();
    private final JButton send = new JButton("Enviar");
    private final JButton cancel = new JButton("Cancelar");
    private final JButton newChat = new JButton("Nueva conversación");
    private final JButton copyLast = new JButton("Copiar última");
    private final List<JButton> quickButtons = new ArrayList<>();
    private final List<ChatMessage> messages = new ArrayList<>();

    private volatile Thread worker;
    private boolean busy;
    private long startedAt;
    private String lastAnswer = "";
    private final Timer clock = new Timer(1000, event -> {
        if (busy) {
            long seconds = (System.currentTimeMillis() - startedAt) / 1000;
            status.setText("Pensando… (" + seconds + " s)");
        }
    });

    public static synchronized TutorTopComponent create() {
        if (instance == null) {
            instance = new TutorTopComponent();
        }
        return instance;
    }

    public static TutorTopComponent findInstance() {
        TutorTopComponent found = instance;
        if (found == null) {
            for (TopComponent component : WindowManager.getDefault().getRegistry().getOpened()) {
                if (component instanceof TutorTopComponent candidate) {
                    found = candidate;
                    break;
                }
            }
        }
        return found == null ? create() : found;
    }

    /** Constructor publico sin argumentos: lo necesita el sistema de ventanas. */
    public TutorTopComponent() {
        super();
        instance = this;
        setName("GeminiTutor");
        setDisplayName("Tutor Gemini");
        setToolTipText("Aprende a programar con tu propio código y Gemini");
        setIcon(TutorIcon.image());
        buildUi();
        refreshModel();
        render();
    }

    @Override
    public int getPersistenceType() {
        return PERSISTENCE_ALWAYS;
    }

    @Override
    public String preferredID() {
        return "GeminiTutor";
    }

    private void buildUi() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        header.add(new JLabel("Nivel:"));
        level.setSelectedItem(safeLevel(GeminiSettings.level()));
        level.addActionListener(event -> GeminiSettings.setLevel(String.valueOf(level.getSelectedItem())));
        header.add(level);
        header.add(includeFile);
        modelLabel.setForeground(MUTED);
        header.add(modelLabel);
        JButton config = new JButton("API key / modelo…");
        config.addActionListener(event -> {
            if (GeminiConfigDialog.open(this)) {
                refreshModel();
            }
        });
        header.add(config);

        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addQuick(quick, "Explicar archivo", "explicar");
        addQuick(quick, "Explicar selección", "seleccion");
        addQuick(quick, "Revisar código", "revisar");
        addQuick(quick, "Generar preguntas", "preguntas");
        addQuick(quick, "Dame un ejercicio", "ejercicio");
        addQuick(quick, "Explicar error", "error");

        chat.setEditable(false);
        chat.setContentType("text/html");
        chat.setBackground(BG);
        chat.setForeground(FG);
        chat.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        JScrollPane chatScroll = new JScrollPane(chat);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());

        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        input.setToolTipText("Escribe tu pregunta. Ctrl+Enter para enviar.");
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
                "enviar-pregunta");
        input.getActionMap().put("enviar-pregunta", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                sendMessage(null);
            }
        });
        JScrollPane inputScroll = new JScrollPane(input);
        inputScroll.setPreferredSize(new java.awt.Dimension(200, 86));

        send.addActionListener(event -> sendMessage(null));
        cancel.setEnabled(false);
        cancel.setToolTipText("Interrumpe la consulta actual");
        cancel.addActionListener(event -> {
            Thread running = worker;
            if (running != null) {
                running.interrupt();
                status.setText("Cancelando…");
            }
        });
        newChat.addActionListener(event -> {
            if (busy) {
                return;
            }
            messages.clear();
            lastAnswer = "";
            status.setText("Nueva conversación");
            render();
        });
        copyLast.addActionListener(event -> {
            if (lastAnswer.isEmpty()) {
                status.setText("Todavía no hay una respuesta que copiar");
                return;
            }
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(lastAnswer), null);
            status.setText("Última respuesta copiada");
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.add(send);
        actions.add(cancel);
        actions.add(copyLast);
        actions.add(newChat);

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.add(inputScroll, BorderLayout.CENTER);
        inputRow.add(actions, BorderLayout.EAST);

        status.setForeground(MUTED);

        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(quick, BorderLayout.NORTH);
        south.add(inputRow, BorderLayout.CENTER);
        south.add(status, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);
        add(chatScroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        newChat.setToolTipText("Borra la conversación actual");
        copyLast.setToolTipText("Copia la última respuesta de Gemini");
    }

    private void addQuick(JPanel panel, String label, String kind) {
        JButton button = new JButton(label);
        button.addActionListener(event -> sendMessage(promptFor(kind)));
        quickButtons.add(button);
        panel.add(button);
    }

    private static String promptFor(String kind) {
        return switch (kind) {
            case "explicar" -> "Explícame este archivo completo: qué hace, cómo está organizado "
                    + "y qué conceptos de Java debería repasar. Usa el contexto adjunto.";
            case "seleccion" -> "Explícame el fragmento seleccionado; si no hay selección, "
                    + "explica la línea del cursor. Di qué hace y por qué está escrito así.";
            case "revisar" -> "Revisa este código como un profesor: errores reales, riesgos, "
                    + "malas prácticas y mejoras concretas. Explica el porqué de cada cambio.";
            case "preguntas" -> "Genera 6 preguntas para comprobar si entendí este código. "
                    + "No pongas las respuestas hasta después de una línea '--- RESPUESTAS ---'.";
            case "ejercicio" -> "Propón un ejercicio pequeño y guiado basado en este código. "
                    + "Dame el enunciado, una pista y un criterio para saber si lo hice bien. "
                    + "No des la solución completa al principio.";
            case "error" -> "Explícame el error de la línea actual y cómo corregirlo paso a paso. "
                    + "Si no hay errores, dime qué debería mejorar.";
            default -> "";
        };
    }

    private void sendMessage(String preset) {
        if (busy) {
            return;
        }
        String typed = preset != null ? preset : input.getText().strip();
        if (typed.isEmpty()) {
            status.setText("Escribe una pregunta o usa un botón de la izquierda");
            return;
        }
        if (!GeminiSettings.hasKey()) {
            if (!GeminiConfigDialog.open(this) || !GeminiSettings.hasKey()) {
                status.setText("Falta la API key: Herramientas > Configurar Gemini");
                return;
            }
            refreshModel();
        }

        boolean withContext = includeFile.isSelected() || preset != null;
        CodeContext context = withContext ? CodeContext.captureFull() : null;
        if (withContext && context == null) {
            status.setText("Abre un archivo en el editor para usar su código como contexto");
            return;
        }

        String levelValue = safeLevel(String.valueOf(level.getSelectedItem()));
        GeminiSettings.setLevel(levelValue);

        ChatMessage userMessage = new ChatMessage("user", typed, typed);
        messages.add(userMessage);
        List<ChatMessage> history = new ArrayList<>(messages);
        if (preset == null) {
            input.setText("");
        }
        busy = true;
        setBusy(true);
        startedAt = System.currentTimeMillis();
        clock.start();
        status.setText("Pensando… (0 s)");
        render();

        Thread thread = new Thread(() -> {
            try {
                String key = GeminiSettings.apiKey();
                String model = GeminiSettings.model();
                if (!GeminiSettings.modelChosen()) {
                    model = new GeminiClient().pickDefaultModel(key);
                    GeminiSettings.setModel(model);
                }
                String prompt = typed;
                if (context != null) {
                    String block = contextBlock(context, levelValue);
                    if (!block.isBlank()) {
                        prompt = prompt + "\n\n" + block;
                    }
                }
                List<GeminiClient.Turn> turns = buildTurns(history, prompt);
                String answer = new GeminiClient().generate(key, model, systemPrompt(levelValue), turns);
                SwingUtilities.invokeLater(() -> {
                    messages.add(new ChatMessage("model", answer, answer));
                    lastAnswer = answer;
                    finishBusy();
                    status.setText("Listo");
                    refreshModel();
                    render();
                });
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                SwingUtilities.invokeLater(() -> {
                    messages.remove(userMessage);
                    if (preset == null) {
                        input.setText(typed);
                    }
                    finishBusy();
                    status.setText("Error: " + message);
                    render();
                });
            }
        }, "deepsetk-gemini-tutor");
        worker = thread;
        thread.start();
    }

    /** Limita el historial enviado: ultimos 8 turnos, empezando por el usuario. */
    private static List<GeminiClient.Turn> buildTurns(List<ChatMessage> history, String prompt) {
        int start = Math.max(0, history.size() - 8);
        while (start < history.size() && !"user".equals(history.get(start).role)) {
            start++;
        }
        List<GeminiClient.Turn> turns = new ArrayList<>();
        for (int i = start; i < history.size(); i++) {
            ChatMessage message = history.get(i);
            String text;
            if (i == history.size() - 1 && "user".equals(message.role)) {
                text = prompt;
            } else {
                text = message.sendText;
                if (text.length() > 6_000) {
                    text = "...(recortado)\n" + text.substring(text.length() - 6_000);
                }
            }
            turns.add(new GeminiClient.Turn(message.role, text));
        }
        if (turns.isEmpty()) {
            turns.add(new GeminiClient.Turn("user", prompt));
        }
        return turns;
    }

    private static String contextBlock(CodeContext context, String levelValue) {        StringBuilder sb = new StringBuilder("--- CONTEXTO DEL EDITOR ---\n");
        sb.append("Archivo: ").append(context.fileName)
                .append(" (").append(context.mimeType).append(")\n");
        sb.append("Linea del cursor: ").append(context.caretLine).append('\n');
        sb.append("Nivel indicado: ").append(levelValue).append('\n');
        if (!context.selection.isBlank()) {
            sb.append("\nSeleccion actual:\n").append(context.selection).append('\n');
        }
        String diagnostics = context.diagnostics();
        if (!diagnostics.isBlank()) {
            sb.append("\nDiagnosticos de NetBeans:\n").append(diagnostics);
        }
        sb.append("\nCodigo (numeros de linea reales):\n").append(context.code);
        return sb.toString();
    }

    private static String systemPrompt(String levelValue) {
        return """
                Eres un tutor de programacion en espanol para un estudiante que usa Apache NetBeans y Java.
                Nivel del estudiante: %s.
                Enseña paso a paso y explica el porqué de cada cosa; no des solo la respuesta.
                Da ejemplos pequenos y, cuando tenga sentido, un ejercicio para practicar.
                Cuando revises codigo, separa errores reales de mejoras de estilo.
                No inventes APIs ni nombres que no aparezcan en el codigo.
                Responde en Markdown, con titulos cortos, listas y bloques de codigo.
                """.formatted(levelValue);
    }

    private void finishBusy() {
        busy = false;
        worker = null;
        clock.stop();
        setBusy(false);
    }

    private void setBusy(boolean value) {
        send.setEnabled(!value);
        cancel.setEnabled(value);
        newChat.setEnabled(!value);
        input.setEnabled(!value);
        level.setEnabled(!value);
        includeFile.setEnabled(!value);
        for (JButton button : quickButtons) {
            button.setEnabled(!value);
        }
    }

    private void refreshModel() {
        modelLabel.setText("Modelo: " + GeminiSettings.model().replace("models/", ""));
    }

    private void render() {
        StringBuilder html = new StringBuilder("<html><head><style>");
        html.append("body{background:#1B1E22;color:#D6DAE0;font-family:'Segoe UI';")
                .append("font-size:11px;margin:8px;}");
        html.append(".q{border-left:3px solid #9CC7E8;padding:4px 8px;margin:10px 0;color:#9CC7E8;}");
        html.append(".a{margin:10px 0;}");
        html.append("pre{background:#232930;padding:6px;}");
        html.append("</style></head><body>");

        if (messages.isEmpty()) {
            html.append("<p>Hola. Soy tu tutor. Abre un archivo Java, usa un botón de arriba ")
                    .append("o escríbeme una pregunta. Puedo explicar, revisar y hacerte preguntas ")
                    .append("para practicar.</p>");
        }
        for (ChatMessage message : messages) {
            if ("user".equals(message.role)) {
                html.append("<div class='q'><b>Tú</b><br>").append(escape(message.display)).append("</div>");
            } else {
                html.append("<div class='a'>").append(Markdown.toHtml(message.sendText)).append("</div>");
            }
        }
        if (busy) {
            html.append("<p><i>Gemini está pensando…</i></p>");
        }
        html.append("</body></html>");
        chat.setText(html.toString());
        chat.setCaretPosition(chat.getDocument().getLength());
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    private static String safeLevel(String value) {
        if ("Principiante".equals(value) || "Intermedio".equals(value) || "Avanzado".equals(value)) {
            return value;
        }
        return "Intermedio";
    }

    private record ChatMessage(String role, String display, String sendText) {
    }
}
