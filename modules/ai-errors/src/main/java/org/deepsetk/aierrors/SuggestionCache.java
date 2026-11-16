package org.deepsetk.aierrors;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cache LRU de sugerencias del LLM para contextos repetidos. */
final class SuggestionCache {

    private static final int MAX_ENTRIES = 256;

    private static final Map<String, String> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private SuggestionCache() {
    }

    static synchronized String get(String key) {
        return CACHE.get(key);
    }

    static synchronized void put(String key, String value) {
        if (key != null && value != null && !value.isBlank()) {
            CACHE.put(key, value);
        }
    }

    static synchronized void clear() {
        CACHE.clear();
    }

    static String key(String base, String model, String context) {
        return base + "|" + model + "|" + context.length() + "|"
                + Integer.toHexString(context.hashCode());
    }
}
