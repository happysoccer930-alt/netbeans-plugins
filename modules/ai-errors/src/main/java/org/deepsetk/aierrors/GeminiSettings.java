package org.deepsetk.aierrors;

import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * Preferencias del modulo. La key se guarda ofuscada (ver {@link SecretCodec}).
 */
public final class GeminiSettings {

    private static final String KEY_API = "apiKey";
    private static final String KEY_MODEL = "model";
    private static final String KEY_LEVEL = "tutorLevel";

    /** Solo se usa si el usuario nunca ha elegido modelo y la lista falla. */
    static final String FALLBACK_MODEL = "models/gemini-2.5-flash";

    private static final Preferences PREFS = NbPreferences.forModule(GeminiSettings.class);
    private static String cachedEncrypted;
    private static String cachedKey;

    private GeminiSettings() {
    }

    static Preferences prefs() {
        return PREFS;
    }

    static synchronized String apiKey() {
        String encrypted = PREFS.get(KEY_API, "");
        if (encrypted.equals(cachedEncrypted)) {
            return cachedKey;
        }
        cachedEncrypted = encrypted;
        cachedKey = SecretCodec.decrypt(encrypted);
        return cachedKey;
    }

    static synchronized void setApiKey(String key) {
        if (key == null || key.isBlank()) {
            PREFS.remove(KEY_API);
        } else {
            PREFS.put(KEY_API, SecretCodec.encrypt(key.trim()));
        }
        cachedEncrypted = null;
        cachedKey = null;
    }

    static boolean hasKey() {
        String key = apiKey();
        return key != null && !key.isBlank();
    }

    static String model() {
        return prefs().get(KEY_MODEL, FALLBACK_MODEL);
    }

    static boolean modelChosen() {
        return prefs().get(KEY_MODEL, "").length() > 0;
    }

    static void setModel(String model) {
        if (model == null || model.isBlank()) {
            prefs().remove(KEY_MODEL);
        } else {
            prefs().put(KEY_MODEL, model);
        }
    }

    static String level() {
        return prefs().get(KEY_LEVEL, "Intermedio");
    }

    static void setLevel(String level) {
        if (level != null && !level.isBlank()) {
            prefs().put(KEY_LEVEL, level);
        }
    }
}
