package org.deepsetk.aierrors;

import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/** Preferencias del proveedor OpenCode Zen (modelos gratuitos). */
final class OpenCodeSettings {

    static final String DEFAULT_URL = "https://opencode.ai/zen/v1";
    static final String DEFAULT_MODEL = "deepseek-v4.1-flash-free";

    private static final String KEY_API = "opencodeApiKey";
    private static final String KEY_MODEL = "opencodeModel";
    private static final String KEY_URL = "opencodeUrl";

    private static final Preferences PREFS = NbPreferences.forModule(OpenCodeSettings.class);
    private static String cachedEncrypted;
    private static String cachedKey;

    private OpenCodeSettings() {
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
        return prefs().get(KEY_MODEL, DEFAULT_MODEL);
    }

    static String url() {
        return prefs().get(KEY_URL, DEFAULT_URL);
    }

    static void save(String url, String model, String apiKey) {
        prefs().put(KEY_URL, url == null || url.isBlank() ? DEFAULT_URL : url.strip());
        prefs().put(KEY_MODEL, model == null || model.isBlank() ? DEFAULT_MODEL : model.strip());
        setApiKey(apiKey);
    }
}
