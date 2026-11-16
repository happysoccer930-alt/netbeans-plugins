package org.deepsetk.aierrors;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.CaretEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Utilities;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.awt.StatusDisplayer;
import org.openide.loaders.DataObject;
import org.openide.util.NbPreferences;
import org.openide.windows.WindowManager;

/**
 * Sugerencias fantasma para Java. La prediccion local es inmediata; si hay una
 * API key de Gemini configurada, al pausar se pide una sugerencia contextual.
 * El texto se dibuja con "virtual-text-prepend" y se acepta con Tab.
 */
public final class GhostCompletion {

    private static final Logger LOG = Logger.getLogger(GhostCompletion.class.getName());

    private static final String KEY_BAG = "org.deepsetk.aierrors.ghost.bag";
    private static final String KEY_ATTACHED = "org.deepsetk.aierrors.ghost.attached";
    private static final String GHOST_ATTRIBUTE = "virtual-text-prepend";
    private static final int LOCAL_DELAY_MS = 120;
    private static final int AI_DELAY_MS = 600;
    private static final int LOCAL_AI_DELAY_MS = 1500;
    private static final int AI_COOLDOWN_MS = 1500;
    private static final int LOCAL_AI_COOLDOWN_MS = 4000;
    private static final int INDEX_BEFORE = 12_000;
    private static final int INDEX_AFTER = 4_000;

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    /** Comentarios y cadenas en una sola pasada: no aportan contexto de codigo. */
    private static final Pattern NOISE = Pattern.compile(
            "(?s)/\\*.*?\\*/|//[^\\r\\n]*|\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern EDGE_QUOTES = Pattern.compile("^[\"'`]+|[\"'`]+$");
    private static final Set<String> NOISE_TOKENS = Set.of(
            "TODO", "FIXME", "XXX", "NOI18N", "serialVersionUID", "evt");

    /** Vocabulario base para completar mientras se escribe (estilo IntelliSense). */
    private static final String[] JAVA_WORDS = {
        "String", "System", "StringBuilder", "StringBuffer", "Scanner", "Integer",
        "Double", "Boolean", "Character", "Object", "Exception", "RuntimeException",
        "ArrayList", "LinkedList", "List", "Map", "HashMap", "Set", "HashSet",
        "Arrays", "Collections", "Optional", "Stream", "Thread", "Runnable", "Math",
        "LocalDate", "LocalDateTime", "DateTimeFormatter", "BigDecimal", "BigInteger",
        "Override", "SuppressWarnings", "Serializable", "Comparable", "Comparator",
        "Iterator", "Iterable", "File", "Files", "Path", "Paths", "IOException",
        "SQLException", "Connection", "PreparedStatement", "ResultSet", "DriverManager",
        "JOptionPane", "JFrame", "JPanel", "JButton", "JLabel", "JTextField",
        "JPasswordField", "JTable", "DefaultTableModel"
    };
    private static final Map<String, Integer> JAVA_PRIORITY = javaPriority();

    private static final Map<JTextComponent, State> STATES = new WeakHashMap<>();
    private static volatile Preferences prefs;
    private static final ExecutorService AI_EXECUTOR = Executors.newFixedThreadPool(2,
            daemonFactory("deepsetk-ghost-ai"));
    private static final ExecutorService REQUEST_EXECUTOR = Executors.newFixedThreadPool(4,
            daemonFactory("deepsetk-ghost-request"));
    private static boolean installed;

    private static Map<String, Integer> javaPriority() {
        Map<String, Integer> priority = new HashMap<>(JAVA_WORDS.length * 2);
        for (int i = 0; i < JAVA_WORDS.length; i++) {
            priority.put(JAVA_WORDS[i], JAVA_WORDS.length - i);
        }
        return Map.copyOf(priority);
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + '-' + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private GhostCompletion() {
    }

    static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        LOG.fine("GhostCompletion instalado");
        EditorRegistry.addPropertyChangeListener(event -> {
            JTextComponent component = EditorRegistry.lastFocusedComponent();
            if (component != null) {
                attach(component);
            }
        });
        try {
            WindowManager.getDefault().invokeWhenUIReady(GhostCompletion::attachAll);
            for (int delay : new int[]{1000, 3000, 6000}) {
                Timer timer = new Timer(delay, event -> attachAll());
                timer.setRepeats(false);
                timer.start();
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void attachAll() {
        for (JTextComponent component : EditorRegistry.componentList()) {
            attach(component);
        }
        JTextComponent current = EditorRegistry.lastFocusedComponent();
        if (current != null) {
            attach(current);
        }
    }

    static void toggle() {
        boolean enabled = !enabled();
        prefs().putBoolean("enabled", enabled);
        if (!enabled) {
            for (State state : STATES.values()) {
                state.stop();
            }
        }
        StatusDisplayer.getDefault().setStatusText(enabled
                ? "Sugerencias fantasma activadas: Tab acepta, Escape descarta."
                : "Sugerencias fantasma desactivadas.");
    }

    static boolean enabled() {
        return prefs().getBoolean("enabled", true);
    }

    static boolean aiEnabled() {
        return prefs().getBoolean("ai", true);
    }

    static boolean localEnabled() {
        return prefs().getBoolean("localLlm", true);
    }

    static void setLocalEnabled(boolean value) {
        prefs().putBoolean("localLlm", value);
    }

    static boolean autoPairEnabled() {
        return prefs().getBoolean("autoPair", true);
    }

    static void setAutoPair(boolean value) {
        prefs().putBoolean("autoPair", value);
    }

    static boolean localOnly() {
        return prefs().getBoolean("localOnly", false);
    }

    static String localUrl() {
        return prefs().get("localUrl", LocalLlmClient.DEFAULT_URL);
    }

    static String localModel() {
        return prefs().get("localModel", LocalLlmClient.DEFAULT_MODEL);
    }

    static void setLocalConfig(String url, String model) {
        prefs().put("localUrl", url == null ? "" : url.strip());
        prefs().put("localModel", model == null ? "" : model.strip());
        LocalLlmClient.resetCache();
        SuggestionCache.clear();
        ProviderStats.reset();
    }

    /** Pide una sugerencia inmediata al proveedor de IA para el editor actual. */
    static void suggestNow() {
        JTextComponent component = EditorRegistry.lastFocusedComponent();
        if (component == null) {
            StatusDisplayer.getDefault().setStatusText("No hay un editor Java activo.");
            return;
        }
        attach(component);
        State state = STATES.get(component);
        if (state != null) {
            suggestNow(component, state);
        }
    }

    private static void suggestNow(JTextComponent component, State state) {
        if (state.aiTimer != null) {
            state.aiTimer.stop();
        }
        if (state.aiFuture != null) {
            state.aiFuture.cancel(true);
        }
        clearGhost(state);
        state.lastAiAt = 0;
        StatusDisplayer.getDefault().setStatusText("Consultando el proveedor de IA...");
        requestAi(component, state);
    }

    /** Bag compartido con la capa de highlights que dibuja el texto fantasma. */
    static OffsetsBag bagFor(Document document) {
        Object value = document.getProperty(KEY_BAG);
        if (value instanceof OffsetsBag bag) {
            return bag;
        }
        OffsetsBag bag = new OffsetsBag(document);
        document.putProperty(KEY_BAG, bag);
        return bag;
    }

    private static void attach(JTextComponent component) {
        if (component == null || Boolean.TRUE.equals(component.getClientProperty(KEY_ATTACHED))) {
            return;
        }
        component.putClientProperty(KEY_ATTACHED, Boolean.TRUE);
        LOG.fine(() -> "Ghost attach: " + component.getClass().getName() + " mime=" + component.getDocument().getProperty("mimeType"));
        State state = new State();
        STATES.put(component, state);
        state.localTimer = new Timer(LOCAL_DELAY_MS, event -> predictLocal(component, state));
        state.localTimer.setRepeats(false);
        state.aiTimer = new Timer(AI_DELAY_MS, event -> requestAi(component, state));
        state.aiTimer.setRepeats(false);
        state.documentListener = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                documentChanged(component, state);
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                documentChanged(component, state);
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
            }
        };
        attachDocument(component, state, component.getDocument());
        component.addCaretListener((CaretEvent event) -> {
            if (state.inserting) {
                return;
            }
            state.version++;
            if (state.ghostOffset >= 0 && component.getCaretPosition() != state.ghostOffset) {
                clearGhost(state);
            }
            scheduleLocal(component, state);
            scheduleAi(component, state);
        });
        component.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.isControlDown() && event.isAltDown()
                        && event.getKeyCode() == KeyEvent.VK_SPACE) {
                    suggestNow(component, state);
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_TAB
                        && !event.isControlDown() && !event.isAltDown() && !event.isShiftDown()) {
                    if (accept(component, state)) {
                        event.consume();
                    }
                } else if (event.isControlDown() && !event.isAltDown()
                        && event.getKeyCode() == KeyEvent.VK_RIGHT) {
                    if (acceptWord(component, state)) {
                        event.consume();
                    }
                } else if (event.getKeyCode() == KeyEvent.VK_ESCAPE && state.ghostText != null) {
                    clearGhost(state);
                    event.consume();
                }
            }
        });
        component.addPropertyChangeListener("document", event -> {
            attachDocument(component, state, (Document) event.getNewValue());
            scheduleLocal(component, state);
        });
        AutoPair.install(component);
        scheduleLocal(component, state);
    }

    private static void attachDocument(JTextComponent component, State state, Document document) {
        clearGhost(state);
        if (state.document != null && state.documentListener != null) {
            state.document.removeDocumentListener(state.documentListener);
        }
        state.document = document;
        state.bag = document == null ? null : bagFor(document);
        state.docVersion++;
        state.tokenVersion = -1;
        state.ngramVersion = -1;
        state.java = null;
        if (document != null && state.documentListener != null) {
            document.addDocumentListener(state.documentListener);
        }
    }

    private static void documentChanged(JTextComponent component, State state) {
        if (state.inserting) {
            return;
        }
        state.version++;
        state.docVersion++;
        state.tokenVersion = -1;
        state.ngramVersion = -1;
        clearGhost(state);
        scheduleLocal(component, state);
        scheduleAi(component, state);
    }

    private static void scheduleLocal(JTextComponent component, State state) {
        if (state.localTimer == null) {
            return;
        }
        if (!enabled()) {
            clearGhost(state);
            state.localTimer.stop();
            return;
        }
        state.localTimer.restart();
    }

    private static void scheduleAi(JTextComponent component, State state) {
        if (state.aiTimer == null) {
            return;
        }
        boolean cloud = GeminiSettings.hasKey() || OpenCodeSettings.hasKey();
        if (!enabled() || !aiEnabled() || (!localEnabled() && !localOnly() && !cloud)) {
            state.aiTimer.stop();
            return;
        }
        boolean localFallback = localOnly() || (!cloud && localEnabled());
        int delay = localFallback ? LOCAL_AI_DELAY_MS : AI_DELAY_MS;
        state.aiTimer.setDelay(delay);
        state.aiTimer.setInitialDelay(delay);
        state.aiTimer.restart();
    }

    private static void predictLocal(JTextComponent component, State state) {
        if (!enabled() || state.document == null || state.document != component.getDocument()
                || !component.isEditable() || component.getSelectionStart() != component.getSelectionEnd()) {
            clearGhost(state);
            return;
        }
        try {
            int caret = component.getCaretPosition();
            boolean java = isJavaCached(state, state.document);
            boolean point = java && isCompletionPoint(component, state.document, caret);
            LOG.fine(() -> "Ghost predict caret=" + caret + " java=" + java + " point=" + point);
            if (!java || !point) {
                clearGhost(state);
                return;
            }
            String suggestion = localSuggestion(state, state.document, caret);
            LOG.fine(() -> "Ghost local suggestion=" + suggestion);
            if (suggestion == null || suggestion.isBlank()) {
                clearGhost(state);
            } else {
                showGhost(component, state, suggestion);
            }
        } catch (BadLocationException | RuntimeException ex) {
            LOG.log(Level.FINE, "No se pudo predecir la siguiente palabra", ex);
            clearGhost(state);
        }
    }

    private static String localSuggestion(State state, Document document, int caret)
            throws BadLocationException {
        String prefix = wordPrefix(document, caret);
        String member = memberSuggestion(state, document, caret, prefix);
        if (member != null) {
            return member;
        }
        if (prefix != null) {
            ensureTokens(state, document, caret);
            return completePrefix(prefix, state, document);
        }
        ensureNgrams(state, document, caret);
        String[] lastTwo = previousTokens(document, caret, 2);
        if (lastTwo.length == 0) {
            return null;
        }
        String previous = lastTwo[lastTwo.length - 1];
        Map<String, Integer> counts = Map.of();
        if (lastTwo.length >= 2) {
            counts = state.trigramCounts.getOrDefault(
                    lastTwo[0] + "\u0000" + lastTwo[1], Map.of());
        }
        if (counts.isEmpty()) {
            counts = state.nextCounts.getOrDefault(previous, Map.of());
        }
        String best = pickBest(counts);
        String contextual = contextualSuggestion(previous, state);
        if (best != null && (contextual == null || counts.getOrDefault(best, 0) >= 2)) {
            return best;
        }
        if (contextual != null) {
            return contextual;
        }
        if (best != null) {
            return best;
        }
        return keywordSuggestion(previous);
    }

    /**
     * Si el cursor esta detras de un punto, resuelve el tipo del receptor y
     * devuelve el sufijo del mejor metodo/campo real.
     */
    private static String memberSuggestion(State state, Document document, int caret, String prefix)
            throws BadLocationException {
        int dot = caret - (prefix == null ? 0 : prefix.length()) - 1;
        if (dot <= 0 || dot >= document.getLength()) {
            return null;
        }
        if (document.getText(dot, 1).charAt(0) != '.') {
            return null;
        }
        if (dot + 1 < document.getLength()
                && Character.isDigit(document.getText(dot + 1, 1).charAt(0))) {
            return null; // decimal, no miembro
        }
        char before = document.getText(dot - 1, 1).charAt(0);
        if (!Character.isJavaIdentifierPart(before) && before != ')' && before != ']') {
            return null;
        }
        String p = prefix == null ? "" : prefix;
        String key = state.docVersion + "|" + dot;
        List<String> members;
        if (key.equals(state.memberKey) && state.memberMembers != null) {
            members = state.memberMembers;
        } else {
            ensureTokens(state, document, caret);
            members = RealMembers.members(document, dot, "");
            state.memberKey = key;
            state.memberMembers = members;
        }
        String best = pickMember(members, state, p);
        return best == null ? null : best.substring(p.length());
    }

    private static String pickMember(List<String> members, State state, String prefix) {
        String best = null;
        int bestCount = -1;
        for (String name : members) {
            if (name.length() <= prefix.length() || !name.startsWith(prefix)) {
                continue;
            }
            int count = state.tokenCounts.getOrDefault(name, 0) + Learned.boost(name);
            if (best == null || count > bestCount
                    || count == bestCount && name.length() < best.length()
                    || count == bestCount && name.length() == best.length()
                    && name.compareTo(best) < 0) {
                best = name;
                bestCount = count;
            }
        }
        return best;
    }

    /**
     * Indice ligero para completar la palabra que se esta escribiendo. Solo
     * cuenta tokens: no construye bigramas/trigramas que aqui no se usan.
     */
    private static void ensureTokens(State state, Document document, int caret) throws BadLocationException {
        if (state.tokenVersion == state.docVersion
                && caret >= state.indexStart && caret <= state.indexEnd) {
            return;
        }
        int start = Math.max(0, caret - INDEX_BEFORE);
        int end = Math.min(document.getLength(), caret + INDEX_AFTER);
        String allText = NOISE.matcher(document.getText(start, end - start)).replaceAll(" ");
        Map<String, Integer> tokenCounts = new HashMap<>(1024);
        String bestClass = null;
        int bestClassCount = 0;
        Matcher matcher = TOKEN.matcher(allText);
        while (matcher.find()) {
            String token = matcher.group();
            if (NOISE_TOKENS.contains(token)) {
                continue;
            }
            int count = tokenCounts.merge(token, 1, Integer::sum);
            if (!token.isEmpty() && Character.isUpperCase(token.charAt(0))
                    && count > bestClassCount) {
                bestClass = token;
                bestClassCount = count;
            }
        }
        state.tokenCounts = tokenCounts;
        state.bestClass = bestClass;
        state.tokenVersion = state.docVersion;
        state.ngramVersion = -1;
        state.indexStart = start;
        state.indexEnd = end;
    }

    /**
     * Indice completo para predecir la siguiente palabra. Construye tokens y
     * bigramas/trigramas en una sola pasada.
     */
    private static void ensureNgrams(State state, Document document, int caret) throws BadLocationException {
        if (state.ngramVersion == state.docVersion
                && caret >= state.indexStart && caret <= state.indexEnd) {
            return;
        }
        int start = Math.max(0, caret - INDEX_BEFORE);
        int end = Math.min(document.getLength(), caret + INDEX_AFTER);
        String allText = NOISE.matcher(document.getText(start, end - start)).replaceAll(" ");
        Map<String, Map<String, Integer>> nextCounts = new HashMap<>(512);
        Map<String, Map<String, Integer>> trigramCounts = new HashMap<>(1024);
        Map<String, Integer> tokenCounts = new HashMap<>(1024);
        String bestClass = null;
        int bestClassCount = 0;
        Matcher matcher = TOKEN.matcher(allText);
        String prev2 = null;
        String prev1 = null;
        while (matcher.find()) {
            String token = matcher.group();
            if (NOISE_TOKENS.contains(token)) {
                prev2 = null;
                prev1 = null;
                continue;
            }
            int count = tokenCounts.merge(token, 1, Integer::sum);
            if (!token.isEmpty() && Character.isUpperCase(token.charAt(0))
                    && count > bestClassCount) {
                bestClass = token;
                bestClassCount = count;
            }
            if (prev1 != null) {
                nextCounts.computeIfAbsent(prev1, key -> new HashMap<>())
                        .merge(token, 1, Integer::sum);
            }
            if (prev2 != null && prev1 != null) {
                String key = prev2 + "\u0000" + prev1;
                trigramCounts.computeIfAbsent(key, k -> new HashMap<>())
                        .merge(token, 1, Integer::sum);
            }
            prev2 = prev1;
            prev1 = token;
        }
        state.nextCounts = nextCounts;
        state.trigramCounts = trigramCounts;
        state.tokenCounts = tokenCounts;
        state.bestClass = bestClass;
        state.tokenVersion = state.docVersion;
        state.ngramVersion = state.docVersion;
        state.indexStart = start;
        state.indexEnd = end;
    }

    private static String pickBest(Map<String, Integer> counts) {
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (NOISE_TOKENS.contains(entry.getKey())) {
                continue;
            }
            int count = entry.getValue() + Learned.boost(entry.getKey());
            if (count > bestCount
                    || count == bestCount && best != null
                    && entry.getKey().compareTo(best) < 0) {
                best = entry.getKey();
                bestCount = count;
            }
        }
        return best;
    }

    /** Capa de contexto: tipos, retornos y acceso a System.out. */
    private static String contextualSuggestion(String previous, State state) {
        switch (previous) {
            case "new":
                return state.bestClass != null ? state.bestClass : "String";
            case "return":
                for (String candidate : new String[]{"null", "true", "false", "this"}) {
                    if (state.tokenCounts.containsKey(candidate)) {
                        return candidate;
                    }
                }
                return "null";
            case "System":
                return "out";
            case "out":
                return state.tokenCounts.containsKey("println") ? "println" : "print";
            default:
                return null;
        }
    }

    /**
     * Lee solo los ultimos identificadores cercanos al cursor. Evita
     * tokenizar miles de caracteres en cada pausa.
     */
    private static String[] previousTokens(Document document, int caret, int max)
            throws BadLocationException {
        int start = Math.max(0, caret - 512);
        String chunk = document.getText(start, caret - start);
        chunk = NOISE.matcher(chunk).replaceAll(
                match -> " ".repeat(match.group().length()));
        String[] found = new String[max];
        int count = 0;
        int index = chunk.length();
        while (index > 0 && count < max) {
            while (index > 0 && !Character.isJavaIdentifierPart(chunk.charAt(index - 1))) {
                index--;
            }
            if (index == 0) {
                break;
            }
            int end = index;
            while (index > 0 && Character.isJavaIdentifierPart(chunk.charAt(index - 1))) {
                index--;
            }
            found[count++] = chunk.substring(index, end);
        }
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            result[i] = found[count - 1 - i];
        }
        return result;
    }

    private static String keywordSuggestion(String previous) {
        return switch (previous) {
            case "return" -> "null";
            case "new" -> "String";
            case "if", "while", "for", "switch", "catch" -> "(";
            case "public", "private", "protected", "static", "final" -> "void";
            case "void" -> "main";
            case "String" -> "texto";
            case "int" -> "valor";
            case "boolean" -> "true";
            case "System" -> "out";
            case "out" -> "println";
            case "class" -> "Nombre";
            default -> null;
        };
    }

    /** Prefijo del identificador que se esta escribiendo, o null si el cursor no esta dentro de uno. */
    private static String wordPrefix(Document document, int caret) throws BadLocationException {
        int start = Math.max(0, caret - 128);
        String chunk = document.getText(start, caret - start);
        int i = chunk.length();
        while (i > 0 && Character.isJavaIdentifierPart(chunk.charAt(i - 1))) {
            i--;
        }
        return i == chunk.length() ? null : chunk.substring(i);
    }

    /** Completa el prefijo con tokens del documento, clases reales y vocabulario Java. */
    private static String completePrefix(String prefix, State state, Document document) {
        if (prefix == null || prefix.length() < 2 || isCompleteWord(prefix, state)) {
            return null;
        }
        String best = null;
        int bestCount = -1;
        int bestTier = -1;
        // 1) tokens del documento (contexto, maxima prioridad)
        for (Map.Entry<String, Integer> entry : state.tokenCounts.entrySet()) {
            String token = entry.getKey();
            if (token.length() <= prefix.length() || !token.startsWith(prefix)
                    || NOISE_TOKENS.contains(token)) {
                continue;
            }
            int count = entry.getValue() + Learned.boost(token);
            if (isBetterCompletion(token, count, 2, best, bestCount, bestTier)) {
                best = token;
                bestCount = count;
                bestTier = 2;
            }
        }
        // 2) clases reales del proyecto + JDK
        for (String simpleName : RealCompletion.typeNames(document, prefix)) {
            int count = Math.max(1, state.tokenCounts.getOrDefault(simpleName, 0))
                    + Learned.boost(simpleName);
            if (isBetterCompletion(simpleName, count, 1, best, bestCount, bestTier)) {
                best = simpleName;
                bestCount = count;
                bestTier = 1;
            }
        }
        // 3) vocabulario Java estatico (ultimo recurso)
        for (String token : JAVA_WORDS) {
            if (token.length() <= prefix.length() || !token.startsWith(prefix)) {
                continue;
            }
            int count = Math.max(1, state.tokenCounts.getOrDefault(token, 0))
                    + Learned.boost(token);
            if (isBetterCompletion(token, count, 0, best, bestCount, bestTier)) {
                best = token;
                bestCount = count;
                bestTier = 0;
            }
        }
        return best == null ? null : best.substring(prefix.length());
    }

    private static boolean isCompleteWord(String prefix, State state) {
        return state.tokenCounts.getOrDefault(prefix, 0) >= 2
                || JAVA_PRIORITY.containsKey(prefix);
    }

    private static boolean isBetterCompletion(String candidate, int count, int tier,
            String best, int bestCount, int bestTier) {
        if (best == null || count > bestCount) {
            return true;
        }
        if (count < bestCount) {
            return false;
        }
        if (tier != bestTier) {
            return tier > bestTier;
        }
        int candidatePriority = staticPriority(candidate);
        int bestPriority = staticPriority(best);
        if (candidatePriority != bestPriority) {
            return candidatePriority > bestPriority;
        }
        if (candidate.length() != best.length()) {
            return candidate.length() < best.length();
        }
        return candidate.compareTo(best) < 0;
    }

    /** Las primeras palabras del vocabulario base tienen prioridad en empates. */
    private static int staticPriority(String token) {
        return JAVA_PRIORITY.getOrDefault(token, 0);
    }

    private static boolean insideStringLiteral(String line) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            }
        }
        return inString;
    }

    private static boolean isJavaIdentifier(String value) {
        if (value == null || value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompletionPoint(JTextComponent component, Document document, int caret)
            throws BadLocationException {
        if (caret <= 0 || caret > document.getLength()) {
            return false;
        }
        int lineStart = Utilities.getRowStart(component, caret);
        String line = document.getText(lineStart, caret - lineStart);
        String trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
                || insideStringLiteral(line)) {
            return false;
        }
        char previous = document.getText(caret - 1, 1).charAt(0);
        if (Character.isJavaIdentifierPart(previous)) {
            String prefix = wordPrefix(document, caret);
            return prefix != null && prefix.length() >= 2
                    && !prefix.chars().allMatch(Character::isDigit);
        }
        return true;
    }

    private static boolean isJava(Document document) {
        Object mime = document.getProperty("mimeType");
        if ("text/x-java".equals(mime)) {
            return true;
        }
        Object stream = document.getProperty(Document.StreamDescriptionProperty);
        if (stream instanceof DataObject dataObject) {
            return "text/x-java".equals(dataObject.getPrimaryFile().getMIMEType());
        }
        return false;
    }

    /** Cachea solo el resultado positivo: el MIME puede tardar en resolverse. */
    private static boolean isJavaCached(State state, Document document) {
        if (Boolean.TRUE.equals(state.java)) {
            return true;
        }
        boolean java = isJava(document);
        if (java) {
            state.java = Boolean.TRUE;
        }
        return java;
    }

    private static void showGhost(JTextComponent component, State state, String suggestion) {
        if (suggestion == null) {
            return;
        }
        String text = suggestion.strip();
        if (text.isEmpty() || text.length() > 120 || text.contains("\n")) {
            return;
        }
        Document document = component.getDocument();
        if (document == null || state.document != document) {
            return;
        }
        int caret = component.getCaretPosition();
        if (caret < 0 || caret > document.getLength()) {
            return;
        }
        if (text.equals(state.ghostText) && caret == state.ghostOffset) {
            return;
        }
        OffsetsBag bag = state.bag != null ? state.bag : bagFor(document);
        state.bag = bag;
        bag.clear();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        attributes.addAttribute(GHOST_ATTRIBUTE, text);
        try {
            int end = Math.min(document.getLength(), caret + 1);
            if (end > caret) {
                bag.addHighlight(caret, end, attributes);
            } else {
                bag.addHighlight(caret, caret, attributes);
            }
            state.ghostOffset = caret;
            state.ghostText = text;
            LOG.fine(() -> "Ghost mostrado: " + text + " en " + caret);
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "No se pudo mostrar el texto fantasma", ex);
            clearGhost(state);
        }
    }

    private static boolean accept(JTextComponent component, State state) {
        if (state.ghostText == null || state.ghostOffset < 0
                || component.getCaretPosition() != state.ghostOffset) {
            return false;
        }
        String text = state.ghostText;
        try {
            state.inserting = true;
            Document document = component.getDocument();
            int offset = component.getCaretPosition();
            document.insertString(offset, text, null);
            component.setCaretPosition(Math.min(document.getLength(), offset + text.length()));
            Learned.record(text);
            clearGhost(state);
            return true;
        } catch (BadLocationException ex) {
            LOG.log(Level.FINE, "No se pudo insertar la sugerencia", ex);
            return false;
        } finally {
            state.inserting = false;
        }
    }

    /** Acepta solo la siguiente palabra del ghost y deja el resto. */
    private static boolean acceptWord(JTextComponent component, State state) {
        if (state.ghostText == null || state.ghostOffset < 0
                || component.getCaretPosition() != state.ghostOffset) {
            return false;
        }
        String text = state.ghostText;
        int split = 0;
        while (split < text.length() && text.charAt(split) != ' ') {
            split++;
        }
        if (split < text.length() && text.charAt(split) == ' ') {
            split++;
        }
        if (split <= 0) {
            return false;
        }
        String word = text.substring(0, split);
        String rest = text.substring(split).stripLeading();
        try {
            state.inserting = true;
            Document document = component.getDocument();
            int offset = component.getCaretPosition();
            document.insertString(offset, word, null);
            component.setCaretPosition(Math.min(document.getLength(), offset + word.length()));
            Learned.record(word);
            state.ghostOffset = -1;
            state.ghostText = null;
            if (rest.isBlank()) {
                clearGhost(state);
            } else {
                showGhost(component, state, rest);
            }
            return true;
        } catch (BadLocationException ex) {
            LOG.log(Level.FINE, "No se pudo aceptar la palabra", ex);
            return false;
        } finally {
            state.inserting = false;
        }
    }

    private static void clearGhost(State state) {
        if (state.bag != null && (state.ghostText != null || state.ghostOffset >= 0)) {
            state.bag.clear();
        }
        state.ghostOffset = -1;
        state.ghostText = null;
    }

    private static void requestAi(JTextComponent component, State state) {
        if (!enabled() || !aiEnabled()
                || state.document == null || state.document != component.getDocument()
                || !component.isEditable() || component.getSelectionStart() != component.getSelectionEnd()) {
            return;
        }
        if (!localEnabled() && !localOnly()
                && !GeminiSettings.hasKey() && !OpenCodeSettings.hasKey()) {
            return;
        }
        try {
            Document document = component.getDocument();
            int caret = component.getCaretPosition();
            if (!isJavaCached(state, document) || !isCompletionPoint(component, document, caret)) {
                return;
            }
            String prefix = wordPrefix(document, caret);
            if (state.ghostText != null && !state.ghostText.isBlank()) {
                return;
            }
            long now = System.currentTimeMillis();
            boolean cloud = GeminiSettings.hasKey() || OpenCodeSettings.hasKey();
            boolean localFallback = localOnly() || (!cloud && localEnabled());
            int cooldown = localFallback ? LOCAL_AI_COOLDOWN_MS : AI_COOLDOWN_MS;
            if (now - state.lastAiAt < cooldown) {
                return;
            }
            state.lastAiAt = now;
            if (state.aiFuture != null) {
                state.aiFuture.cancel(true);
            }
            String context = buildContext(component, document, caret);
            long version = state.version;
            state.aiFuture = AI_EXECUTOR.submit(() -> {
                try {
                    String system;
                    if (prefix != null) {
                        system = "Eres un motor de autocompletado de Java. El usuario esta escribiendo "
                                + "el identificador \"" + prefix + "\". Devuelve SOLO el sufijo que falta "
                                + "para completarlo, sin repetir el prefijo, sin comillas y sin explicar. "
                                + "Usa el metodo, los imports y las variables del contexto. "
                                + "No devuelvas TODO, FIXME ni comentarios. "
                                + "Si no estas seguro, devuelve una cadena vacia.";
                    } else {
                        system = "Eres un motor de autocompletado de Java. Devuelve SOLO "
                                + "las siguientes 1 a 8 palabras que deben insertarse en el cursor. "
                                + "Si completas una sentencia, termina en punto y coma. "
                                + "Usa el metodo, los imports y las variables del contexto. "
                                + "No expliques, no uses comillas, no uses bloques de codigo. "
                                + "No devuelvas TODO, FIXME ni comentarios. "
                                + "Si no estas seguro, devuelve una cadena vacia.";
                    }
                    String answer = null;
                    // 1) Gemini primero: es el proveedor mas rapido para ghost text.
                    if (!localOnly() && GeminiSettings.hasKey() && ProviderStats.allowed("gemini")) {
                        String model = GeminiSettings.model();
                        String cacheKey = SuggestionCache.key("gemini", model, context);
                        answer = SuggestionCache.get(cacheKey);
                        if (answer == null || answer.isBlank()) {
                            long started = System.currentTimeMillis();
                            try {
                                answer = callWithTimeout(() -> new GeminiClient().generateShort(
                                        GeminiSettings.apiKey(),
                                        model,
                                        system,
                                        context), 5);
                                ProviderStats.success("gemini", System.currentTimeMillis() - started);
                                SuggestionCache.put(cacheKey, answer);
                            } catch (Exception ex) {
                                if (cancelled(ex)) {
                                    return;
                                }
                                ProviderStats.failure("gemini");
                                LOG.log(Level.FINE, "Gemini no respondio", ex);
                            }
                        }
                    }
                    // 2) OpenCode Zen como respaldo.
                    if ((answer == null || answer.isBlank()) && !localOnly()
                            && OpenCodeSettings.hasKey() && ProviderStats.allowed("opencode")) {
                        String ocUrl = OpenCodeSettings.url();
                        String ocModel = OpenCodeSettings.model();
                        String cacheKey = SuggestionCache.key("opencode|" + ocUrl, ocModel, context);
                        answer = SuggestionCache.get(cacheKey);
                        if (answer == null || answer.isBlank()) {
                            long started = System.currentTimeMillis();
                            try {
                                answer = callWithTimeout(() -> OpenCodeClient.generateShort(
                                        ocUrl,
                                        OpenCodeSettings.apiKey(),
                                        ocModel,
                                        system,
                                        context), 6);
                                ProviderStats.success("opencode", System.currentTimeMillis() - started);
                                SuggestionCache.put(cacheKey, answer);
                            } catch (Exception ex) {
                                if (cancelled(ex)) {
                                    return;
                                }
                                ProviderStats.failure("opencode");
                                LOG.log(Level.FINE, "OpenCode Zen no respondio", ex);
                            }
                        }
                    }
                    // 3) LLM local solo como ultimo recurso (o si localOnly esta activo).
                    if ((answer == null || answer.isBlank()) && (localEnabled() || localOnly())
                            && ProviderStats.allowed("local")) {
                        String base = localUrl();
                        if (LocalLlmClient.available(base)) {
                            String model = LocalLlmClient.model(base, localModel());
                            String cacheKey = SuggestionCache.key(base, model, context);
                            answer = SuggestionCache.get(cacheKey);
                            if (answer == null || answer.isBlank()) {
                                long started = System.currentTimeMillis();
                                try {
                                    answer = callWithTimeout(() -> LocalLlmClient.generateShort(base, model, system, context), 4);
                                    ProviderStats.success("local", System.currentTimeMillis() - started);
                                    SuggestionCache.put(cacheKey, answer);
                                } catch (Exception ex) {
                                    if (cancelled(ex)) {
                                        return;
                                    }
                                    ProviderStats.failure("local");
                                    LOG.log(Level.FINE, "El LLM local no respondio", ex);
                                }
                            }
                        }
                    }
                    String suggestion = cleanSuggestion(answer);
                    if (prefix != null) {
                        suggestion = suffixFor(prefix, suggestion);
                    }
                    if (suggestion.isEmpty()) {
                        return;
                    }
                    String finalSuggestion = suggestion;
                    SwingUtilities.invokeLater(() -> {
                        if (state.version != version || component.getDocument() != document
                                || component.getCaretPosition() != caret || !enabled()) {
                            return;
                        }
                        showGhost(component, state, finalSuggestion);
                    });
                } catch (Exception ex) {
                    LOG.log(Level.FINE, "La sugerencia del LLM no esta disponible", ex);
                }
            });
        } catch (BadLocationException | RuntimeException ex) {
            LOG.log(Level.FINE, "No se pudo preparar la sugerencia de Gemini", ex);
        }
    }

    private static String callWithTimeout(Callable<String> task, int seconds) throws Exception {
        Future<String> future = REQUEST_EXECUTOR.submit(task);
        try {
            return future.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw ex;
        } catch (Exception ex) {
            future.cancel(true);
            throw ex;
        }
    }

    /** Detecta cancelaciones para no penalizar al proveedor con falsos fallos. */
    private static boolean cancelled(Exception ex) {
        if (ex instanceof CancellationException || ex instanceof InterruptedException
                || Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
            return true;
        }
        return false;
    }

    private static String buildContext(JTextComponent component, Document document, int caret)
            throws BadLocationException {
        int start = Math.max(0, caret - 1800);
        int end = Math.min(document.getLength(), caret + 500);
        String around = document.getText(start, end - start);
        String marked = around.substring(0, caret - start) + "<|CURSOR|>"
                + around.substring(caret - start);
        int lineStart = Utilities.getRowStart(component, caret);
        int lineEnd = Utilities.getRowEnd(component, caret);
        String line = document.getText(lineStart, lineEnd - lineStart);
        String imports = importContext(document);
        return "Archivo: " + fileName(document) + "\n"
                + (imports.isEmpty() ? "" : "Paquete/imports:\n" + imports + "\n\n")
                + "Linea actual:\n" + line + "\n\n"
                + "Contexto (el cursor esta en <|CURSOR|>):\n" + marked;
    }

    private static String fileName(Document document) {
        Object stream = document.getProperty(Document.StreamDescriptionProperty);
        if (stream instanceof DataObject dataObject) {
            return dataObject.getPrimaryFile().getNameExt();
        }
        return "editor";
    }

    private static String importContext(Document document) throws BadLocationException {
        int length = Math.min(document.getLength(), 2000);
        String text = document.getText(0, length);
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
                sb.append(trimmed).append('\n');
                if (sb.length() > 800) {
                    break;
                }
            }
        }
        return sb.toString().strip();
    }

    /** Normaliza la respuesta de la IA cuando se esta completando una palabra. */
    private static String suffixFor(String prefix, String suggestion) {
        if (suggestion == null || suggestion.isEmpty()) {
            return "";
        }
        String text = suggestion.strip();
        if (text.startsWith(prefix)) {
            text = text.substring(prefix.length()).strip();
        }
        if (text.isEmpty()) {
            return "";
        }
        return isJavaIdentifier(prefix + text) ? text : "";
    }

    private static String cleanSuggestion(String answer) {
        if (answer == null) {
            return "";
        }
        String text = answer.strip();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                text = text.substring(firstLine + 1, lastFence).strip();
            }
        }
        int newline = text.indexOf('\n');
        if (newline >= 0) {
            text = text.substring(0, newline).strip();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1).strip();
            int comma = text.indexOf(',');
            if (comma >= 0) {
                text = text.substring(0, comma).strip();
            }
        }
        text = EDGE_QUOTES.matcher(text).replaceAll("").strip();
        if (text.regionMatches(true, 0, "sugerencia:", 0, 11)) {
            text = text.substring(11).strip();
        }
        if (text.startsWith("+") || text.startsWith("-")) {
            text = text.substring(1).strip();
        }
        text = text.replace("<|CURSOR|>", "").strip();
        if (text.isEmpty() || text.equalsIgnoreCase("vacio")
                || text.equalsIgnoreCase("empty") || text.equalsIgnoreCase("ninguna")) {
            return "";
        }
        if (text.regionMatches(true, 0, "TODO", 0, 4)
                || text.regionMatches(true, 0, "FIXME", 0, 5)
                || text.regionMatches(true, 0, "XXX", 0, 3)) {
            return "";
        }
        String[] words = text.split("\\s+");
        if (words.length > 8) {
            text = String.join(" ", Arrays.copyOf(words, 8));
        }
        if (text.length() > 120 || text.contains("\n") || text.contains("Â¡") || text.contains("Â¿")) {
            return "";
        }
        return text;
    }

    private static Preferences prefs() {
        Preferences value = prefs;
        if (value == null) {
            synchronized (GhostCompletion.class) {
                value = prefs;
                if (value == null) {
                    value = NbPreferences.forModule(GhostCompletion.class);
                    prefs = value;
                }
            }
        }
        return value;
    }

    private static final class State {

        private Document document;
        private OffsetsBag bag;
        private javax.swing.event.DocumentListener documentListener;
        private Timer localTimer;
        private Timer aiTimer;
        private Future<?> aiFuture;
        private int ghostOffset = -1;
        private String ghostText;
        private long version;
        private long docVersion;
        private long tokenVersion = -1;
        private long ngramVersion = -1;
        private int indexStart;
        private int indexEnd;
        private Map<String, Map<String, Integer>> nextCounts = Map.of();
        private Map<String, Map<String, Integer>> trigramCounts = Map.of();
        private Map<String, Integer> tokenCounts = Map.of();
        private String bestClass;
        private Boolean java;
        private String memberKey;
        private java.util.List<String> memberMembers;
        private long lastAiAt;
        private boolean inserting;

        private void stop() {
            if (localTimer != null) {
                localTimer.stop();
            }
            if (aiTimer != null) {
                aiTimer.stop();
            }
            if (aiFuture != null) {
                aiFuture.cancel(true);
            }
            clearGhost(this);
        }
    }
}



