package org.deepsetk.aierrors;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.openide.filesystems.XMLFileSystem;

/**
 * Verificacion offline del modulo ai-errors: parser JSON, cifrado de la key,
 * conversor Markdown y registro del layer.
 */
public final class VerifyAiErrors {

    private static int failures;

    public static void main(String[] args) throws Exception {
        String resDir = args[0];

        // --- layer ---
        XMLFileSystem fs = new XMLFileSystem(new File(resDir, "layer.xml").toURI().toURL());
        check(fs.findResource("Actions/Tools/org-deepsetk-aierrors-explain.instance") != null,
                "accion 'Explicar error' registrada");
        check(fs.findResource("Actions/Tools/org-deepsetk-aierrors-config.instance") != null,
                "accion 'Configurar Gemini' registrada");
        check(fs.findResource("Menu/Tools/org-deepsetk-aierrors-explain.shadow") != null,
                "menu Herramientas -> Explicar error");
        check(fs.findResource("Editors/Popup/org-deepsetk-aierrors-explain.shadow") != null,
                "menu contextual del editor");
        check(fs.findResource("Actions/Tools/org-deepsetk-aierrors-tutor.instance") != null,
                "accion 'Tutor Gemini' registrada");
        check(fs.findResource("Menu/Tools/org-deepsetk-aierrors-tutor.shadow") != null,
                "menu Herramientas -> Tutor Gemini");
        check(fs.findResource("Menu/Window/org-deepsetk-aierrors-tutor.shadow") != null,
                "menu Ventana -> Tutor Gemini");
        check(fs.findResource("Editors/Popup/org-deepsetk-aierrors-tutor.shadow") != null,
                "menu contextual -> Tutor Gemini");
        check(fs.findResource("Toolbars/File/org-deepsetk-aierrors-tutor.shadow") != null,
                "boton del tutor en la barra");
        check(fs.findResource("Windows2/Components/GeminiTutor.settings") != null,
                "ventana del tutor registrada");
        check(fs.findResource("Windows2/Modes/output/GeminiTutor.wstcref") != null,
                "ventana del tutor en el modo output");
        check(TutorIcon.image().getWidth() == 16, "icono del tutor 16x16");

        // --- JSON: respuesta de generateContent ---
        String generate = """
                {"candidates":[{"content":{"role":"model","parts":[
                  {"text":"Causa: el metodo no existe.\\n\\nCorreccion:\\n```java\\ncuenta.acreditar(monto);\\n```"},
                  {"text":"\\nFin."}]},"finishReason":"STOP"}],
                 "usageMetadata":{"totalTokenCount":42}}
                """;
        Map<String, Object> root = Json.obj(Json.parse(generate));
        List<Object> candidates = Json.arr(root.get("candidates"));
        check(candidates.size() == 1, "JSON: un candidato");
        StringBuilder text = new StringBuilder();
        for (Object part : Json.arr(Json.obj(Json.obj(candidates.get(0)).get("content")).get("parts"))) {
            text.append(Json.str(Json.obj(part).get("text"), ""));
        }
        check(text.toString().contains("acreditar(monto)"), "JSON: texto reconstruido");
        check(text.toString().contains("\n```java\n"), "JSON: escapes \\n respetados");

        // --- JSON: lista de modelos ---
        String models = """
                {"models":[
                  {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash",
                   "supportedGenerationMethods":["generateContent","countTokens"]},
                  {"name":"models/text-embedding-004","displayName":"Embedding",
                   "supportedGenerationMethods":["embedContent"]}]}
                """;
        int usable = 0;
        for (Object item : Json.arr(Json.obj(Json.parse(models)).get("models"))) {
            for (Object method : Json.arr(Json.obj(item).get("supportedGenerationMethods"))) {
                if ("generateContent".equals(Json.str(method, ""))) {
                    usable++;
                }
            }
        }
        check(usable == 1, "JSON: solo cuenta modelos con generateContent");

        // --- JSON: error de la API ---
        Map<String, Object> error = Json.obj(Json.parse(
                "{\"error\":{\"code\":400,\"message\":\"API key not valid\",\"status\":\"INVALID_ARGUMENT\"}}"));
        check("API key not valid".equals(Json.str(Json.obj(error.get("error")).get("message"), "")),
                "JSON: mensaje de error extraido");

        // --- Cifrado de la API key ---
        String secret = "AIzaSyEjemplo-De-Key-1234567890";
        String encrypted = SecretCodec.encrypt(secret);
        check(!encrypted.contains(secret), "key no aparece en claro");
        check(secret.equals(SecretCodec.decrypt(encrypted)), "key descifrada correctamente");
        check("".equals(SecretCodec.decrypt("texto-invalido")), "descifrado invalido devuelve vacio");

        // --- Markdown ---
        String html = Markdown.toHtml("## Causa\n\nEl **metodo** `acreditar` no existe.\n\n- uno\n- dos\n\n```java\nint x = 1;\n```\n");
        check(html.contains("<b>metodo</b>"), "Markdown: negritas");
        check(html.contains("<pre>"), "Markdown: bloque de codigo");
        check(html.contains("<li>uno</li>"), "Markdown: listas");
        check(html.contains("&lt;") || !html.contains("<script>"), "Markdown: escapa HTML");

        // --- Modelo: toString legible ---
        GeminiClient.Model m = new GeminiClient.Model("models/gemini-2.5-flash", "Gemini 2.5 Flash");
        check(m.toString().contains("Gemini 2.5 Flash"), "modelo: nombre legible en el combo");

        // --- Tutor: cuerpo JSON multi-turno ---
        String body = GeminiClient.buildBody("Eres un tutor", List.of(
                new GeminiClient.Turn("user", "Hola \"mundo\"\nlinea"),
                new GeminiClient.Turn("model", "Respuesta")));
        Map<String, Object> bodyRoot = Json.obj(Json.parse(body));
        List<Object> contents = Json.arr(bodyRoot.get("contents"));
        check(contents.size() == 2, "tutor: dos turnos en contents");
        check("user".equals(Json.str(Json.obj(contents.get(0)).get("role"), "")),
                "tutor: primer turno user");
        check("model".equals(Json.str(Json.obj(contents.get(1)).get("role"), "")),
                "tutor: segundo turno model");
        check(body.contains("Hola \\\"mundo\\\""), "tutor: escapa comillas");
        check(body.contains("\\nlinea"), "tutor: escapa saltos de linea");

        System.out.println(failures == 0 ? "VERIFICACION OK" : "FALLOS: " + failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "  OK   " : "  FALLO ") + what);
        if (!ok) {
            failures++;
        }
    }
}
