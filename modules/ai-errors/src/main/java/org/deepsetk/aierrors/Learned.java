package org.deepsetk.aierrors;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aprende de las sugerencias que el usuario acepta. Guarda un contador por
 * token en un archivo del directorio de usuario de NetBeans y lo usa para
 * priorizar lo que mas aceptas.
 */
final class Learned {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final int MAX_ENTRIES = 3000;

    private static final Map<String, Integer> COUNTS = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();
    private static volatile boolean loaded;

    private Learned() {
    }

    static int boost(String token) {
        if (!loaded) {
            load();
        }
        return token == null ? 0 : COUNTS.getOrDefault(token, 0);
    }

    static void record(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!loaded) {
            load();
        }
        boolean changed = false;
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 2 || NOISE.contains(token)) {
                continue;
            }
            COUNTS.merge(token, 1, Integer::sum);
            changed = true;
        }
        if (changed) {
            synchronized (LOCK) {
                trim();
                save();
            }
        }
    }

    private static final java.util.Set<String> NOISE = java.util.Set.of(
            "TODO", "FIXME", "XXX", "NOI18N", "serialVersionUID", "evt");

    private static void load() {
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            loaded = true;
            File file = file();
            if (!file.isFile()) {
                return;
            }
            try {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    int eq = line.lastIndexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String token = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    if (token.isEmpty()) {
                        continue;
                    }
                    try {
                        COUNTS.put(token, Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static void save() {
        try {
            File file = file();
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            List<String> lines = new ArrayList<>(COUNTS.size());
            for (Map.Entry<String, Integer> entry : COUNTS.entrySet()) {
                lines.add(entry.getKey() + "=" + entry.getValue());
            }
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void trim() {
        if (COUNTS.size() <= MAX_ENTRIES) {
            return;
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(COUNTS.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        int remove = COUNTS.size() - MAX_ENTRIES;
        for (int i = 0; i < remove && i < entries.size(); i++) {
            if (entries.get(i).getValue() <= 1) {
                COUNTS.remove(entries.get(i).getKey());
            }
        }
        if (COUNTS.size() > MAX_ENTRIES) {
            COUNTS.clear();
        }
    }

    private static File file() {
        String user = System.getProperty("netbeans.user");
        File dir = user == null || user.isBlank()
                ? new File(System.getProperty("user.home", "."), ".deepsetk")
                : new File(user, "config");
        return new File(dir, "deepsetk-learned.properties");
    }
}
