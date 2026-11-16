package org.deepsetk.aierrors;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente HTTP minimo para la API REST de Gemini (v1beta).
 */
final class GeminiClient {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    record Model(String name, String displayName) {
        @Override
        public String toString() {
            return displayName + "  (" + name.replace("models/", "") + ")";
        }
    }

    /** Un turno de la conversacion. role = "user" o "model". */
    record Turn(String role, String text) {
    }

    static final class GeminiException extends Exception {
        GeminiException(String message) {
            super(message);
        }
    }

    List<Model> listModels(String apiKey) throws GeminiException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    BASE + "/models?pageSize=200&key=" + url(apiKey)))
                    .timeout(Duration.ofSeconds(45))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> root = Json.obj(Json.parse(response.body()));
            if (response.statusCode() != 200) {
                throw new GeminiException(errorMessage(root, response.statusCode()));
            }
            List<Model> models = new ArrayList<>();
            for (Object item : Json.arr(root.get("models"))) {
                Map<String, Object> model = Json.obj(item);
                boolean generates = false;
                for (Object method : Json.arr(model.get("supportedGenerationMethods"))) {
                    if ("generateContent".equals(Json.str(method, ""))) {
                        generates = true;
                    }
                }
                String name = Json.str(model.get("name"), "");
                if (!generates || name.isEmpty()) {
                    continue;
                }
                models.add(new Model(name, Json.str(model.get("displayName"), name)));
            }
            models.sort(Comparator.comparing(Model::name));
            if (models.isEmpty()) {
                throw new GeminiException("La key es valida pero no tiene modelos con generateContent.");
            }
            return models;
        } catch (IOException ex) {
            throw new GeminiException("No se pudo conectar con Gemini: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GeminiException("Consulta interrumpida.");
        } catch (IllegalArgumentException ex) {
            throw new GeminiException("Respuesta inesperada de Gemini: " + ex.getMessage());
        }
    }

    /** Devuelve el primer modelo "flash" (rapido y barato) de la cuenta. */
    String pickDefaultModel(String apiKey) throws GeminiException {
        List<Model> models = listModels(apiKey);
        return models.stream()
                .map(Model::name)
                .filter(n -> n.toLowerCase(Locale.ROOT).contains("flash"))
                .filter(n -> !n.toLowerCase(Locale.ROOT).contains("thinking"))
                .findFirst()
                .orElseGet(() -> models.get(0).name());
    }

    String generate(String apiKey, String model, String system, String userText) throws GeminiException {
        return generate(apiKey, model, system, List.of(new Turn("user", userText)));
    }

    /** Version corta para ghost text: 1-4 palabras y timeout agresivo. */
    String generateShort(String apiKey, String model, String system, String userText) throws GeminiException {
        return generate(apiKey, model, system,
                List.of(new Turn("user", userText)), 40, Duration.ofSeconds(8));
    }

    /** Conversacion multi-turno para el tutor. */
    String generate(String apiKey, String model, String system, List<Turn> turns) throws GeminiException {
        return generate(apiKey, model, system, turns, 2048, Duration.ofSeconds(90));
    }

    private String generate(String apiKey, String model, String system, List<Turn> turns,
            int maxTokens, Duration timeout) throws GeminiException {
        if (turns == null || turns.isEmpty()) {
            throw new GeminiException("No hay contenido para enviar.");
        }
        String body = buildBody(system, turns, maxTokens);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    BASE + "/" + model + ":generateContent?key=" + url(apiKey)))
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> root = Json.obj(Json.parse(response.body()));
            if (response.statusCode() != 200) {
                throw new GeminiException(errorMessage(root, response.statusCode()));
            }
            StringBuilder text = new StringBuilder();
            String finish = "";
            for (Object cand : Json.arr(root.get("candidates"))) {
                Map<String, Object> candidate = Json.obj(cand);
                if (finish.isEmpty()) {
                    finish = Json.str(candidate.get("finishReason"), "");
                }
                Map<String, Object> content = Json.obj(candidate.get("content"));
                for (Object part : Json.arr(content.get("parts"))) {
                    text.append(Json.str(Json.obj(part).get("text"), ""));
                }
            }
            String answer = text.toString().trim();
            if (answer.isEmpty()) {
                Map<String, Object> feedback = Json.obj(root.get("promptFeedback"));
                String blocked = Json.str(feedback.get("blockReason"), "");
                throw new GeminiException(blocked.isEmpty()
                        ? "Gemini no devolvio texto (respuesta vacia)."
                        : "Gemini bloqueo la peticion: " + blocked);
            }
            if ("MAX_TOKENS".equals(finish)) {
                answer += "\n\n_(Respuesta recortada por el limite de tokens. Pide que continue.)_";
            }
            return answer;
        } catch (IOException ex) {
            throw new GeminiException("No se pudo conectar con Gemini: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GeminiException("Consulta interrumpida.");
        } catch (IllegalArgumentException ex) {
            throw new GeminiException("Respuesta inesperada de Gemini: " + ex.getMessage());
        }
    }

    /** Construye el JSON de la peticion (visible para las pruebas offline). */
    static String buildBody(String system, List<Turn> turns) {
        return buildBody(system, turns, 2048);
    }

    /** Construye el JSON de la peticion con un limite de tokens concreto. */
    static String buildBody(String system, List<Turn> turns, int maxTokens) {
        StringBuilder contents = new StringBuilder("[");
        for (int i = 0; i < turns.size(); i++) {
            Turn turn = turns.get(i);
            if (i > 0) {
                contents.append(',');
            }
            String role = "model".equals(turn.role()) ? "model" : "user";
            contents.append("{\"role\":\"").append(role).append("\",\"parts\":[{\"text\":\"")
                    .append(escape(turn.text())).append("\"}]}");
        }
        contents.append(']');
        return "{\"systemInstruction\":{\"parts\":[{\"text\":\"" + escape(system) + "\"}]},"
                + "\"contents\":" + contents + ","
                + "\"generationConfig\":{\"temperature\":0.35,\"maxOutputTokens\":" + maxTokens + "}}";
    }

    private static String errorMessage(Map<String, Object> root, int status) {
        String message = Json.str(Json.obj(root.get("error")).get("message"), "");
        if (message.isEmpty()) {
            message = "HTTP " + status;
        }
        return switch (status) {
            case 400 -> "Peticion rechazada: " + message;
            case 401, 403 -> "API key invalida o sin permiso: " + message;
            case 404 -> "Modelo no encontrado (" + message + "). Actualiza la lista de modelos.";
            case 429 -> "Limite de uso alcanzado. Espera un momento o usa otro modelo. (" + message + ")";
            default -> "Error de Gemini (" + status + "): " + message;
        };
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
}
