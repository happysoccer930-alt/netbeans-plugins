package org.deepsetk.aierrors;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente minimo para un servidor local compatible con OpenAI: LM Studio,
 * Ollama o llama.cpp. No usa API key ni Internet.
 *
 * Si el endpoint configurado no responde, autodetecta LM Studio (1234),
 * Ollama (11434) o llama.cpp (8080).
 */
final class LocalLlmClient {

    static final String DEFAULT_URL = "http://127.0.0.1:1234/v1";
    static final String DEFAULT_MODEL = "qwen2.5-coder-1.5b-instruct";

    private static final String[] CANDIDATE_URLS = {
        "http://127.0.0.1:1235/v1", // Bionic / LM Studio (puerto alternativo)
        "http://127.0.0.1:1234/v1", // LM Studio
        "http://127.0.0.1:11434/v1", // Ollama
        "http://127.0.0.1:8080/v1" // llama.cpp / koboldcpp
    };

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final Object CACHE_LOCK = new Object();
    private static final long AVAILABLE_TTL_MS = 30_000;
    private static final long UNAVAILABLE_TTL_MS = 120_000;
    private static String cachedBase; // base configurada (normalizada)
    private static String cachedUrl; // url que realmente responde
    private static List<String> cachedModels = List.of();
    private static long cachedAt;
    private static boolean cachedAvailable;

    private LocalLlmClient() {
    }

    static void resetCache() {
        synchronized (CACHE_LOCK) {
            cachedBase = null;
            cachedUrl = null;
            cachedModels = List.of();
            cachedAt = 0;
            cachedAvailable = false;
        }
    }

    static boolean available(String base) {
        return !detect(base).isEmpty();
    }

    static String model(String base, String preferred) {
        String normalized = normalize(base);
        detect(base);
        List<String> models;
        synchronized (CACHE_LOCK) {
            models = normalized.equals(cachedBase) ? cachedModels : List.of();
        }
        if (preferred != null && !preferred.isBlank() && models.contains(preferred)) {
            return preferred;
        }
        String smallest = null;
        double smallestSize = Double.MAX_VALUE;
        for (String candidate : models) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (!lower.contains("coder") || lower.contains("embedding")) {
                continue;
            }
            double size = sizeOf(lower);
            if (size > 0 && size < smallestSize) {
                smallestSize = size;
                smallest = candidate;
            }
        }
        if (smallest != null) {
            return smallest;
        }
        if (models.isEmpty()) {
            return preferred == null || preferred.isBlank() ? DEFAULT_MODEL : preferred;
        }
        return models.get(0);
    }

    /** Elige el modelo mas grande disponible: mejor calidad para explicaciones. */
    static String modelForChat(String base, String preferred) {
        String normalized = normalize(base);
        detect(base);
        List<String> models;
        synchronized (CACHE_LOCK) {
            models = normalized.equals(cachedBase) ? cachedModels : List.of();
        }
        if (preferred != null && !preferred.isBlank() && models.contains(preferred)) {
            return preferred;
        }
        String best = null;
        double bestSize = -1;
        for (String candidate : models) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.contains("embed")) {
                continue;
            }
            double size = sizeOf(lower);
            if (size > bestSize) {
                bestSize = size;
                best = candidate;
            }
        }
        if (best != null) {
            return best;
        }
        return model(base, preferred);
    }

    /** Tamano aproximado en miles de millones deducido del nombre (1.5b, 3b, 7b...). */
    private static double sizeOf(String id) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)b(?:[-_.]|$|-)").matcher(id);
        double value = 0;
        while (matcher.find()) {
            try {
                value = Math.max(value, Double.parseDouble(matcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return value;
    }

    static String generate(String base, String model, String system, String user)
            throws LocalException {
        return generate(base, model, system, user, Duration.ofSeconds(60), 400);
    }

    /** Respuesta larga (explicaciones), sin limite tan corto como el ghost text. */
    static String generateLong(String base, String model, String system, String user)
            throws LocalException {
        return generate(base, model, system, user, Duration.ofSeconds(120), 800);
    }

    /** Version corta para ghost text: no bloquear el hilo mas de lo necesario. */
    static String generateShort(String base, String model, String system, String user)
            throws LocalException {
        return generate(base, model, system, user, Duration.ofSeconds(4), 24);
    }

    private static String generate(String base, String model, String system, String user,
            Duration timeout, int maxTokens) throws LocalException {
        String workingUrl = resolveWorkingUrl(base);
        String body = buildBody(model, system, user, maxTokens);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(workingUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> root = Json.obj(Json.parse(response.body()));
            if (response.statusCode() != 200) {
                throw new LocalException("HTTP " + response.statusCode() + ": " + errorMessage(root));
            }
            for (Object choice : Json.arr(root.get("choices"))) {
                Map<String, Object> message = Json.obj(Json.obj(choice).get("message"));
                String text = Json.str(message.get("content"), "");
                if (!text.isBlank()) {
                    return text;
                }
            }
            throw new LocalException("El servidor local no devolvio texto.");
        } catch (IOException ex) {
            throw new LocalException("No se pudo conectar con el servidor local: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LocalException("Consulta local interrumpida.");
        } catch (IllegalArgumentException ex) {
            throw new LocalException("Respuesta inesperada del servidor local: " + ex.getMessage());
        }
    }

    /** Prueba endpoints y cachea el primero que devuelve modelos. */
    private static List<String> detect(String base) {
        String normalized = normalize(base);
        synchronized (CACHE_LOCK) {
            long ttl = cachedAvailable ? AVAILABLE_TTL_MS : UNAVAILABLE_TTL_MS;
            if (normalized.equals(cachedBase) && System.currentTimeMillis() - cachedAt < ttl) {
                return cachedModels;
            }
        }
        List<String> models = List.of();
        String working = normalized;
        for (String url : candidates(normalized)) {
            try {
                List<String> found = fetchModels(url);
                if (!found.isEmpty()) {
                    models = found;
                    working = url;
                    break;
                }
            } catch (RuntimeException ignored) {
            }
        }
        synchronized (CACHE_LOCK) {
            cachedBase = normalized;
            cachedUrl = working;
            cachedModels = models;
            cachedAvailable = !models.isEmpty();
            cachedAt = System.currentTimeMillis();
        }
        return models;
    }

    private static String resolveWorkingUrl(String base) {
        String normalized = normalize(base);
        synchronized (CACHE_LOCK) {
            if (normalized.equals(cachedBase) && cachedUrl != null
                    && System.currentTimeMillis() - cachedAt < AVAILABLE_TTL_MS) {
                return cachedUrl;
            }
        }
        detect(base);
        synchronized (CACHE_LOCK) {
            return normalized.equals(cachedBase) && cachedUrl != null ? cachedUrl : normalized;
        }
    }

    private static List<String> candidates(String normalized) {
        List<String> result = new ArrayList<>(CANDIDATE_URLS.length + 1);
        result.add(normalized);
        for (String candidate : CANDIDATE_URLS) {
            if (!candidate.equals(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }

    static String buildBody(String model, String system, String user, int maxTokens) {
        return "{\"model\":\"" + escape(model) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + escape(system) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + escape(user) + "\"}"
                + "],"
                + "\"temperature\":0.2,"
                + "\"max_tokens\":" + maxTokens + ","
                + "\"stream\":false}";
    }

    private static List<String> fetchModels(String base) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/models"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return List.of();
            }
            Map<String, Object> root = Json.obj(Json.parse(response.body()));
            List<String> ids = new ArrayList<>();
            for (Object item : Json.arr(root.get("data"))) {
                String id = Json.str(Json.obj(item).get("id"), "");
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
            return ids;
        } catch (IOException ex) {
            return List.of();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    private static String normalize(String base) {
        String value = base == null || base.isBlank() ? DEFAULT_URL : base.strip();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String errorMessage(Map<String, Object> root) {
        Object error = root.get("error");
        if (error instanceof Map<?, ?>) {
            return Json.str(Json.obj(error).get("message"), "error desconocido");
        }
        return Json.str(error, "error desconocido");
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    static final class LocalException extends Exception {

        LocalException(String message) {
            super(message);
        }
    }
}
