package org.deepsetk.aierrors;

import java.util.HashMap;
import java.util.Map;

/**
 * Salud y latencia de cada proveedor. Evita repetir un proveedor que acaba de
 * fallar o tardar demasiado (circuit breaker).
 */
final class ProviderStats {

    private static final long BLOCK_MS = 30_000;
    private static final Map<String, Long> AVERAGE = new HashMap<>();
    private static final Map<String, Integer> FAILURES = new HashMap<>();
    private static final Map<String, Long> BLOCKED_UNTIL = new HashMap<>();

    private ProviderStats() {
    }

    static synchronized boolean allowed(String provider) {
        Long blocked = BLOCKED_UNTIL.get(provider);
        return blocked == null || System.currentTimeMillis() >= blocked;
    }

    static synchronized void success(String provider, long latencyMs) {
        Long old = AVERAGE.get(provider);
        long average = old == null ? latencyMs : (old * 3 + latencyMs) / 4;
        AVERAGE.put(provider, average);
        FAILURES.put(provider, 0);
        BLOCKED_UNTIL.remove(provider);
    }

    static synchronized void failure(String provider) {
        int failures = FAILURES.getOrDefault(provider, 0) + 1;
        FAILURES.put(provider, failures);
        if (failures >= 2) {
            BLOCKED_UNTIL.put(provider, System.currentTimeMillis() + BLOCK_MS);
        }
    }

    static synchronized long estimate(String provider, long fallback) {
        return AVERAGE.getOrDefault(provider, fallback);
    }

    static synchronized void reset() {
        AVERAGE.clear();
        FAILURES.clear();
        BLOCKED_UNTIL.clear();
    }
}
