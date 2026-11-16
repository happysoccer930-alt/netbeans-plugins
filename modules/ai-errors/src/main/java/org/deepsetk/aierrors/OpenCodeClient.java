package org.deepsetk.aierrors;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Cliente OpenAI-compatible para los modelos gratuitos de OpenCode Zen. */
final class OpenCodeClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private OpenCodeClient() {
    }

    static String generate(String base, String apiKey, String model, String system, String user)
            throws OpenCodeException {
        return generate(base, apiKey, model, system, user, Duration.ofSeconds(90));
    }

    /** Version corta para ghost text: 1-4 palabras y timeout agresivo. */
    static String generateShort(String base, String apiKey, String model, String system, String user)
            throws OpenCodeException {
        return generate(base, apiKey, model, system, user, Duration.ofSeconds(8));
    }

    private static String generate(String base, String apiKey, String model, String system,
            String user, Duration timeout) throws OpenCodeException {
        String normalized = normalize(base);
        String body = buildBody(model, system, user);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalized + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> root = Json.obj(Json.parse(response.body()));
            if (response.statusCode() != 200) {
                throw new OpenCodeException("HTTP " + response.statusCode() + ": " + errorMessage(root));
            }
            for (Object choice : Json.arr(root.get("choices"))) {
                Map<String, Object> message = Json.obj(Json.obj(choice).get("message"));
                String text = Json.str(message.get("content"), "");
                if (!text.isBlank()) {
                    return text;
                }
            }
            throw new OpenCodeException("OpenCode no devolvio texto.");
        } catch (IOException ex) {
            throw new OpenCodeException("No se pudo conectar con OpenCode: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OpenCodeException("Consulta interrumpida.");
        } catch (IllegalArgumentException ex) {
            throw new OpenCodeException("Respuesta inesperada de OpenCode: " + ex.getMessage());
        }
    }

    static String buildBody(String model, String system, String user) {
        return "{\"model\":\"" + escape(model) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + escape(system) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + escape(user) + "\"}"
                + "],"
                + "\"temperature\":0.2,"
                + "\"max_tokens\":40,"
                + "\"stream\":false}";
    }

    private static String normalize(String base) {
        String value = base == null || base.isBlank() ? OpenCodeSettings.DEFAULT_URL : base.strip();
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

    static final class OpenCodeException extends Exception {

        OpenCodeException(String message) {
            super(message);
        }
    }
}
